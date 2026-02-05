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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama Embeddings API 實作
 *
 * 使用本地部署的 Ollama 服務產生文字向量嵌入。
 * 支援模型：nomic-embed-text, mxbai-embed-large, all-minilm 等。
 *
 * 配置方式：
 * - n3n.embedding.provider=ollama
 * - n3n.embedding.ollama.base-url=http://localhost:11434
 * - n3n.embedding.ollama.model=nomic-embed-text
 */
@Service
@ConditionalOnProperty(name = "n3n.embedding.provider", havingValue = "ollama")
@Slf4j
public class OllamaEmbeddingService implements EmbeddingService {

    private static final String DEFAULT_MODEL = "nomic-embed-text";
    private static final int DEFAULT_DIMENSION = 768; // nomic-embed-text 維度

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String baseUrl;
    private final int timeoutMs;
    private volatile int detectedDimension = -1;

    public OllamaEmbeddingService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${n3n.embedding.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${n3n.embedding.ollama.model:nomic-embed-text}") String model,
            @Value("${n3n.embedding.timeout-ms:60000}") int timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        log.info("Ollama Embedding Service initialized - model: {}, baseUrl: {}", model, baseUrl);

        // 異步檢測服務可用性和維度
        detectDimensionAsync();
    }

    private void detectDimensionAsync() {
        try {
            // 用一個簡短的測試文字來檢測維度
            float[] testEmbedding = getEmbeddingInternal("test");
            if (testEmbedding != null && testEmbedding.length > 0) {
                this.detectedDimension = testEmbedding.length;
                log.info("Ollama embedding dimension detected: {}", detectedDimension);
            }
        } catch (Exception e) {
            log.warn("Failed to detect Ollama embedding dimension, will detect on first use: {}", e.getMessage());
        }
    }

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[getDimension()];
        }

        try {
            float[] embedding = getEmbeddingInternal(text);

            // 更新檢測到的維度
            if (detectedDimension < 0 && embedding != null) {
                detectedDimension = embedding.length;
            }

            return embedding;

        } catch (Exception e) {
            log.error("Failed to get embedding from Ollama", e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    private float[] getEmbeddingInternal(String text) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", text);

        String response = webClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        return parseEmbeddingResponse(response);
    }

    @Override
    public List<float[]> getBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        // Ollama 的 /api/embed 支援批次輸入
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", texts);

            String response = webClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs * 2))
                    .block();

            return parseBatchEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("Failed to get batch embeddings from Ollama, falling back to sequential", e);
            // 批次失敗時逐一處理
            List<float[]> results = new ArrayList<>();
            for (String text : texts) {
                try {
                    results.add(getEmbedding(text));
                } catch (Exception ex) {
                    results.add(new float[getDimension()]);
                }
            }
            return results;
        }
    }

    private float[] parseEmbeddingResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        // Ollama 格式：{ "embeddings": [[...]] } 或 { "embedding": [...] }
        JsonNode embeddings = root.path("embeddings");
        if (embeddings.isArray() && !embeddings.isEmpty()) {
            return parseEmbeddingArray(embeddings.get(0));
        }

        JsonNode embedding = root.path("embedding");
        if (embedding.isArray()) {
            return parseEmbeddingArray(embedding);
        }

        throw new RuntimeException("Invalid Ollama embedding response structure");
    }

    private List<float[]> parseBatchEmbeddingResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode embeddings = root.path("embeddings");

        List<float[]> results = new ArrayList<>();

        if (embeddings.isArray()) {
            for (JsonNode embedding : embeddings) {
                results.add(parseEmbeddingArray(embedding));
            }
        }

        return results;
    }

    private float[] parseEmbeddingArray(JsonNode embeddingNode) {
        if (!embeddingNode.isArray()) {
            return new float[getDimension()];
        }

        float[] embedding = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        return embedding;
    }

    @Override
    public int getDimension() {
        if (detectedDimension > 0) {
            return detectedDimension;
        }

        // 根據模型返回預設維度
        return switch (model.toLowerCase()) {
            case "nomic-embed-text" -> 768;
            case "mxbai-embed-large" -> 1024;
            case "all-minilm" -> 384;
            case "snowflake-arctic-embed" -> 1024;
            default -> DEFAULT_DIMENSION;
        };
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        try {
            // 檢查 Ollama 服務是否可用
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            // 檢查指定的模型是否已安裝
            JsonNode root = objectMapper.readTree(response);
            JsonNode models = root.path("models");
            if (models.isArray()) {
                for (JsonNode m : models) {
                    String name = m.path("name").asText();
                    if (name.equals(model) || name.startsWith(model + ":")) {
                        return true;
                    }
                }
            }

            log.warn("Ollama model {} not found. Available models: {}", model, response);
            return false;

        } catch (Exception e) {
            log.debug("Ollama service not available: {}", e.getMessage());
            return false;
        }
    }
}
