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
 * Google Gemini API Provider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiProvider implements AiProvider {

    private static final String PROVIDER_ID = "gemini";
    private static final String DISPLAY_NAME = "Gemini (Google)";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
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
        String url = resolveBaseUrl(baseUrl) + "/v1beta/models?key=" + apiKey;

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(this::parseModelsResponse)
                .onErrorReturn(getStaticModelList())
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
                    String displayName = modelNode.path("displayName").asText();

                    // 只返回支援 generateContent 的模型
                    JsonNode supportedMethods = modelNode.path("supportedGenerationMethods");
                    boolean supportsChat = false;
                    if (supportedMethods.isArray()) {
                        for (JsonNode method : supportedMethods) {
                            if ("generateContent".equals(method.asText())) {
                                supportsChat = true;
                                break;
                            }
                        }
                    }

                    if (supportsChat && name.contains("gemini")) {
                        // 提取模型 ID (去除 "models/" 前綴)
                        String id = name.replace("models/", "");

                        result.add(AiModel.builder()
                                .id(id)
                                .displayName(displayName)
                                .providerId(PROVIDER_ID)
                                .contextWindow(modelNode.path("inputTokenLimit").asInt(32768))
                                .maxOutputTokens(modelNode.path("outputTokenLimit").asInt(8192))
                                .supportsVision(id.contains("pro") || id.contains("flash"))
                                .supportsStreaming(true)
                                .build());
                    }
                }
            }

            if (result.isEmpty()) {
                return getStaticModelList();
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to parse Gemini models response", e);
            return getStaticModelList();
        }
    }

    private List<AiModel> getStaticModelList() {
        return List.of(
                AiModel.builder()
                        .id("gemini-2.0-flash")
                        .displayName("Gemini 2.0 Flash")
                        .providerId(PROVIDER_ID)
                        .contextWindow(1048576)
                        .maxOutputTokens(8192)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("gemini-1.5-pro")
                        .displayName("Gemini 1.5 Pro")
                        .providerId(PROVIDER_ID)
                        .contextWindow(2097152)
                        .maxOutputTokens(8192)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build(),
                AiModel.builder()
                        .id("gemini-1.5-flash")
                        .displayName("Gemini 1.5 Flash")
                        .providerId(PROVIDER_ID)
                        .contextWindow(1048576)
                        .maxOutputTokens(8192)
                        .supportsVision(true)
                        .supportsStreaming(true)
                        .build()
        );
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        String url = resolveBaseUrl(settings.getBaseUrl()) +
                "/v1beta/models/" + request.getModel() + ":generateContent?key=" + settings.getApiKey();
        long startTime = System.currentTimeMillis();

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request);

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
        String url = resolveBaseUrl(settings.getBaseUrl()) +
                "/v1beta/models/" + request.getModel() + ":streamGenerateContent?alt=sse&key=" + settings.getApiKey();

        WebClient client = buildClient(settings);
        String body = buildRequestBody(request);

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
        String url = resolveBaseUrl(baseUrl) + "/v1beta/models?key=" + apiKey;

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
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
                "properties", Map.of()
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

    private String buildRequestBody(AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();

        // 加入訊息
        for (AiMessage msg : request.getMessages()) {
            Map<String, Object> content = new LinkedHashMap<>();
            // Gemini 使用 "user" 和 "model" 作為角色
            String role = "assistant".equals(msg.getRole()) ? "model" : msg.getRole();
            if ("system".equals(role)) {
                role = "user"; // Gemini 沒有 system role，轉為 user
            }
            content.put("role", role);
            content.put("parts", List.of(Map.of("text", msg.getContent())));
            contents.add(content);
        }

        body.put("contents", contents);

        // System instruction
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemPrompt()))
            ));
        }

        // Generation config
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (request.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
        }
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            generationConfig.put("stopSequences", request.getStopSequences());
        }
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
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

            StringBuilder content = new StringBuilder();
            JsonNode candidates = root.path("candidates");
            String stopReason = null;

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode contentNode = firstCandidate.path("content").path("parts");
                if (contentNode.isArray()) {
                    for (JsonNode part : contentNode) {
                        content.append(part.path("text").asText(""));
                    }
                }
                stopReason = firstCandidate.path("finishReason").asText(null);
            }

            JsonNode usageMetadata = root.path("usageMetadata");
            AiUsage usage = AiUsage.builder()
                    .inputTokens(usageMetadata.path("promptTokenCount").asInt(0))
                    .outputTokens(usageMetadata.path("candidatesTokenCount").asInt(0))
                    .totalTokens(usageMetadata.path("totalTokenCount").asInt(0))
                    .build();

            return AiResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .model("")
                    .content(content.toString())
                    .stopReason(stopReason)
                    .usage(usage)
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", responseBody, e);
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
        if (data.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode candidates = node.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode parts = firstCandidate.path("content").path("parts");

                if (parts.isArray() && !parts.isEmpty()) {
                    String text = parts.get(0).path("text").asText("");
                    if (!text.isEmpty()) {
                        return AiStreamChunk.text(text);
                    }
                }

                String finishReason = firstCandidate.path("finishReason").asText(null);
                if (finishReason != null && !"FINISH_REASON_UNSPECIFIED".equals(finishReason)) {
                    return AiStreamChunk.done(finishReason, null);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse stream event: {}", event, e);
        }

        return null;
    }
}
