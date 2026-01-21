package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for sending messages to Slack.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SlackNodeHandler extends AbstractNodeHandler {

    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    @Override
    public String getType() {
        return "slack";
    }

    @Override
    public String getDisplayName() {
        return "Slack";
    }

    @Override
    public String getDescription() {
        return "Send messages to Slack channels.";
    }

    @Override
    public String getCategory() {
        return "Communication";
    }

    @Override
    public String getIcon() {
        return "slack";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "sendMessage");
        String token = getStringConfig(context, "token", "");
        String channel = getStringConfig(context, "channel", "");

        if (token.isEmpty()) {
            // Try to get from environment
            token = System.getenv("SLACK_BOT_TOKEN");
        }

        if (token == null || token.isEmpty()) {
            return NodeExecutionResult.failure("Slack bot token is required");
        }

        try {
            return switch (operation) {
                case "sendMessage" -> sendMessage(token, channel, context);
                case "uploadFile" -> uploadFile(token, channel, context);
                case "listChannels" -> listChannels(token);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (IOException e) {
            log.error("Slack API error: {}", e.getMessage());
            return NodeExecutionResult.failure("Slack API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult sendMessage(String token, String channel, NodeExecutionContext context) throws IOException {
        if (channel.isEmpty()) {
            return NodeExecutionResult.failure("Channel is required");
        }

        String text = getStringConfig(context, "text", "");
        if (text.isEmpty()) {
            return NodeExecutionResult.failure("Message text is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", channel);
        payload.put("text", text);

        // Optional blocks for rich messages
        Object blocks = context.getNodeConfig().get("blocks");
        if (blocks != null) {
            payload.put("blocks", blocks);
        }

        String threadTs = getStringConfig(context, "threadTs", "");
        if (!threadTs.isEmpty()) {
            payload.put("thread_ts", threadTs);
        }

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url("https://slack.com/api/chat.postMessage")
            .post(body)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            if (!json.path("ok").asBoolean(false)) {
                String error = json.path("error").asText("Unknown error");
                return NodeExecutionResult.failure("Slack API error: " + error);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("channel", json.path("channel").asText());
            output.put("ts", json.path("ts").asText());
            output.put("messageId", json.path("ts").asText());

            log.info("Slack message sent to channel: {}", channel);
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult uploadFile(String token, String channel, NodeExecutionContext context) throws IOException {
        String content = getStringConfig(context, "content", "");
        String filename = getStringConfig(context, "filename", "file.txt");
        String title = getStringConfig(context, "title", "");

        if (content.isEmpty()) {
            return NodeExecutionResult.failure("File content is required");
        }

        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("content", content)
            .addFormDataPart("filename", filename);

        if (!channel.isEmpty()) {
            bodyBuilder.addFormDataPart("channels", channel);
        }
        if (!title.isEmpty()) {
            bodyBuilder.addFormDataPart("title", title);
        }

        Request request = new Request.Builder()
            .url("https://slack.com/api/files.upload")
            .post(bodyBuilder.build())
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            if (!json.path("ok").asBoolean(false)) {
                String error = json.path("error").asText("Unknown error");
                return NodeExecutionResult.failure("Slack API error: " + error);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("fileId", json.path("file").path("id").asText());
            output.put("fileName", json.path("file").path("name").asText());

            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult listChannels(String token) throws IOException {
        Request request = new Request.Builder()
            .url("https://slack.com/api/conversations.list?types=public_channel,private_channel")
            .get()
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            if (!json.path("ok").asBoolean(false)) {
                String error = json.path("error").asText("Unknown error");
                return NodeExecutionResult.failure("Slack API error: " + error);
            }

            List<Map<String, Object>> channels = new java.util.ArrayList<>();
            for (JsonNode channel : json.path("channels")) {
                channels.add(Map.of(
                    "id", channel.path("id").asText(),
                    "name", channel.path("name").asText(),
                    "isPrivate", channel.path("is_private").asBoolean()
                ));
            }

            Map<String, Object> output = new HashMap<>();
            output.put("channels", channels);
            output.put("count", channels.size());

            return NodeExecutionResult.success(output);
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("sendMessage", "uploadFile", "listChannels"),
                    "default", "sendMessage"
                ),
                "token", Map.of(
                    "type", "string",
                    "title", "Bot Token",
                    "description", "Slack Bot OAuth Token (or use SLACK_BOT_TOKEN env var)",
                    "format", "password"
                ),
                "channel", Map.of(
                    "type", "string",
                    "title", "Channel",
                    "description", "Channel ID or name (e.g., #general or C01234567)"
                ),
                "text", Map.of(
                    "type", "string",
                    "title", "Message",
                    "description", "Message text to send",
                    "format", "textarea"
                ),
                "threadTs", Map.of(
                    "type", "string",
                    "title", "Thread Timestamp",
                    "description", "Reply in thread (optional)"
                ),
                "blocks", Map.of(
                    "type", "object",
                    "title", "Blocks",
                    "description", "Slack Block Kit JSON for rich messages"
                ),
                "content", Map.of(
                    "type", "string",
                    "title", "File Content",
                    "description", "Content for file upload"
                ),
                "filename", Map.of(
                    "type", "string",
                    "title", "Filename",
                    "default", "file.txt"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
