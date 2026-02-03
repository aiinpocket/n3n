package com.aiinpocket.n3n.ai.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Simple AI Provider.
 * Works with OpenAI, Ollama, Gemini, and Claude APIs.
 * NOT a Spring bean - created dynamically by SimpleAIProviderRegistry.
 */
@Slf4j
public class OpenAICompatibleSimpleProvider implements SimpleAIProvider {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final WebClient webClient;

    public OpenAICompatibleSimpleProvider(String name, String baseUrl, String apiKey, String model, Long timeoutMs) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs != null ? timeoutMs : 60000L;
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAvailable() {
        // For cloud providers, just check if API key is configured
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public String chat(String prompt, String systemPrompt, int maxTokens, double temperature) {
        List<Map<String, String>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> request = Map.of(
            "model", model,
            "messages", messages,
            "temperature", temperature,
            "max_tokens", maxTokens
        );

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON);

            if (apiKey != null && !apiKey.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = requestSpec
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            throw new RuntimeException("Invalid response from " + name);
        } catch (WebClientResponseException e) {
            log.error("{} API error: {}", name, e.getMessage());
            throw new RuntimeException(name + " service error: " + e.getMessage());
        }
    }
}
