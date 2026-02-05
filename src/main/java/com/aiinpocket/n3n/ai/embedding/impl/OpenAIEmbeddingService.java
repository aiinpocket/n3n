package com.aiinpocket.n3n.ai.embedding.impl;

import com.aiinpocket.n3n.ai.embedding.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Embeddings API 實作
 *
 * 使用 OpenAI 的 text-embedding-3-small 或 text-embedding-3-large 模型
 * 產生高品質的文字向量嵌入。
 *
 * 配置方式：
 * - n3n.embedding.provider=openai
 * - n3n.embedding.openai.api-key=sk-...
 * - n3n.embedding.openai.model=text-embedding-3-small
 */
@Service
@ConditionalOnProperty(name = "n3n.embedding.provider", havingValue = "openai", matchIfMissing = true)
@Slf4j
public class OpenAIEmbeddingService implements EmbeddingService {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_DIMENSION = 1536;
    private static final int BATCH_SIZE = 100; // OpenAI 限制每次最多 2048 個輸入

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int dimension;
    private final int timeoutMs;

    public OpenAIEmbeddingService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${n3n.embedding.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${n3n.embedding.openai.model:text-embedding-3-small}") String model,
            @Value("${n3n.embedding.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${n3n.embedding.openai.dimension:1536}") int dimension,
            @Value("${n3n.embedding.timeout-ms:30000}") int timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.dimension = dimension;
        this.timeoutMs = timeoutMs;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        log.info("OpenAI Embedding Service initialized - model: {}, dimension: {}, available: {}",
                model, dimension, isAvailable());
    }

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        if (!isAvailable()) {
            log.warn("OpenAI API key not configured, returning zero vector");
            return new float[dimension];
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", text);
            if (model.startsWith("text-embedding-3")) {
                requestBody.put("dimensions", dimension);
            }

            String response = webClient.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            return parseEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("Failed to get embedding from OpenAI", e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> getBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        if (!isAvailable()) {
            log.warn("OpenAI API key not configured, returning zero vectors");
            return texts.stream().map(t -> new float[dimension]).toList();
        }

        List<float[]> results = new ArrayList<>();

        // 分批處理
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            results.addAll(processBatch(batch));
        }

        return results;
    }

    private List<float[]> processBatch(List<String> batch) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", batch);
            if (model.startsWith("text-embedding-3")) {
                requestBody.put("dimensions", dimension);
            }

            String response = webClient.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs * 2)) // 批次處理給更長時間
                    .block();

            return parseBatchEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("Failed to get batch embeddings from OpenAI", e);
            // 發生錯誤時返回零向量
            return batch.stream().map(t -> new float[dimension]).toList();
        }
    }

    private float[] parseEmbeddingResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");

        if (data.isArray() && !data.isEmpty()) {
            JsonNode embedding = data.get(0).path("embedding");
            return parseEmbeddingArray(embedding);
        }

        throw new RuntimeException("Invalid embedding response structure");
    }

    private List<float[]> parseBatchEmbeddingResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");

        List<float[]> embeddings = new ArrayList<>();

        if (data.isArray()) {
            // OpenAI 按 index 排序結果
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                embeddings.add(parseEmbeddingArray(embedding));
            }
        }

        return embeddings;
    }

    private float[] parseEmbeddingArray(JsonNode embeddingNode) {
        if (!embeddingNode.isArray()) {
            return new float[dimension];
        }

        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        return embedding;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
