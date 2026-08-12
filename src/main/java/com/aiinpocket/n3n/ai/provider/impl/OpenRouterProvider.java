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
 * OpenRouter Provider
 *
 * 單一 API Key 即可使用 OpenAI / Anthropic / Google / Meta 等上百個模型，
 * 並且提供官方餘額查詢 API（GET /api/v1/credits），適合作為統一入口。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterProvider implements AiProvider {

    private static final String PROVIDER_ID = "openrouter";
    private static final String DISPLAY_NAME = "OpenRouter";
    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api";
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

        return webClientBuilder.build().get()
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
                    if (id.isBlank()) {
                        continue;
                    }
                    JsonNode architecture = modelNode.path("architecture");
                    boolean supportsVision = architecture.path("input_modalities").toString().contains("image");
                    models.add(AiModel.builder()
                            .id(id)
                            .displayName(modelNode.path("name").asText(id))
                            .providerId(PROVIDER_ID)
                            .contextWindow(modelNode.path("context_length").asInt(8192))
                            .maxOutputTokens(modelNode.path("top_provider").path("max_completion_tokens").asInt(4096))
                            .supportsVision(supportsVision)
                            .supportsStreaming(true)
                            .build());
                }
            }
            return models.isEmpty() ? getStaticModelList() : models;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter models response", e);
            return getStaticModelList();
        }
    }

    private List<AiModel> getStaticModelList() {
        return List.of(
                staticModel("anthropic/claude-sonnet-4.5", "Claude Sonnet 4.5", 200000, true),
                staticModel("openai/gpt-5", "GPT-5", 400000, true),
                staticModel("openai/gpt-5-mini", "GPT-5 Mini", 400000, true),
                staticModel("google/gemini-2.5-pro", "Gemini 2.5 Pro", 1000000, true),
                staticModel("google/gemini-2.5-flash", "Gemini 2.5 Flash", 1000000, true),
                staticModel("meta-llama/llama-4-maverick", "Llama 4 Maverick", 131072, false)
        );
    }

    private AiModel staticModel(String id, String name, int contextWindow, boolean vision) {
        return AiModel.builder()
                .id(id)
                .displayName(name)
                .providerId(PROVIDER_ID)
                .contextWindow(contextWindow)
                .maxOutputTokens(16384)
                .supportsVision(vision)
                .supportsStreaming(true)
                .build();
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/chat/completions";
        long startTime = System.currentTimeMillis();

        return buildClient().post()
                .uri(url)
                .headers(h -> applyHeaders(h::add, settings))
                .bodyValue(buildRequestBody(request, false))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .map(responseBody -> parseResponse(responseBody, System.currentTimeMillis() - startTime))
                .toFuture();
    }

    @Override
    public Flux<AiStreamChunk> chatStream(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) + "/v1/chat/completions";

        return buildClient().post()
                .uri(url)
                .headers(h -> applyHeaders(h::add, settings))
                .bodyValue(buildRequestBody(request, true))
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(settings.getTimeoutMs()))
                .mapNotNull(this::parseStreamEvent);
    }

    @Override
    public CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl) {
        String url = resolveBaseUrl(baseUrl) + "/v1/key";

        return webClientBuilder.build().get()
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
                        "appName", Map.of(
                                "type", "string",
                                "description", "Application name shown on OpenRouter dashboard"
                        )
                )
        );
    }

    private void applyHeaders(java.util.function.BiConsumer<String, String> add, AiProviderSettings settings) {
        add.accept("Authorization", "Bearer " + settings.getApiKey());
        add.accept("Content-Type", "application/json");
        add.accept("HTTP-Referer", "https://github.com/aiinpocket/n3n");
        add.accept("X-Title", "N3N Flow Platform");
    }

    private WebClient buildClient() {
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

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        for (AiMessage msg : request.getMessages()) {
            messages.add(OpenAiContentMapper.toOpenAiMessage(msg));
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
                    .id(root.path("id").asText())
                    .model(root.path("model").asText())
                    .content(content)
                    .stopReason(stopReason)
                    .usage(aiUsage)
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response", e);
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
        // OpenRouter 會送出 ": OPENROUTER PROCESSING" keep-alive 註解
        if (data.startsWith(":")) {
            return null;
        }
        if (data.isBlank() || data.equals("[DONE]")) {
            return AiStreamChunk.done("stop", null);
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                String content = firstChoice.path("delta").path("content").asText("");
                String finishReason = firstChoice.path("finish_reason").asText(null);

                if (finishReason != null) {
                    return AiStreamChunk.done(finishReason, null);
                }
                if (!content.isEmpty()) {
                    return AiStreamChunk.text(content);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse OpenRouter stream event: {}", event, e);
        }

        return null;
    }
}
