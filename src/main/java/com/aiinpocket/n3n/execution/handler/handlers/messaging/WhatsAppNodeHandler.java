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
 * Handler for WhatsApp Business Cloud API operations.
 * Supports sending messages, templates, media, and interactive messages.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsAppNodeHandler extends AbstractNodeHandler {

    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v18.0";

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    @Override
    public String getType() {
        return "whatsapp";
    }

    @Override
    public String getDisplayName() {
        return "WhatsApp";
    }

    @Override
    public String getDescription() {
        return "Send messages via WhatsApp Business Cloud API";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "whatsapp";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String resource = getStringConfig(context, "resource", "message");
        String operation = getStringConfig(context, "operation", "sendText");
        String accessToken = getStringConfig(context, "accessToken", "");
        String phoneNumberId = getStringConfig(context, "phoneNumberId", "");

        if (accessToken.isEmpty()) {
            accessToken = System.getenv("WHATSAPP_ACCESS_TOKEN");
        }

        if (phoneNumberId.isEmpty()) {
            phoneNumberId = System.getenv("WHATSAPP_PHONE_NUMBER_ID");
        }

        if (accessToken == null || accessToken.isEmpty()) {
            return NodeExecutionResult.failure("WhatsApp access token is required");
        }

        if (phoneNumberId == null || phoneNumberId.isEmpty()) {
            return NodeExecutionResult.failure("WhatsApp phone number ID is required");
        }

        try {
            return switch (resource) {
                case "message" -> handleMessageOperations(accessToken, phoneNumberId, operation, context);
                case "template" -> handleTemplateOperations(accessToken, phoneNumberId, operation, context);
                case "media" -> handleMediaOperations(accessToken, phoneNumberId, operation, context);
                case "interactive" -> handleInteractiveOperations(accessToken, phoneNumberId, operation, context);
                case "contact" -> handleContactOperations(accessToken, phoneNumberId, operation, context);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (IOException e) {
            log.error("WhatsApp API error: {}", e.getMessage());
            return NodeExecutionResult.failure("WhatsApp API error: " + e.getMessage());
        }
    }

    private NodeExecutionResult handleMessageOperations(String token, String phoneNumberId, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "sendText" -> sendTextMessage(token, phoneNumberId, context);
            case "sendReaction" -> sendReaction(token, phoneNumberId, context);
            case "markRead" -> markAsRead(token, phoneNumberId, context);
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
    }

    private NodeExecutionResult sendTextMessage(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String body = getStringConfig(context, "body", "");
        boolean previewUrl = getBooleanConfig(context, "previewUrl", false);

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("'to' (recipient phone number) is required");
        }
        if (body.isEmpty()) {
            return NodeExecutionResult.failure("Message body is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of(
            "preview_url", previewUrl,
            "body", body
        ));

        // Optional context for reply
        String replyToMessageId = getStringConfig(context, "replyToMessageId", "");
        if (!replyToMessageId.isEmpty()) {
            payload.put("context", Map.of("message_id", replyToMessageId));
        }

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult sendReaction(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String messageId = getStringConfig(context, "messageId", "");
        String emoji = getStringConfig(context, "emoji", "");

        if (to.isEmpty() || messageId.isEmpty() || emoji.isEmpty()) {
            return NodeExecutionResult.failure("'to', 'messageId', and 'emoji' are required");
        }

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "recipient_type", "individual",
            "to", to,
            "type", "reaction",
            "reaction", Map.of(
                "message_id", messageId,
                "emoji", emoji
            )
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult markAsRead(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String messageId = getStringConfig(context, "messageId", "");

        if (messageId.isEmpty()) {
            return NodeExecutionResult.failure("messageId is required");
        }

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "status", "read",
            "message_id", messageId
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult handleTemplateOperations(String token, String phoneNumberId, String operation, NodeExecutionContext context) throws IOException {
        if (!"send".equals(operation)) {
            return NodeExecutionResult.failure("Unknown template operation: " + operation);
        }

        String to = getStringConfig(context, "to", "");
        String templateName = getStringConfig(context, "templateName", "");
        String languageCode = getStringConfig(context, "languageCode", "en_US");

        if (to.isEmpty() || templateName.isEmpty()) {
            return NodeExecutionResult.failure("'to' and 'templateName' are required");
        }

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));

        // Template components (header, body, buttons)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) context.getNodeConfig().get("templateComponents");
        if (components != null && !components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult handleMediaOperations(String token, String phoneNumberId, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "sendImage" -> sendMedia(token, phoneNumberId, "image", context);
            case "sendVideo" -> sendMedia(token, phoneNumberId, "video", context);
            case "sendAudio" -> sendMedia(token, phoneNumberId, "audio", context);
            case "sendDocument" -> sendMedia(token, phoneNumberId, "document", context);
            case "sendSticker" -> sendMedia(token, phoneNumberId, "sticker", context);
            case "upload" -> uploadMedia(token, phoneNumberId, context);
            case "getUrl" -> getMediaUrl(token, context);
            case "delete" -> deleteMedia(token, context);
            default -> NodeExecutionResult.failure("Unknown media operation: " + operation);
        };
    }

    private NodeExecutionResult sendMedia(String token, String phoneNumberId, String mediaType, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String mediaUrl = getStringConfig(context, "mediaUrl", "");
        String mediaId = getStringConfig(context, "mediaId", "");
        String caption = getStringConfig(context, "caption", "");
        String filename = getStringConfig(context, "filename", "");

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("'to' is required");
        }

        if (mediaUrl.isEmpty() && mediaId.isEmpty()) {
            return NodeExecutionResult.failure("Either 'mediaUrl' or 'mediaId' is required");
        }

        Map<String, Object> mediaObject = new HashMap<>();
        if (!mediaId.isEmpty()) {
            mediaObject.put("id", mediaId);
        } else {
            mediaObject.put("link", mediaUrl);
        }

        if (!caption.isEmpty() && (mediaType.equals("image") || mediaType.equals("video") || mediaType.equals("document"))) {
            mediaObject.put("caption", caption);
        }

        if (!filename.isEmpty() && mediaType.equals("document")) {
            mediaObject.put("filename", filename);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", mediaType);
        payload.put(mediaType, mediaObject);

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult uploadMedia(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String mediaUrl = getStringConfig(context, "mediaUrl", "");
        String mimeType = getStringConfig(context, "mimeType", "");

        if (mediaUrl.isEmpty() || mimeType.isEmpty()) {
            return NodeExecutionResult.failure("'mediaUrl' and 'mimeType' are required");
        }

        // Download the media first
        Request downloadRequest = new Request.Builder().url(mediaUrl).get().build();
        byte[] mediaBytes;
        try (Response downloadResponse = httpClient.newCall(downloadRequest).execute()) {
            if (!downloadResponse.isSuccessful() || downloadResponse.body() == null) {
                return NodeExecutionResult.failure("Failed to download media from URL");
            }
            mediaBytes = downloadResponse.body().bytes();
        }

        // Upload to WhatsApp
        String url = WHATSAPP_API_BASE + "/" + phoneNumberId + "/media";

        RequestBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("messaging_product", "whatsapp")
            .addFormDataPart("file", "media", RequestBody.create(mediaBytes, MediaType.parse(mimeType)))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response);
        }
    }

    private NodeExecutionResult getMediaUrl(String token, NodeExecutionContext context) throws IOException {
        String mediaId = getStringConfig(context, "mediaId", "");

        if (mediaId.isEmpty()) {
            return NodeExecutionResult.failure("mediaId is required");
        }

        String url = WHATSAPP_API_BASE + "/" + mediaId;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response);
        }
    }

    private NodeExecutionResult deleteMedia(String token, NodeExecutionContext context) throws IOException {
        String mediaId = getStringConfig(context, "mediaId", "");

        if (mediaId.isEmpty()) {
            return NodeExecutionResult.failure("mediaId is required");
        }

        String url = WHATSAPP_API_BASE + "/" + mediaId;

        Request request = new Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response);
        }
    }

    private NodeExecutionResult handleInteractiveOperations(String token, String phoneNumberId, String operation, NodeExecutionContext context) throws IOException {
        return switch (operation) {
            case "sendButtons" -> sendInteractiveButtons(token, phoneNumberId, context);
            case "sendList" -> sendInteractiveList(token, phoneNumberId, context);
            case "sendCta" -> sendCtaButton(token, phoneNumberId, context);
            default -> NodeExecutionResult.failure("Unknown interactive operation: " + operation);
        };
    }

    private NodeExecutionResult sendInteractiveButtons(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String bodyText = getStringConfig(context, "body", "");

        if (to.isEmpty() || bodyText.isEmpty()) {
            return NodeExecutionResult.failure("'to' and 'body' are required");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buttons = (List<Map<String, Object>>) context.getNodeConfig().get("buttons");
        if (buttons == null || buttons.isEmpty()) {
            return NodeExecutionResult.failure("At least one button is required");
        }

        // Convert buttons to WhatsApp format
        List<Map<String, Object>> whatsappButtons = new ArrayList<>();
        for (Map<String, Object> button : buttons) {
            whatsappButtons.add(Map.of(
                "type", "reply",
                "reply", Map.of(
                    "id", button.getOrDefault("id", UUID.randomUUID().toString()),
                    "title", button.get("title")
                )
            ));
        }

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", Map.of("buttons", whatsappButtons));

        // Optional header
        String headerText = getStringConfig(context, "headerText", "");
        if (!headerText.isEmpty()) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }

        // Optional footer
        String footerText = getStringConfig(context, "footerText", "");
        if (!footerText.isEmpty()) {
            interactive.put("footer", Map.of("text", footerText));
        }

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "interactive",
            "interactive", interactive
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult sendInteractiveList(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String bodyText = getStringConfig(context, "body", "");
        String buttonText = getStringConfig(context, "buttonText", "Select");

        if (to.isEmpty() || bodyText.isEmpty()) {
            return NodeExecutionResult.failure("'to' and 'body' are required");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) context.getNodeConfig().get("sections");
        if (sections == null || sections.isEmpty()) {
            return NodeExecutionResult.failure("At least one section is required");
        }

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "list");
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", Map.of(
            "button", buttonText,
            "sections", sections
        ));

        // Optional header
        String headerText = getStringConfig(context, "headerText", "");
        if (!headerText.isEmpty()) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }

        // Optional footer
        String footerText = getStringConfig(context, "footerText", "");
        if (!footerText.isEmpty()) {
            interactive.put("footer", Map.of("text", footerText));
        }

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "interactive",
            "interactive", interactive
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult sendCtaButton(String token, String phoneNumberId, NodeExecutionContext context) throws IOException {
        String to = getStringConfig(context, "to", "");
        String bodyText = getStringConfig(context, "body", "");
        String buttonText = getStringConfig(context, "buttonText", "");
        String buttonUrl = getStringConfig(context, "buttonUrl", "");

        if (to.isEmpty() || bodyText.isEmpty() || buttonText.isEmpty() || buttonUrl.isEmpty()) {
            return NodeExecutionResult.failure("'to', 'body', 'buttonText', and 'buttonUrl' are required");
        }

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "cta_url");
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", Map.of(
            "name", "cta_url",
            "parameters", Map.of(
                "display_text", buttonText,
                "url", buttonUrl
            )
        ));

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "interactive",
            "interactive", interactive
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult handleContactOperations(String token, String phoneNumberId, String operation, NodeExecutionContext context) throws IOException {
        if (!"send".equals(operation)) {
            return NodeExecutionResult.failure("Unknown contact operation: " + operation);
        }

        String to = getStringConfig(context, "to", "");

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("'to' is required");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) context.getNodeConfig().get("contacts");
        if (contacts == null || contacts.isEmpty()) {
            return NodeExecutionResult.failure("At least one contact is required");
        }

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "contacts",
            "contacts", contacts
        );

        return callWhatsAppApi(token, phoneNumberId, "messages", payload);
    }

    private NodeExecutionResult callWhatsAppApi(String token, String phoneNumberId, String endpoint, Map<String, Object> payload) throws IOException {
        String url = WHATSAPP_API_BASE + "/" + phoneNumberId + "/" + endpoint;

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

        try (Response response = httpClient.newCall(request).execute()) {
            return parseResponse(response);
        }
    }

    private NodeExecutionResult parseResponse(Response response) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";

        if (!response.isSuccessful()) {
            String errorMessage = responseBody;
            if (!responseBody.isEmpty()) {
                try {
                    JsonNode json = objectMapper.readTree(responseBody);
                    JsonNode error = json.path("error");
                    if (!error.isMissingNode()) {
                        errorMessage = error.path("message").asText(responseBody);
                        int errorCode = error.path("code").asInt(0);
                        if (errorCode > 0) {
                            errorMessage = "[" + errorCode + "] " + errorMessage;
                        }
                    }
                } catch (Exception ignored) {}
            }
            return NodeExecutionResult.failure("WhatsApp API error [" + response.code() + "]: " + errorMessage);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("success", true);
        output.put("statusCode", response.code());

        if (!responseBody.isEmpty()) {
            output.put("result", objectMapper.readValue(responseBody, Object.class));

            // Extract message ID if present
            try {
                JsonNode json = objectMapper.readTree(responseBody);
                JsonNode messages = json.path("messages");
                if (messages.isArray() && messages.size() > 0) {
                    output.put("messageId", messages.get(0).path("id").asText());
                }
            } catch (Exception ignored) {}
        }

        log.info("WhatsApp API call successful");
        return NodeExecutionResult.success(output);
    }


    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                Map.entry("resource", Map.of(
                    "type", "string",
                    "title", "Resource",
                    "enum", List.of("message", "template", "media", "interactive", "contact"),
                    "default", "message"
                )),
                Map.entry("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("sendText", "sendReaction", "markRead", "send",
                                   "sendImage", "sendVideo", "sendAudio", "sendDocument", "sendSticker",
                                   "upload", "getUrl", "delete",
                                   "sendButtons", "sendList", "sendCta"),
                    "default", "sendText"
                )),
                Map.entry("accessToken", Map.of(
                    "type", "string",
                    "title", "Access Token",
                    "description", "WhatsApp Business API access token",
                    "format", "password"
                )),
                Map.entry("phoneNumberId", Map.of(
                    "type", "string",
                    "title", "Phone Number ID",
                    "description", "WhatsApp Business phone number ID"
                )),
                Map.entry("to", Map.of(
                    "type", "string",
                    "title", "To",
                    "description", "Recipient phone number with country code (e.g., 14155238886)"
                )),
                Map.entry("body", Map.of(
                    "type", "string",
                    "title", "Message Body",
                    "format", "textarea"
                )),
                Map.entry("previewUrl", Map.of(
                    "type", "boolean",
                    "title", "Preview URL",
                    "default", false
                )),
                Map.entry("templateName", Map.of(
                    "type", "string",
                    "title", "Template Name"
                )),
                Map.entry("languageCode", Map.of(
                    "type", "string",
                    "title", "Language Code",
                    "default", "en_US"
                )),
                Map.entry("mediaUrl", Map.of(
                    "type", "string",
                    "title", "Media URL",
                    "format", "uri"
                )),
                Map.entry("mediaId", Map.of(
                    "type", "string",
                    "title", "Media ID"
                )),
                Map.entry("caption", Map.of(
                    "type", "string",
                    "title", "Caption"
                )),
                Map.entry("filename", Map.of(
                    "type", "string",
                    "title", "Filename"
                )),
                Map.entry("mimeType", Map.of(
                    "type", "string",
                    "title", "MIME Type"
                )),
                Map.entry("messageId", Map.of(
                    "type", "string",
                    "title", "Message ID"
                )),
                Map.entry("emoji", Map.of(
                    "type", "string",
                    "title", "Emoji"
                )),
                Map.entry("headerText", Map.of(
                    "type", "string",
                    "title", "Header Text"
                )),
                Map.entry("footerText", Map.of(
                    "type", "string",
                    "title", "Footer Text"
                )),
                Map.entry("buttonText", Map.of(
                    "type", "string",
                    "title", "Button Text"
                )),
                Map.entry("buttonUrl", Map.of(
                    "type", "string",
                    "title", "Button URL",
                    "format", "uri"
                )),
                Map.entry("replyToMessageId", Map.of(
                    "type", "string",
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
