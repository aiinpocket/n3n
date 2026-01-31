package com.aiinpocket.n3n.ai.provider.impl;

import com.aiinpocket.n3n.ai.provider.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Ollama Local AI Provider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaProvider implements AiProvider {

    private static final String PROVIDER_ID = "ollama";
    private static final String DISPLAY_NAME = "Ollama (本地)";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final int DEFAULT_TIMEOUT_MS = 300000; // 本地模型可能較慢

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public int getDefaultTimeoutMs() {
        return DEFAULT_TIMEOUT_MS;
    }

    @Override
    public boolean requiresApiKey() {
        return false; // Ollama 不需要 API Key
    }

    @Override
    public CompletableFuture<List<AiModel>> fetchModels(String apiKey, String baseUrl) {
        String url = resolveBaseUrl(baseUrl) + "/api/tags";

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(this::parseModelsResponse)
                .onErrorReturn(List.of())
                .toFuture();
    }

    private List<AiModel> parseModelsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.path("models");

            List<AiModel> result = new ArrayList<>();
            if (models.isArray()) {
                for (JsonNode modelNode : models) {
                    String name = modelNode.path("name").asText();
                    String model = modelNode.path("model").asText(name);

                    // 解析模型大小
                    long size = modelNode.path("size").asLong(0);
                    String sizeStr = formatSize(size);

                    result.add(AiModel.builder()
                            .id(name)
                            .displayName(name + " (" + sizeStr + ")")
                            .providerId(PROVIDER_ID)
                            .contextWindow(getContextWindow(name))
                            .maxOutputTokens(4096)
                            .supportsVision(name.contains("llava") || name.contains("vision"))
                            .supportsStreaming(true)
                            .capabilities(Map.of("size", size))
                            .build());
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to parse Ollama models response", e);
            return List.of();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private int getContextWindow(String modelName) {
        // 根據模型名稱估算上下文長度
        if (modelName.contains("llama3") || modelName.contains("llama-3")) {
            return 8192;
        }
        if (modelName.contains("mistral")) {
            return 32768;
        }
        if (modelName.contains("qwen")) {
            return 32768;
        }
        return 4096; // 預設
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/api/chat";
        long startTime = System.currentTimeMillis();

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, false);

        return client.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .map(responseBody -> parseResponse(responseBody, System.currentTimeMillis() - startTime))
                .toFuture();
    }

    @Override
    public Flux<AiStreamChunk> chatStream(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/api/chat";

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, true);

        return client.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .mapNotNull(this::parseStreamEvent);
    }

    @Override
    public CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl) {
        String url = resolveBaseUrl(baseUrl) + "/api/tags";

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> true)
                .onErrorReturn(false)
                .toFuture();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "baseUrl", Map.of(
                                "type", "string",
                                "default", DEFAULT_BASE_URL,
                                "description", "Ollama 服務位址"
                        ),
                        "keepAlive", Map.of(
                                "type", "string",
                                "default", "5m",
                                "description", "模型保持載入時間"
                        )
                )
        );
    }

    private WebClient buildClient(AiProviderSettings settings) {
        return webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    private String resolveBaseUrl(String customUrl) {
        return customUrl != null && !customUrl.isBlank() ? customUrl : DEFAULT_BASE_URL;
    }

    private String buildRequestBody(AiChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());

        List<Map<String, Object>> messages = new ArrayList<>();

        // 加入 system 訊息
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        // 加入其他訊息
        for (AiMessage msg : request.getMessages()) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        body.put("messages", messages);
        body.put("stream", stream);

        // Options
        Map<String, Object> options = new LinkedHashMap<>();
        if (request.getTemperature() != null) {
            options.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.put("num_predict", request.getMaxTokens());
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    private AiResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String model = root.path("model").asText("");
            String content = root.path("message").path("content").asText("");

            // Ollama 回傳的 token 統計
            int promptTokens = root.path("prompt_eval_count").asInt(0);
            int completionTokens = root.path("eval_count").asInt(0);

            AiUsage usage = AiUsage.builder()
                    .inputTokens(promptTokens)
                    .outputTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();

            return AiResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .model(model)
                    .content(content)
                    .stopReason(root.path("done").asBoolean() ? "stop" : null)
                    .usage(usage)
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Ollama response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private AiStreamChunk parseStreamEvent(String event) {
        if (event == null || event.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(event);

            String content = node.path("message").path("content").asText("");
            boolean done = node.path("done").asBoolean(false);

            if (done) {
                int evalCount = node.path("eval_count").asInt(0);
                AiUsage usage = AiUsage.builder()
                        .outputTokens(evalCount)
                        .build();
                return AiStreamChunk.done("stop", usage);
            }

            if (!content.isEmpty()) {
                return AiStreamChunk.text(content);
            }
        } catch (Exception e) {
            log.warn("Failed to parse stream event: {}", event, e);
        }

        return null;
    }
}
