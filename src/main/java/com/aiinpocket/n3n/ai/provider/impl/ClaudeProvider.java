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
 * Anthropic Claude API Provider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeProvider implements AiProvider {

    private static final String PROVIDER_ID = "claude";
    private static final String DISPLAY_NAME = "Claude (Anthropic)";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";
    private static final int DEFAULT_TIMEOUT_MS = 120000;

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
    public CompletableFuture<List<AiModel>> fetchModels(String apiKey, String baseUrl) {
        // Claude 沒有 models endpoint，使用預定義清單
        return CompletableFuture.completedFuture(getStaticModelList());
    }

    private List<AiModel> getStaticModelList() {
        return List.of(
                AiModel.builder()
                        .id("claude-sonnet-4-20250514")
                        .displayName("Claude Sonnet 4")
                        .providerId(PROVIDER_ID)
                        .contextWindow(200000)
                        .maxOutputTokens(64000)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("claude-opus-4-20250514")
                        .displayName("Claude Opus 4")
                        .providerId(PROVIDER_ID)
                        .contextWindow(200000)
                        .maxOutputTokens(32000)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("claude-3-5-sonnet-20241022")
                        .displayName("Claude 3.5 Sonnet")
                        .providerId(PROVIDER_ID)
                        .contextWindow(200000)
                        .maxOutputTokens(8192)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("claude-3-5-haiku-20241022")
                        .displayName("Claude 3.5 Haiku")
                        .providerId(PROVIDER_ID)
                        .contextWindow(200000)
                        .maxOutputTokens(8192)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build()
        );
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/messages";
        long startTime = System.currentTimeMillis();

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, false);

        return client.post()
                .uri(url)
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", API_VERSION)
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
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/messages";

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, true);

        return client.post()
                .uri(url)
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", API_VERSION)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .mapNotNull(this::parseStreamEvent);
    }

    @Override
    public CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl) {
        String url = resolveBaseUrl(baseUrl) + "/v1/messages";

        WebClient client = webClientBuilder.build();

        // 發送一個最小請求測試連線
        String testBody = """
            {
                "model": "claude-3-5-haiku-20241022",
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hi"}]
            }
            """;

        return client.post()
                .uri(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("Content-Type", "application/json")
                .bodyValue(testBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(response -> true)
                .onErrorReturn(false)
                .toFuture();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "anthropicVersion", Map.of(
                                "type", "string",
                                "default", API_VERSION,
                                "description", "Anthropic API 版本"
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
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }

        body.put("messages", convertMessages(request.getMessages()));

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            body.put("stop_sequences", request.getStopSequences());
        }
        if (stream) {
            body.put("stream", true);
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    private List<Map<String, Object>> convertMessages(List<AiMessage> messages) {
        return messages.stream()
                .filter(m -> !"system".equals(m.getRole())) // system 訊息單獨處理
                .map(m -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    return msg;
                })
                .toList();
    }

    private AiResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String id = root.path("id").asText();
            String model = root.path("model").asText();

            StringBuilder content = new StringBuilder();
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        content.append(block.path("text").asText());
                    }
                }
            }

            String stopReason = root.path("stop_reason").asText(null);

            JsonNode usage = root.path("usage");
            AiUsage aiUsage = AiUsage.builder()
                    .inputTokens(usage.path("input_tokens").asInt(0))
                    .outputTokens(usage.path("output_tokens").asInt(0))
                    .totalTokens(usage.path("input_tokens").asInt(0) + usage.path("output_tokens").asInt(0))
                    .build();

            return AiResponse.builder()
                    .id(id)
                    .model(model)
                    .content(content.toString())
                    .stopReason(stopReason)
                    .usage(aiUsage)
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private AiStreamChunk parseStreamEvent(String event) {
        if (event == null || event.isBlank()) {
            return null;
        }

        // SSE 格式: event: xxx\ndata: {...}
        String data = event;
        if (event.startsWith("data:")) {
            data = event.substring(5).trim();
        }
        if (data.isBlank() || data.equals("[DONE]")) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            String type = node.path("type").asText();

            if ("content_block_delta".equals(type)) {
                String delta = node.path("delta").path("text").asText("");
                return AiStreamChunk.text(delta);
            } else if ("message_stop".equals(type)) {
                return AiStreamChunk.done("end_turn", null);
            } else if ("message_delta".equals(type)) {
                String stopReason = node.path("delta").path("stop_reason").asText(null);
                JsonNode usage = node.path("usage");
                AiUsage aiUsage = null;
                if (!usage.isMissingNode()) {
                    aiUsage = AiUsage.builder()
                            .outputTokens(usage.path("output_tokens").asInt(0))
                            .build();
                }
                if (stopReason != null) {
                    return AiStreamChunk.done(stopReason, aiUsage);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse stream event: {}", event, e);
        }

        return null;
    }
}
