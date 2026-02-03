package com.aiinpocket.n3n.ai.module;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Llamafile Simple AI Provider (local Phi-3-Mini model)
 * Implements the simplified interface for the modular AI assistant system.
 */
@Component
@Slf4j
public class LlamafileSimpleProvider implements SimpleAIProvider {

    @Value("${n3n.ai.llamafile.url:http://localhost:8081}")
    private String baseUrl;

    @Value("${n3n.ai.llamafile.model:phi-3-mini}")
    private String model;

    @Value("${n3n.ai.llamafile.enabled:true}")
    private boolean enabled;

    @Value("${n3n.ai.llamafile.timeout-ms:60000}")
    private long timeoutMs;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public String getName() {
        return "llamafile";
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            webClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
            return true;
        } catch (Exception e) {
            log.debug("Llamafile not available: {}", e.getMessage());
            return false;
        }
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
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
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
            throw new RuntimeException("Invalid response from Llamafile");
        } catch (WebClientResponseException e) {
            log.error("Llamafile API error: {}", e.getMessage());
            throw new RuntimeException("Llamafile service error: " + e.getMessage());
        }
    }
}
