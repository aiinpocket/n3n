package com.aiinpocket.n3n.skill.builtin.notify;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Skill to send data to a webhook URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendWebhookSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "send_webhook";
    }

    @Override
    public String getDisplayName() {
        return "Send Webhook";
    }

    @Override
    public String getDescription() {
        return "Send data to a webhook URL via HTTP POST";
    }

    @Override
    public String getCategory() {
        return "notify";
    }

    @Override
    public String getIcon() {
        return "notification";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "Webhook URL"
                ),
                "payload", Map.of(
                    "type", "object",
                    "description", "Data to send"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "Optional HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                )
            ),
            "required", List.of("url", "payload")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "success", Map.of("type", "boolean"),
                "statusCode", Map.of("type", "integer")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        String url = (String) input.get("url");
        Object payload = input.get("payload");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) input.get("headers");

        if (url == null || url.isBlank()) {
            return SkillResult.failure("MISSING_URL", "Webhook URL is required");
        }

        if (payload == null) {
            return SkillResult.failure("MISSING_PAYLOAD", "Payload is required");
        }

        try {
            WebClient webClient = webClientBuilder.build();

            WebClient.RequestBodySpec request = webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);

            if (headers != null) {
                headers.forEach(request::header);
            }

            request.bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return SkillResult.success(Map.of(
                "success", true,
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("Failed to send webhook to {}: {}", url, e.getMessage());
            return SkillResult.failure("WEBHOOK_ERROR", "Failed to send webhook: " + e.getMessage());
        }
    }
}
