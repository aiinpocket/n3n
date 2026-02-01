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
 * Handler for LINE Messaging API operations.
 * Supports push messages, reply messages, broadcast, and rich menu management.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LineNodeHandler extends AbstractNodeHandler {

    private static final String LINE_API_BASE = "https://api.line.me/v2";
    private static final String LINE_DATA_API = "https://api-data.line.me/v2";

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    @Override
    public String getType() {
        return "line";
    }

    @Override
    public String getDisplayName() {
        return "LINE";
    }

    @Override
    public String getDescription() {
        return "Send messages via LINE Messaging API";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "line";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String resource = getStringConfig(context, "resource", "message");
        String operation = getStringConfig(context, "operation", "push");
        String accessToken = getStringConfig(context, "accessToken", "");

        if (accessToken.isEmpty()) {
            accessToken = System.getenv("LINE_CHANNEL_ACCESS_TOKEN");
        }

        if (accessToken == null || accessToken.isEmpty()) {
            return NodeExecutionResult.failure("LINE Channel Access Token is required");
        }

        try {
            return switch (resource) {
                case "message" -> handleMessageOperations(accessToken, operation, context);
                case "profile" -> handleProfileOperations(accessToken, operation, context);
                case "group" -> handleGroupOperations(accessToken, operation, context);
                case "richMenu" -> handleRichMenuOperations(accessToken, operation, context);
                case "webhook" -> handleWebhookOperations(accessToken, operation, context);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("LINE API error: {}", e.getMessage());
            return NodeExecutionResult.failure("LINE API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult handleMessageOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "push" -> pushMessage(token, context);
            case "reply" -> replyMessage(token, context);
            case "multicast" -> multicastMessage(token, context);
            case "broadcast" -> broadcastMessage(token, context);
            case "narrowcast" -> narrowcastMessage(token, context);
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
    }

    private NodeExecutionResult pushMessage(String token, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("'to' (user ID, group ID, or room ID) is required");
        }

        List<Map<String, Object>> messages = buildMessages(context);
        if (messages.isEmpty()) {
            return NodeExecutionResult.failure("At least one message is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", to);
        payload.put("messages", messages);

        boolean notificationDisabled = getBooleanConfig(context, "notificationDisabled", false);
        if (notificationDisabled) {
            payload.put("notificationDisabled", true);
        }

        return callLineApi(token, "/bot/message/push", payload);
    }

    private NodeExecutionResult replyMessage(String token, NodeExecutionContext context) throws IOException {
        String replyToken = getStringConfig(context, "replyToken", "");

        if (replyToken.isEmpty()) {
            return NodeExecutionResult.failure("replyToken is required");
        }

        List<Map<String, Object>> messages = buildMessages(context);
        if (messages.isEmpty()) {
            return NodeExecutionResult.failure("At least one message is required");
        }

        Map<String, Object> payload = Map.of(
            "replyToken", replyToken,
            "messages", messages
        );

        return callLineApi(token, "/bot/message/reply", payload);
    }

    private NodeExecutionResult multicastMessage(String token, NodeExecutionContext context) throws IOException {
        @SuppressWarnings("unchecked")
        List<String> to = (List<String>) context.getNodeConfig().get("toList");

        if (to == null || to.isEmpty()) {
            return NodeExecutionResult.failure("'toList' (list of user IDs) is required");
        }

        List<Map<String, Object>> messages = buildMessages(context);
        if (messages.isEmpty()) {
            return NodeExecutionResult.failure("At least one message is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", to);
        payload.put("messages", messages);

        return callLineApi(token, "/bot/message/multicast", payload);
    }

    private NodeExecutionResult broadcastMessage(String token, NodeExecutionContext context) throws IOException {
        List<Map<String, Object>> messages = buildMessages(context);
        if (messages.isEmpty()) {
            return NodeExecutionResult.failure("At least one message is required");
        }

        Map<String, Object> payload = Map.of("messages", messages);

        return callLineApi(token, "/bot/message/broadcast", payload);
    }

    private NodeExecutionResult narrowcastMessage(String token, NodeExecutionContext context) throws IOException {
        List<Map<String, Object>> messages = buildMessages(context);
        if (messages.isEmpty()) {
            return NodeExecutionResult.failure("At least one message is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", messages);

        // Optional recipient filter
        Object recipient = context.getNodeConfig().get("recipient");
        if (recipient != null) {
            payload.put("recipient", recipient);
        }

        // Optional demographic filter
        Object filter = context.getNodeConfig().get("filter");
        if (filter != null) {
            payload.put("filter", filter);
        }

        return callLineApi(token, "/bot/message/narrowcast", payload);
    }

    private List<Map<String, Object>> buildMessages(NodeExecutionContext context) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // Check for custom messages array
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> customMessages = (List<Map<String, Object>>) context.getNodeConfig().get("messages");
        if (customMessages != null && !customMessages.isEmpty()) {
            return customMessages;
        }

        // Build simple text message
        String text = getStringConfig(context, "text", "");
        if (!text.isEmpty()) {
            messages.add(Map.of("type", "text", "text", text));
        }

        // Build image message
        String imageUrl = getStringConfig(context, "imageUrl", "");
        String previewUrl = getStringConfig(context, "previewUrl", "");
        if (!imageUrl.isEmpty()) {
            Map<String, Object> imageMessage = new HashMap<>();
            imageMessage.put("type", "image");
            imageMessage.put("originalContentUrl", imageUrl);
            imageMessage.put("previewImageUrl", previewUrl.isEmpty() ? imageUrl : previewUrl);
            messages.add(imageMessage);
        }

        // Build sticker message
        String packageId = getStringConfig(context, "stickerPackageId", "");
        String stickerId = getStringConfig(context, "stickerId", "");
        if (!packageId.isEmpty() && !stickerId.isEmpty()) {
            messages.add(Map.of(
                "type", "sticker",
                "packageId", packageId,
                "stickerId", stickerId
            ));
        }

        // Build location message
        String title = getStringConfig(context, "locationTitle", "");
        String address = getStringConfig(context, "locationAddress", "");
        Double latitude = getDoubleConfig(context, "latitude", null);
        Double longitude = getDoubleConfig(context, "longitude", null);

        if (latitude != null && longitude != null) {
            Map<String, Object> locationMessage = new HashMap<>();
            locationMessage.put("type", "location");
            locationMessage.put("latitude", latitude);
            locationMessage.put("longitude", longitude);
            if (!title.isEmpty()) locationMessage.put("title", title);
            if (!address.isEmpty()) locationMessage.put("address", address);
            messages.add(locationMessage);
        }

        return messages;
    }

    private NodeExecutionResult handleProfileOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        String userId = getStringConfig(context, "userId", "");

        if (userId.isEmpty()) {
            return NodeExecutionResult.failure("userId is required");
        }

        return switch (operation) {
            case "get" -> callLineApiGet(token, "/bot/profile/" + userId);
            default -> NodeExecutionResult.failure("Unknown profile operation: " + operation);
        };
    }

    private NodeExecutionResult handleGroupOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        String groupId = getStringConfig(context, "groupId", "");

        return switch (operation) {
            case "getSummary" -> {
                if (groupId.isEmpty()) yield NodeExecutionResult.failure("groupId is required");
                yield callLineApiGet(token, "/bot/group/" + groupId + "/summary");
            }
            case "getMemberCount" -> {
                if (groupId.isEmpty()) yield NodeExecutionResult.failure("groupId is required");
                yield callLineApiGet(token, "/bot/group/" + groupId + "/members/count");
            }
            case "getMemberIds" -> {
                if (groupId.isEmpty()) yield NodeExecutionResult.failure("groupId is required");
                String start = getStringConfig(context, "start", "");
                String url = "/bot/group/" + groupId + "/members/ids";
                if (!start.isEmpty()) url += "?start=" + start;
                yield callLineApiGet(token, url);
            }
            case "getMemberProfile" -> {
                String userId = getStringConfig(context, "userId", "");
                if (groupId.isEmpty() || userId.isEmpty()) {
                    yield NodeExecutionResult.failure("groupId and userId are required");
                }
                yield callLineApiGet(token, "/bot/group/" + groupId + "/member/" + userId);
            }
            case "leave" -> {
                if (groupId.isEmpty()) yield NodeExecutionResult.failure("groupId is required");
                yield callLineApiPost(token, "/bot/group/" + groupId + "/leave", Map.of());
            }
            default -> NodeExecutionResult.failure("Unknown group operation: " + operation);
        };
    }

    private NodeExecutionResult handleRichMenuOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "list" -> callLineApiGet(token, "/bot/richmenu/list");
            case "get" -> {
                String richMenuId = getStringConfig(context, "richMenuId", "");
                if (richMenuId.isEmpty()) yield NodeExecutionResult.failure("richMenuId is required");
                yield callLineApiGet(token, "/bot/richmenu/" + richMenuId);
            }
            case "create" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> richMenu = (Map<String, Object>) context.getNodeConfig().get("richMenu");
                if (richMenu == null) yield NodeExecutionResult.failure("richMenu object is required");
                yield callLineApi(token, "/bot/richmenu", richMenu);
            }
            case "delete" -> {
                String richMenuId = getStringConfig(context, "richMenuId", "");
                if (richMenuId.isEmpty()) yield NodeExecutionResult.failure("richMenuId is required");
                yield callLineApiDelete(token, "/bot/richmenu/" + richMenuId);
            }
            case "setDefault" -> {
                String richMenuId = getStringConfig(context, "richMenuId", "");
                if (richMenuId.isEmpty()) yield NodeExecutionResult.failure("richMenuId is required");
                yield callLineApiPost(token, "/bot/user/all/richmenu/" + richMenuId, Map.of());
            }
            case "getDefault" -> callLineApiGet(token, "/bot/user/all/richmenu");
            case "linkToUser" -> {
                String richMenuId = getStringConfig(context, "richMenuId", "");
                String userId = getStringConfig(context, "userId", "");
                if (richMenuId.isEmpty() || userId.isEmpty()) {
                    yield NodeExecutionResult.failure("richMenuId and userId are required");
                }
                yield callLineApiPost(token, "/bot/user/" + userId + "/richmenu/" + richMenuId, Map.of());
            }
            case "unlinkFromUser" -> {
                String userId = getStringConfig(context, "userId", "");
                if (userId.isEmpty()) yield NodeExecutionResult.failure("userId is required");
                yield callLineApiDelete(token, "/bot/user/" + userId + "/richmenu");
            }
            default -> NodeExecutionResult.failure("Unknown rich menu operation: " + operation);
        };
    }

    private NodeExecutionResult handleWebhookOperations(String token, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "getEndpoint" -> callLineApiGet(token, "/bot/channel/webhook/endpoint");
            case "setEndpoint" -> {
                String endpoint = getStringConfig(context, "webhookEndpoint", "");
                if (endpoint.isEmpty()) yield NodeExecutionResult.failure("webhookEndpoint is required");
                yield callLineApiPut(token, "/bot/channel/webhook/endpoint", Map.of("endpoint", endpoint));
            }
            case "test" -> callLineApiPost(token, "/bot/channel/webhook/test", Map.of());
            default -> NodeExecutionResult.failure("Unknown webhook operation: " + operation);
        };
    }

    private NodeExecutionResult callLineApi(String token, String endpoint, Map<String, Object> payload) throws IOException {
        String url = LINE_API_BASE + endpoint;

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult callLineApiGet(String token, String endpoint) throws IOException {
        String url = LINE_API_BASE + endpoint;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer " + token)
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult callLineApiPost(String token, String endpoint, Map<String, Object> payload) throws IOException {
        return callLineApi(token, endpoint, payload);
    }

    private NodeExecutionResult callLineApiPut(String token, String endpoint, Map<String, Object> payload) throws IOException {
        String url = LINE_API_BASE + endpoint;

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult callLineApiDelete(String token, String endpoint) throws IOException {
        String url = LINE_API_BASE + endpoint;

        Request request = new Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", "Bearer " + token)
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult executeRequest(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                String errorMessage = responseBody;
                if (!responseBody.isEmpty()) {
                    try {
                        JsonNode json = objectMapper.readTree(responseBody);
                        errorMessage = json.path("message").asText(responseBody);
                    } catch (Exception ignored) {}
                }
                return NodeExecutionResult.failure("LINE API error [" + response.code() + "]: " + errorMessage);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("statusCode", response.code());

            if (!responseBody.isEmpty()) {
                output.put("result", objectMapper.readValue(responseBody, Object.class));
            }

            log.info("LINE API call successful: {}", request.url());
            return NodeExecutionResult.success(output);
        }
    }


    private Double getDoubleConfig(NodeExecutionContext context, String key, Double defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
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
                    "enum", List.of("message", "profile", "group", "richMenu", "webhook"),
                    "default", "message"
                )),
                Map.entry("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("push", "reply", "multicast", "broadcast", "narrowcast",
                                   "get", "getSummary", "getMemberCount", "getMemberIds", "getMemberProfile",
                                   "leave", "list", "create", "delete", "setDefault", "getDefault",
                                   "linkToUser", "unlinkFromUser", "getEndpoint", "setEndpoint", "test"),
                    "default", "push"
                )),
                Map.entry("accessToken", Map.of(
                    "type", "string",
                    "title", "Channel Access Token",
                    "format", "password"
                )),
                Map.entry("to", Map.of(
                    "type", "string",
                    "title", "To",
                    "description", "User ID, Group ID, or Room ID"
                )),
                Map.entry("replyToken", Map.of(
                    "type", "string",
                    "title", "Reply Token",
                    "description", "Reply token from webhook event"
                )),
                Map.entry("text", Map.of(
                    "type", "string",
                    "title", "Message Text",
                    "format", "textarea"
                )),
                Map.entry("imageUrl", Map.of(
                    "type", "string",
                    "title", "Image URL",
                    "format", "uri"
                )),
                Map.entry("previewUrl", Map.of(
                    "type", "string",
                    "title", "Preview Image URL",
                    "format", "uri"
                )),
                Map.entry("stickerPackageId", Map.of(
                    "type", "string",
                    "title", "Sticker Package ID"
                )),
                Map.entry("stickerId", Map.of(
                    "type", "string",
                    "title", "Sticker ID"
                )),
                Map.entry("locationTitle", Map.of(
                    "type", "string",
                    "title", "Location Title"
                )),
                Map.entry("locationAddress", Map.of(
                    "type", "string",
                    "title", "Location Address"
                )),
                Map.entry("latitude", Map.of(
                    "type", "number",
                    "title", "Latitude"
                )),
                Map.entry("longitude", Map.of(
                    "type", "number",
                    "title", "Longitude"
                )),
                Map.entry("userId", Map.of(
                    "type", "string",
                    "title", "User ID"
                )),
                Map.entry("groupId", Map.of(
                    "type", "string",
                    "title", "Group ID"
                )),
                Map.entry("richMenuId", Map.of(
                    "type", "string",
                    "title", "Rich Menu ID"
                )),
                Map.entry("notificationDisabled", Map.of(
                    "type", "boolean",
                    "title", "Disable Notification",
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
