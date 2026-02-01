package com.aiinpocket.n3n.execution.handler.handlers.messaging;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Handler for Discord Bot API and Webhook operations.
 * Supports sending messages, managing channels, and webhook integrations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DiscordNodeHandler extends AbstractNodeHandler {

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    @Override
    public String getType() {
        return "discord";
    }

    @Override
    public String getDisplayName() {
        return "Discord";
    }

    @Override
    public String getDescription() {
        return "Send messages and interact with Discord servers";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "discord";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String resource = getStringConfig(context, "resource", "message");
        String operation = getStringConfig(context, "operation", "send");

        try {
            return switch (resource) {
                case "message" -> handleMessageOperations(operation, context);
                case "channel" -> handleChannelOperations(operation, context);
                case "webhook" -> handleWebhookOperations(operation, context);
                case "user" -> handleUserOperations(operation, context);
                case "guild" -> handleGuildOperations(operation, context);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("Discord API error: {}", e.getMessage());
            return NodeExecutionResult.failure("Discord API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult handleMessageOperations(String operation, NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        String channelId = getStringConfig(context, "channelId", "");

        if (botToken.isEmpty()) {
            botToken = System.getenv("DISCORD_BOT_TOKEN");
        }

        return switch (operation) {
            case "send" -> sendMessage(botToken, channelId, context);
            case "edit" -> editMessage(botToken, channelId, context);
            case "delete" -> deleteMessage(botToken, channelId, context);
            case "react" -> addReaction(botToken, channelId, context);
            case "pin" -> pinMessage(botToken, channelId, context);
            case "get" -> getMessage(botToken, channelId, context);
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
    }

    private NodeExecutionResult sendMessage(String token, String channelId, NodeExecutionContext context) throws IOException {
        if (channelId.isEmpty()) {
            return NodeExecutionResult.failure("channelId is required");
        }

        String content = getStringConfig(context, "content", "");
        boolean tts = getBooleanConfig(context, "tts", false);

        Map<String, Object> payload = new HashMap<>();
        if (!content.isEmpty()) {
            payload.put("content", content);
        }
        if (tts) {
            payload.put("tts", true);
        }

        // Embeds
        Object embeds = context.getNodeConfig().get("embeds");
        if (embeds != null) {
            payload.put("embeds", embeds);
        }

        // Components (buttons, select menus)
        Object components = context.getNodeConfig().get("components");
        if (components != null) {
            payload.put("components", components);
        }

        // Message reference for replies
        String replyToMessageId = getStringConfig(context, "replyToMessageId", "");
        if (!replyToMessageId.isEmpty()) {
            payload.put("message_reference", Map.of("message_id", replyToMessageId));
        }

        return callDiscordApi(token, "POST", "/channels/" + channelId + "/messages", payload);
    }

    private NodeExecutionResult editMessage(String token, String channelId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");

        if (channelId.isEmpty() || messageId.isEmpty()) {
            return NodeExecutionResult.failure("channelId and messageId are required");
        }

        Map<String, Object> payload = new HashMap<>();
        String content = getStringConfig(context, "content", "");
        if (!content.isEmpty()) {
            payload.put("content", content);
        }

        Object embeds = context.getNodeConfig().get("embeds");
        if (embeds != null) {
            payload.put("embeds", embeds);
        }

        return callDiscordApi(token, "PATCH", "/channels/" + channelId + "/messages/" + messageId, payload);
    }

    private NodeExecutionResult deleteMessage(String token, String channelId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");

        if (channelId.isEmpty() || messageId.isEmpty()) {
            return NodeExecutionResult.failure("channelId and messageId are required");
        }

        return callDiscordApi(token, "DELETE", "/channels/" + channelId + "/messages/" + messageId, null);
    }

    private NodeExecutionResult addReaction(String token, String channelId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");
        String emoji = getStringConfig(context, "emoji", "");

        if (channelId.isEmpty() || messageId.isEmpty() || emoji.isEmpty()) {
            return NodeExecutionResult.failure("channelId, messageId, and emoji are required");
        }

        // URL encode the emoji
        String encodedEmoji = java.net.URLEncoder.encode(emoji, java.nio.charset.StandardCharsets.UTF_8);

        return callDiscordApi(token, "PUT",
            "/channels/" + channelId + "/messages/" + messageId + "/reactions/" + encodedEmoji + "/@me",
            null);
    }

    private NodeExecutionResult pinMessage(String token, String channelId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");

        if (channelId.isEmpty() || messageId.isEmpty()) {
            return NodeExecutionResult.failure("channelId and messageId are required");
        }

        return callDiscordApi(token, "PUT", "/channels/" + channelId + "/pins/" + messageId, null);
    }

    private NodeExecutionResult getMessage(String token, String channelId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");

        if (channelId.isEmpty() || messageId.isEmpty()) {
            return NodeExecutionResult.failure("channelId and messageId are required");
        }

        return callDiscordApi(token, "GET", "/channels/" + channelId + "/messages/" + messageId, null);
    }

    private NodeExecutionResult handleChannelOperations(String operation, NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        if (botToken.isEmpty()) {
            botToken = System.getenv("DISCORD_BOT_TOKEN");
        }

        String channelId = getStringConfig(context, "channelId", "");
        String guildId = getStringConfig(context, "guildId", "");

        return switch (operation) {
            case "get" -> {
                if (channelId.isEmpty()) {
                    yield NodeExecutionResult.failure("channelId is required");
                }
                yield callDiscordApi(botToken, "GET", "/channels/" + channelId, null);
            }
            case "list" -> {
                if (guildId.isEmpty()) {
                    yield NodeExecutionResult.failure("guildId is required");
                }
                yield callDiscordApi(botToken, "GET", "/guilds/" + guildId + "/channels", null);
            }
            case "create" -> {
                if (guildId.isEmpty()) {
                    yield NodeExecutionResult.failure("guildId is required");
                }
                String name = getStringConfig(context, "name", "");
                String type = getStringConfig(context, "channelType", "0"); // 0 = text, 2 = voice

                if (name.isEmpty()) {
                    yield NodeExecutionResult.failure("name is required");
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("name", name);
                payload.put("type", Integer.parseInt(type));

                String topic = getStringConfig(context, "topic", "");
                if (!topic.isEmpty()) {
                    payload.put("topic", topic);
                }

                yield callDiscordApi(botToken, "POST", "/guilds/" + guildId + "/channels", payload);
            }
            case "delete" -> {
                if (channelId.isEmpty()) {
                    yield NodeExecutionResult.failure("channelId is required");
                }
                yield callDiscordApi(botToken, "DELETE", "/channels/" + channelId, null);
            }
            case "modify" -> {
                if (channelId.isEmpty()) {
                    yield NodeExecutionResult.failure("channelId is required");
                }
                Map<String, Object> payload = new HashMap<>();
                String name = getStringConfig(context, "name", "");
                String topic = getStringConfig(context, "topic", "");

                if (!name.isEmpty()) payload.put("name", name);
                if (!topic.isEmpty()) payload.put("topic", topic);

                yield callDiscordApi(botToken, "PATCH", "/channels/" + channelId, payload);
            }
            default -> NodeExecutionResult.failure("Unknown channel operation: " + operation);
        };
    }

    private NodeExecutionResult handleWebhookOperations(String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "execute" -> executeWebhook(context);
            case "get" -> getWebhook(context);
            case "modify" -> modifyWebhook(context);
            case "delete" -> deleteWebhook(context);
            default -> NodeExecutionResult.failure("Unknown webhook operation: " + operation);
        };
    }

    private NodeExecutionResult executeWebhook(NodeExecutionContext context) throws IOException {
        String webhookUrl = getStringConfig(context, "webhookUrl", "");

        if (webhookUrl.isEmpty()) {
            return NodeExecutionResult.failure("webhookUrl is required");
        }

        Map<String, Object> payload = new HashMap<>();

        String content = getStringConfig(context, "content", "");
        if (!content.isEmpty()) {
            payload.put("content", content);
        }

        String username = getStringConfig(context, "username", "");
        if (!username.isEmpty()) {
            payload.put("username", username);
        }

        String avatarUrl = getStringConfig(context, "avatarUrl", "");
        if (!avatarUrl.isEmpty()) {
            payload.put("avatar_url", avatarUrl);
        }

        Object embeds = context.getNodeConfig().get("embeds");
        if (embeds != null) {
            payload.put("embeds", embeds);
        }

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                return NodeExecutionResult.failure("Webhook error [" + response.code() + "]: " + responseBody);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("statusCode", response.code());

            if (response.body() != null) {
                String responseBody = response.body().string();
                if (!responseBody.isEmpty()) {
                    output.put("result", objectMapper.readValue(responseBody, Object.class));
                }
            }

            log.info("Discord webhook executed successfully");
            return NodeExecutionResult.success(output);
        }
    }

    private NodeExecutionResult getWebhook(NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        String webhookId = getStringConfig(context, "webhookId", "");

        if (webhookId.isEmpty()) {
            return NodeExecutionResult.failure("webhookId is required");
        }

        return callDiscordApi(botToken, "GET", "/webhooks/" + webhookId, null);
    }

    private NodeExecutionResult modifyWebhook(NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        String webhookId = getStringConfig(context, "webhookId", "");

        if (webhookId.isEmpty()) {
            return NodeExecutionResult.failure("webhookId is required");
        }

        Map<String, Object> payload = new HashMap<>();
        String name = getStringConfig(context, "name", "");
        String avatarUrl = getStringConfig(context, "avatarUrl", "");

        if (!name.isEmpty()) payload.put("name", name);
        if (!avatarUrl.isEmpty()) payload.put("avatar", avatarUrl);

        return callDiscordApi(botToken, "PATCH", "/webhooks/" + webhookId, payload);
    }

    private NodeExecutionResult deleteWebhook(NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        String webhookId = getStringConfig(context, "webhookId", "");

        if (webhookId.isEmpty()) {
            return NodeExecutionResult.failure("webhookId is required");
        }

        return callDiscordApi(botToken, "DELETE", "/webhooks/" + webhookId, null);
    }

    private NodeExecutionResult handleUserOperations(String operation, NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        if (botToken.isEmpty()) {
            botToken = System.getenv("DISCORD_BOT_TOKEN");
        }

        return switch (operation) {
            case "get" -> {
                String userId = getStringConfig(context, "userId", "");
                if (userId.isEmpty()) {
                    yield NodeExecutionResult.failure("userId is required");
                }
                yield callDiscordApi(botToken, "GET", "/users/" + userId, null);
            }
            case "getMe" -> callDiscordApi(botToken, "GET", "/users/@me", null);
            case "dm" -> {
                String userId = getStringConfig(context, "userId", "");
                String content = getStringConfig(context, "content", "");

                if (userId.isEmpty()) {
                    yield NodeExecutionResult.failure("userId is required");
                }

                // First create DM channel
                NodeExecutionResult dmChannel = callDiscordApi(botToken, "POST", "/users/@me/channels",
                    Map.of("recipient_id", userId));

                if (!dmChannel.isSuccess()) {
                    yield dmChannel;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> channelData = (Map<String, Object>) dmChannel.getOutput().get("result");
                String channelId = channelData.get("id").toString();

                // Then send message
                Map<String, Object> payload = new HashMap<>();
                payload.put("content", content);

                yield callDiscordApi(botToken, "POST", "/channels/" + channelId + "/messages", payload);
            }
            default -> NodeExecutionResult.failure("Unknown user operation: " + operation);
        };
    }

    private NodeExecutionResult handleGuildOperations(String operation, NodeExecutionContext context) throws IOException {
        String botToken = getStringConfig(context, "botToken", "");
        String guildId = getStringConfig(context, "guildId", "");

        if (botToken.isEmpty()) {
            botToken = System.getenv("DISCORD_BOT_TOKEN");
        }

        if (guildId.isEmpty()) {
            return NodeExecutionResult.failure("guildId is required");
        }

        return switch (operation) {
            case "get" -> callDiscordApi(botToken, "GET", "/guilds/" + guildId, null);
            case "getMembers" -> {
                int limit = getIntegerConfig(context, "limit", 100);
                yield callDiscordApi(botToken, "GET", "/guilds/" + guildId + "/members?limit=" + limit, null);
            }
            case "getRoles" -> callDiscordApi(botToken, "GET", "/guilds/" + guildId + "/roles", null);
            default -> NodeExecutionResult.failure("Unknown guild operation: " + operation);
        };
    }

    private NodeExecutionResult callDiscordApi(String token, String method, String endpoint, Map<String, Object> payload) throws IOException {
        String url = DISCORD_API_BASE + endpoint;

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .header("Authorization", "Bot " + token);

        if (payload != null && !payload.isEmpty()) {
            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(payload),
                MediaType.parse("application/json")
            );
            requestBuilder.method(method, body);
            requestBuilder.header("Content-Type", "application/json");
        } else {
            switch (method) {
                case "POST" -> requestBuilder.post(RequestBody.create("", MediaType.parse("application/json")));
                case "PUT" -> requestBuilder.put(RequestBody.create("", MediaType.parse("application/json")));
                case "PATCH" -> requestBuilder.patch(RequestBody.create("", MediaType.parse("application/json")));
                case "DELETE" -> requestBuilder.delete();
                default -> requestBuilder.get();
            }
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                return NodeExecutionResult.failure("Discord API error [" + response.code() + "]: " + responseBody);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("statusCode", response.code());

            if (!responseBody.isEmpty()) {
                output.put("result", objectMapper.readValue(responseBody, Object.class));
            }

            log.info("Discord API call successful: {} {}", method, endpoint);
            return NodeExecutionResult.success(output);
        }
    }

    private int getIntegerConfig(NodeExecutionContext context, String key, int defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                Map.entry("resource", Map.of(
                    "type", "string",
                    "title", "Resource",
                    "enum", List.of("message", "channel", "webhook", "user", "guild"),
                    "default", "message"
                )),
                Map.entry("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("send", "edit", "delete", "react", "pin", "get",
                                   "list", "create", "modify", "execute", "getMe", "dm",
                                   "getMembers", "getRoles"),
                    "default", "send"
                )),
                Map.entry("botToken", Map.of(
                    "type", "string",
                    "title", "Bot Token",
                    "format", "password"
                )),
                Map.entry("channelId", Map.of(
                    "type", "string",
                    "title", "Channel ID"
                )),
                Map.entry("guildId", Map.of(
                    "type", "string",
                    "title", "Guild/Server ID"
                )),
                Map.entry("messageId", Map.of(
                    "type", "string",
                    "title", "Message ID"
                )),
                Map.entry("userId", Map.of(
                    "type", "string",
                    "title", "User ID"
                )),
                Map.entry("content", Map.of(
                    "type", "string",
                    "title", "Message Content",
                    "format", "textarea"
                )),
                Map.entry("embeds", Map.of(
                    "type", "array",
                    "title", "Embeds",
                    "description", "Discord embed objects"
                )),
                Map.entry("webhookUrl", Map.of(
                    "type", "string",
                    "title", "Webhook URL",
                    "format", "uri"
                )),
                Map.entry("username", Map.of(
                    "type", "string",
                    "title", "Webhook Username"
                )),
                Map.entry("avatarUrl", Map.of(
                    "type", "string",
                    "title", "Avatar URL"
                )),
                Map.entry("emoji", Map.of(
                    "type", "string",
                    "title", "Emoji"
                )),
                Map.entry("name", Map.of(
                    "type", "string",
                    "title", "Name"
                )),
                Map.entry("topic", Map.of(
                    "type", "string",
                    "title", "Topic"
                )),
                Map.entry("channelType", Map.of(
                    "type", "string",
                    "title", "Channel Type",
                    "enum", List.of("0", "2", "4", "5", "13"),
                    "enumNames", List.of("Text", "Voice", "Category", "Announcement", "Stage"),
                    "default", "0"
                )),
                Map.entry("tts", Map.of(
                    "type", "boolean",
                    "title", "Text-to-Speech",
                    "default", false
                ))
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
