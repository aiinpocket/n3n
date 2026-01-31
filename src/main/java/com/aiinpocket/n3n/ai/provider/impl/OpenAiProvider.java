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
 * OpenAI ChatGPT API Provider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiProvider implements AiProvider {

    private static final String PROVIDER_ID = "openai";
    private static final String DISPLAY_NAME = "ChatGPT (OpenAI)";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
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
        String url = resolveBaseUrl(baseUrl) + "/v1/models";

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(this::parseModelsResponse)
                .toFuture();
    }

    private List<AiModel> parseModelsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");

            List<AiModel> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode modelNode : data) {
                    String id = modelNode.path("id").asText();
                    // 只返回 chat 模型
                    if (id.startsWith("gpt-")) {
                        models.add(AiModel.builder()
                                .id(id)
                                .displayName(formatModelName(id))
                                .providerId(PROVIDER_ID)
                                .contextWindow(getContextWindow(id))
                                .maxOutputTokens(getMaxOutputTokens(id))
                                .supportsVision(id.contains("vision") || id.contains("gpt-4o") || id.contains("gpt-4-turbo"))
                                .supportsStreaming(true)
                                .build());
                    }
                }
            }

            // 排序：新模型在前
            models.sort((a, b) -> b.getId().compareTo(a.getId()));
            return models;

        } catch (Exception e) {
            log.error("Failed to parse OpenAI models response", e);
            return getStaticModelList();
        }
    }

    private List<AiModel> getStaticModelList() {
        return List.of(
                AiModel.builder()
                        .id("gpt-4o")
                        .displayName("GPT-4o")
                        .providerId(PROVIDER_ID)
                        .contextWindow(128000)
                        .maxOutputTokens(16384)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("gpt-4o-mini")
                        .displayName("GPT-4o Mini")
                        .providerId(PROVIDER_ID)
                        .contextWindow(128000)
                        .maxOutputTokens(16384)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("gpt-4-turbo")
                        .displayName("GPT-4 Turbo")
                        .providerId(PROVIDER_ID)
                        .contextWindow(128000)
                        .maxOutputTokens(4096)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build()
        );
    }

    private String formatModelName(String id) {
        return id.replace("-", " ")
                .replace("gpt ", "GPT-")
                .replace("turbo", "Turbo")
                .replace("mini", "Mini");
    }

    private int getContextWindow(String modelId) {
        if (modelId.contains("gpt-4o") || modelId.contains("gpt-4-turbo")) {
            return 128000;
        } else if (modelId.contains("gpt-4")) {
            return 8192;
        }
        return 16385; // GPT-3.5
    }

    private int getMaxOutputTokens(String modelId) {
        if (modelId.contains("gpt-4o")) {
            return 16384;
        }
        return 4096;
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/chat/completions";
        long startTime = System.currentTimeMillis();

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, false);

        return client.post()
                .uri(url)
                .header("Authorization", "Bearer " + settings.getApiKey())
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
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/chat/completions";

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request, true);

        return client.post()
                .uri(url)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .mapNotNull(this::parseStreamEvent);
    }

    @Override
    public CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl) {
        String url = resolveBaseUrl(baseUrl) + "/v1/models";

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
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
                        "organization", Map.of(
                                "type", "string",
                                "description", "OpenAI Organization ID"
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

        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            body.put("stop", request.getStopSequences());
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

    private AiResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String id = root.path("id").asText();
            String model = root.path("model").asText();

            JsonNode choices = root.path("choices");
            String content = "";
            String stopReason = null;

            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                content = firstChoice.path("message").path("content").asText("");
                stopReason = firstChoice.path("finish_reason").asText(null);
            }

            JsonNode usage = root.path("usage");
            AiUsage aiUsage = AiUsage.builder()
                    .inputTokens(usage.path("prompt_tokens").asInt(0))
                    .outputTokens(usage.path("completion_tokens").asInt(0))
                    .totalTokens(usage.path("total_tokens").asInt(0))
                    .build();

            return AiResponse.builder()
                    .id(id)
                    .model(model)
                    .content(content)
                    .stopReason(stopReason)
                    .usage(aiUsage)
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private AiStreamChunk parseStreamEvent(String event) {
        if (event == null || event.isBlank()) {
            return null;
        }

        String data = event;
        if (event.startsWith("data:")) {
            data = event.substring(5).trim();
        }
        if (data.isBlank() || data.equals("[DONE]")) {
            return AiStreamChunk.done("stop", null);
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.path("delta");
                String content = delta.path("content").asText("");
                String finishReason = firstChoice.path("finish_reason").asText(null);

                if (finishReason != null) {
                    return AiStreamChunk.done(finishReason, null);
                }
                if (!content.isEmpty()) {
                    return AiStreamChunk.text(content);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse stream event: {}", event, e);
        }

        return null;
    }
}
