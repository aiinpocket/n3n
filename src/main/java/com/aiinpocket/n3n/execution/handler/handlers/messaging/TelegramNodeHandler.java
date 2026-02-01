package com.aiinpocket.n3n.execution.handler.handlers.messaging;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handler for Telegram Bot API operations.
 * Supports sending messages, photos, documents, and managing webhooks.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramNodeHandler extends AbstractNodeHandler {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    @Override
    public String getType() {
        return "telegram";
    }

    @Override
    public String getDisplayName() {
        return "Telegram";
    }

    @Override
    public String getDescription() {
        return "Send messages and interact with Telegram Bot API";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "telegram";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String resource = getStringConfig(context, "resource", "message");
        String operation = getStringConfig(context, "operation", "send");
        String botToken = getStringConfig(context, "botToken", "");

        if (botToken.isEmpty()) {
            botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        }

        if (botToken == null || botToken.isEmpty()) {
            return NodeExecutionResult.failure("Telegram bot token is required");
        }

        try {
            return switch (resource) {
                case "message" -> handleMessageOperations(botToken, operation, context);
                case "photo" -> handlePhotoOperations(botToken, operation, context);
                case "document" -> handleDocumentOperations(botToken, operation, context);
                case "chat" -> handleChatOperations(botToken, operation, context);
                case "webhook" -> handleWebhookOperations(botToken, operation, context);
                case "bot" -> handleBotOperations(botToken, operation, context);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("Telegram API error: {}", e.getMessage());
            return NodeExecutionResult.failure("Telegram API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult handleMessageOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "send" -> sendMessage(token, context);
            case "edit" -> editMessage(token, context);
            case "delete" -> deleteMessage(token, context);
            case "forward" -> forwardMessage(token, context);
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
    }

    private NodeExecutionResult sendMessage(String token, NodeExecutionContext context) throws IOException {
        String chatId = getStringConfig(context, "chatId", "");
        String text = getStringConfig(context, "text", "");
        String parseMode = getStringConfig(context, "parseMode", "");
        boolean disableNotification = getBooleanConfig(context, "disableNotification", false);
        Integer replyToMessageId = getIntegerConfig(context, "replyToMessageId", null);

        if (chatId.isEmpty()) {
            return NodeExecutionResult.failure("chatId is required");
        }
        if (text.isEmpty()) {
            return NodeExecutionResult.failure("text is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);

        if (!parseMode.isEmpty()) {
            payload.put("parse_mode", parseMode);
        }
        if (disableNotification) {
            payload.put("disable_notification", true);
        }
        if (replyToMessageId != null) {
            payload.put("reply_to_message_id", replyToMessageId);
        }

        // Reply keyboard markup
        Object replyMarkup = context.getNodeConfig().get("replyMarkup");
        if (replyMarkup != null) {
            payload.put("reply_markup", replyMarkup);
        }

        return callTelegramApi(token, "sendMessage", payload);
    }

    private NodeExecutionResult editMessage(String token, NodeExecutionContext context) throws IOException {
        String chatId = getStringConfig(context, "chatId", "");
        Integer messageId = getIntegerConfig(context, "messageId", null);
        String text = getStringConfig(context, "text", "");
        String parseMode = getStringConfig(context, "parseMode", "");

        if (chatId.isEmpty() || messageId == null) {
            return NodeExecutionResult.failure("chatId and messageId are required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("text", text);

        if (!parseMode.isEmpty()) {
            payload.put("parse_mode", parseMode);
        }

        return callTelegramApi(token, "editMessageText", payload);
    }

    private NodeExecutionResult deleteMessage(String token, NodeExecutionContext context) throws IOException {
        String chatId = getStringConfig(context, "chatId", "");
        Integer messageId = getIntegerConfig(context, "messageId", null);

        if (chatId.isEmpty() || messageId == null) {
            return NodeExecutionResult.failure("chatId and messageId are required");
        }

        Map<String, Object> payload = Map.of(
            "chat_id", chatId,
            "message_id", messageId
        );

        return callTelegramApi(token, "deleteMessage", payload);
    }

    private NodeExecutionResult forwardMessage(String token, NodeExecutionContext context) throws IOException {
        String chatId = getStringConfig(context, "chatId", "");
        String fromChatId = getStringConfig(context, "fromChatId", "");
        Integer messageId = getIntegerConfig(context, "messageId", null);

        if (chatId.isEmpty() || fromChatId.isEmpty() || messageId == null) {
            return NodeExecutionResult.failure("chatId, fromChatId, and messageId are required");
        }

        Map<String, Object> payload = Map.of(
            "chat_id", chatId,
            "from_chat_id", fromChatId,
            "message_id", messageId
        );

        return callTelegramApi(token, "forwardMessage", payload);
    }

    private NodeExecutionResult handlePhotoOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        if (!"send".equals(operation)) {
            return NodeExecutionResult.failure("Unknown photo operation: " + operation);
        }

        String chatId = getStringConfig(context, "chatId", "");
        String photo = getStringConfig(context, "photo", "");
        String caption = getStringConfig(context, "caption", "");
        String parseMode = getStringConfig(context, "parseMode", "");

        if (chatId.isEmpty() || photo.isEmpty()) {
            return NodeExecutionResult.failure("chatId and photo are required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("photo", photo);

        if (!caption.isEmpty()) {
            payload.put("caption", caption);
        }
        if (!parseMode.isEmpty()) {
            payload.put("parse_mode", parseMode);
        }

        return callTelegramApi(token, "sendPhoto", payload);
    }

    private NodeExecutionResult handleDocumentOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        if (!"send".equals(operation)) {
            return NodeExecutionResult.failure("Unknown document operation: " + operation);
        }

        String chatId = getStringConfig(context, "chatId", "");
        String document = getStringConfig(context, "document", "");
        String caption = getStringConfig(context, "caption", "");

        if (chatId.isEmpty() || document.isEmpty()) {
            return NodeExecutionResult.failure("chatId and document are required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("document", document);

        if (!caption.isEmpty()) {
            payload.put("caption", caption);
        }

        return callTelegramApi(token, "sendDocument", payload);
    }

    private NodeExecutionResult handleChatOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        String chatId = getStringConfig(context, "chatId", "");

        if (chatId.isEmpty()) {
            return NodeExecutionResult.failure("chatId is required");
        }

        return switch (operation) {
            case "getInfo" -> callTelegramApi(token, "getChat", Map.of("chat_id", chatId));
            case "getMemberCount" -> callTelegramApi(token, "getChatMemberCount", Map.of("chat_id", chatId));
            case "getAdministrators" -> callTelegramApi(token, "getChatAdministrators", Map.of("chat_id", chatId));
            case "leave" -> callTelegramApi(token, "leaveChat", Map.of("chat_id", chatId));
            default -> NodeExecutionResult.failure("Unknown chat operation: " + operation);
        };
    }

    private NodeExecutionResult handleWebhookOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "set" -> {
                String url = getStringConfig(context, "webhookUrl", "");
                if (url.isEmpty()) {
                    yield NodeExecutionResult.failure("webhookUrl is required");
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("url", url);

                String secretToken = getStringConfig(context, "secretToken", "");
                if (!secretToken.isEmpty()) {
                    payload.put("secret_token", secretToken);
                }

                yield callTelegramApi(token, "setWebhook", payload);
            }
            case "delete" -> callTelegramApi(token, "deleteWebhook", Map.of());
            case "getInfo" -> callTelegramApi(token, "getWebhookInfo", Map.of());
            default -> NodeExecutionResult.failure("Unknown webhook operation: " + operation);
        };
    }

    private NodeExecutionResult handleBotOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "getMe" -> callTelegramApi(token, "getMe", Map.of());
            case "getUpdates" -> {
                Map<String, Object> payload = new HashMap<>();
                Integer offset = getIntegerConfig(context, "offset", null);
                Integer limit = getIntegerConfig(context, "limit", null);
                Integer timeout = getIntegerConfig(context, "timeout", null);

                if (offset != null) payload.put("offset", offset);
                if (limit != null) payload.put("limit", limit);
                if (timeout != null) payload.put("timeout", timeout);

                yield callTelegramApi(token, "getUpdates", payload);
            }
            case "setMyCommands" -> {
                Object commands = context.getNodeConfig().get("commands");
                if (commands == null) {
                    yield NodeExecutionResult.failure("commands are required");
                }
                yield callTelegramApi(token, "setMyCommands", Map.of("commands", commands));
            }
            case "getMyCommands" -> callTelegramApi(token, "getMyCommands", Map.of());
            default -> NodeExecutionResult.failure("Unknown bot operation: " + operation);
        };
    }

    private NodeExecutionResult callTelegramApi(String token, String method, Map<String, Object> payload) throws IOException {
        String url = TELEGRAM_API_BASE + token + "/" + method;

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            if (!json.path("ok").asBoolean(false)) {
                String error = json.path("description").asText("Unknown error");
                int errorCode = json.path("error_code").asInt(0);
                return NodeExecutionResult.failure("Telegram API error [" + errorCode + "]: " + error);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("result", objectMapper.convertValue(json.path("result"), Object.class));

            log.info("Telegram API call successful: {}", method);
            return NodeExecutionResult.success(output);
        }
    }

    private Integer getIntegerConfig(NodeExecutionContext context, String key, Integer defaultValue) {
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
                    "enum", List.of("message", "photo", "document", "chat", "webhook", "bot"),
                    "default", "message"
                )),
                Map.entry("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("send", "edit", "delete", "forward", "getInfo", "getMemberCount",
                                   "getAdministrators", "leave", "set", "getMe", "getUpdates",
                                   "setMyCommands", "getMyCommands"),
                    "default", "send"
                )),
                Map.entry("botToken", Map.of(
                    "type", "string",
                    "title", "Bot Token",
                    "description", "Telegram Bot Token from @BotFather",
                    "format", "password"
                )),
                Map.entry("chatId", Map.of(
                    "type", "string",
                    "title", "Chat ID",
                    "description", "Target chat ID (user, group, or channel)"
                )),
                Map.entry("text", Map.of(
                    "type", "string",
                    "title", "Message Text",
                    "format", "textarea"
                )),
                Map.entry("parseMode", Map.of(
                    "type", "string",
                    "title", "Parse Mode",
                    "enum", List.of("", "Markdown", "MarkdownV2", "HTML"),
                    "default", ""
                )),
                Map.entry("messageId", Map.of(
                    "type", "integer",
                    "title", "Message ID"
                )),
                Map.entry("photo", Map.of(
                    "type", "string",
                    "title", "Photo",
                    "description", "Photo URL or file_id"
                )),
                Map.entry("document", Map.of(
                    "type", "string",
                    "title", "Document",
                    "description", "Document URL or file_id"
                )),
                Map.entry("caption", Map.of(
                    "type", "string",
                    "title", "Caption"
                )),
                Map.entry("webhookUrl", Map.of(
                    "type", "string",
                    "title", "Webhook URL",
                    "format", "uri"
                )),
                Map.entry("disableNotification", Map.of(
                    "type", "boolean",
                    "title", "Disable Notification",
                    "default", false
                )),
                Map.entry("replyToMessageId", Map.of(
                    "type", "integer",
                    "title", "Reply To Message ID"
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
