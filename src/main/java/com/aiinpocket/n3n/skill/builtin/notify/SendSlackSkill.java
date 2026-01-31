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
 * Skill to send messages to Slack.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendSlackSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "send_slack";
    }

    @Override
    public String getDisplayName() {
        return "Send Slack Message";
    }

    @Override
    public String getDescription() {
        return "Send a message to Slack channel via webhook or API";
    }

    @Override
    public String getCategory() {
        return "notify";
    }

    @Override
    public String getIcon() {
        return "slack";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "webhookUrl", Map.of(
                    "type", "string",
                    "description", "Slack Incoming Webhook URL"
                ),
                "channel", Map.of(
                    "type", "string",
                    "description", "Channel name (optional, for API mode)"
                ),
                "text", Map.of(
                    "type", "string",
                    "description", "Message text"
                ),
                "blocks", Map.of(
                    "type", "array",
                    "description", "Slack Block Kit blocks (optional)"
                ),
                "username", Map.of(
                    "type", "string",
                    "description", "Bot username (optional)"
                ),
                "iconEmoji", Map.of(
                    "type", "string",
                    "description", "Bot icon emoji (optional, e.g., :robot_face:)"
                )
            ),
            "required", List.of("webhookUrl", "text")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "success", Map.of("type", "boolean"),
                "response", Map.of("type", "string")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        String webhookUrl = (String) input.get("webhookUrl");
        String text = (String) input.get("text");
        String channel = (String) input.get("channel");
        String username = (String) input.get("username");
        String iconEmoji = (String) input.get("iconEmoji");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) input.get("blocks");

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return SkillResult.failure("MISSING_WEBHOOK", "Slack webhook URL is required");
        }

        if (text == null || text.isBlank()) {
            return SkillResult.failure("MISSING_TEXT", "Message text is required");
        }

        try {
            // Build Slack message payload
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("text", text);

            if (channel != null && !channel.isBlank()) {
                payload.put("channel", channel);
            }
            if (username != null && !username.isBlank()) {
                payload.put("username", username);
            }
            if (iconEmoji != null && !iconEmoji.isBlank()) {
                payload.put("icon_emoji", iconEmoji);
            }
            if (blocks != null && !blocks.isEmpty()) {
                payload.put("blocks", blocks);
            }

            WebClient webClient = webClientBuilder.build();

            String response = webClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            boolean success = "ok".equals(response);

            return SkillResult.success(Map.of(
                "success", success,
                "response", response != null ? response : ""
            ));

        } catch (Exception e) {
            log.error("Failed to send Slack message: {}", e.getMessage());
            return SkillResult.failure("SLACK_ERROR", "Failed to send Slack message: " + e.getMessage());
        }
    }
}
