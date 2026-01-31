package com.aiinpocket.n3n.execution.handler.handlers.google;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Gmail API node handler.
 *
 * Supports email operations via Gmail API v1.
 *
 * Features:
 * - List and search emails
 * - Read email content
 * - Send emails (with attachments)
 * - Draft management
 * - Label management
 *
 * Credential schema:
 * - accessToken: OAuth2 access token
 *
 * Required OAuth2 scopes:
 * - https://www.googleapis.com/auth/gmail.readonly
 * - https://www.googleapis.com/auth/gmail.send
 * - https://www.googleapis.com/auth/gmail.modify
 * - https://www.googleapis.com/auth/gmail.compose
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GmailNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";

    @Override
    public String getType() {
        return "gmail";
    }

    @Override
    public String getDisplayName() {
        return "Gmail";
    }

    @Override
    public String getDescription() {
        return "Gmail API. Send, receive, and manage emails.";
    }

    @Override
    public String getCategory() {
        return "Communication";
    }

    @Override
    public String getIcon() {
        return "gmail";
    }

    @Override
    public String getCredentialType() {
        return "gmail";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("message", ResourceDef.of("message", "Message", "Email message operations"));
        resources.put("draft", ResourceDef.of("draft", "Draft", "Draft management"));
        resources.put("label", ResourceDef.of("label", "Label", "Label management"));
        resources.put("thread", ResourceDef.of("thread", "Thread", "Email thread operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Message operations
        operations.put("message", List.of(
            OperationDef.create("list", "List Messages")
                .description("List email messages")
                .fields(List.of(
                    FieldDef.string("query", "Search Query")
                        .withDescription("Gmail search query (e.g., 'from:me is:unread')"),
                    FieldDef.string("labelIds", "Label IDs")
                        .withDescription("Comma-separated label IDs (e.g., INBOX,UNREAD)"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                        .withRange(1, 500),
                    FieldDef.bool("includeSpamTrash", "Include Spam/Trash")
                        .withDefault(false)
                ))
                .outputDescription("Returns { messages: [...], nextPageToken }")
                .build(),

            OperationDef.create("get", "Get Message")
                .description("Get full message content")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required(),
                    FieldDef.string("format", "Format")
                        .withDescription("Response format")
                        .withOptions(List.of("minimal", "full", "raw", "metadata"))
                        .withDefault("full")
                ))
                .outputDescription("Returns message object with headers and body")
                .build(),

            OperationDef.create("send", "Send Email")
                .description("Send a new email")
                .fields(List.of(
                    FieldDef.string("to", "To")
                        .withDescription("Recipient email addresses (comma-separated)")
                        .required(),
                    FieldDef.string("cc", "CC")
                        .withDescription("CC email addresses"),
                    FieldDef.string("bcc", "BCC")
                        .withDescription("BCC email addresses"),
                    FieldDef.string("subject", "Subject")
                        .withDescription("Email subject")
                        .required(),
                    FieldDef.textarea("body", "Body")
                        .withDescription("Email body (plain text)")
                        .required(),
                    FieldDef.textarea("htmlBody", "HTML Body")
                        .withDescription("HTML body (optional, overrides plain text)"),
                    FieldDef.string("replyTo", "Reply-To")
                        .withDescription("Reply-to address")
                ))
                .outputDescription("Returns { id, threadId, labelIds }")
                .build(),

            OperationDef.create("reply", "Reply to Email")
                .description("Reply to an existing email")
                .fields(List.of(
                    FieldDef.string("messageId", "Original Message ID")
                        .required(),
                    FieldDef.textarea("body", "Reply Body")
                        .required(),
                    FieldDef.textarea("htmlBody", "HTML Body"),
                    FieldDef.bool("replyAll", "Reply All")
                        .withDefault(false)
                ))
                .outputDescription("Returns sent message")
                .build(),

            OperationDef.create("trash", "Move to Trash")
                .description("Move message to trash")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required()
                ))
                .outputDescription("Returns updated message")
                .build(),

            OperationDef.create("untrash", "Remove from Trash")
                .description("Remove message from trash")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required()
                ))
                .outputDescription("Returns updated message")
                .build(),

            OperationDef.create("delete", "Delete Permanently")
                .description("Permanently delete a message")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("modify", "Modify Labels")
                .description("Add or remove labels from a message")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required(),
                    FieldDef.string("addLabelIds", "Add Labels")
                        .withDescription("Comma-separated label IDs to add"),
                    FieldDef.string("removeLabelIds", "Remove Labels")
                        .withDescription("Comma-separated label IDs to remove")
                ))
                .outputDescription("Returns updated message")
                .build(),

            OperationDef.create("markRead", "Mark as Read")
                .description("Mark message as read")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required()
                ))
                .outputDescription("Returns updated message")
                .build(),

            OperationDef.create("markUnread", "Mark as Unread")
                .description("Mark message as unread")
                .fields(List.of(
                    FieldDef.string("messageId", "Message ID")
                        .required()
                ))
                .outputDescription("Returns updated message")
                .build()
        ));

        // Draft operations
        operations.put("draft", List.of(
            OperationDef.create("list", "List Drafts")
                .description("List all drafts")
                .fields(List.of(
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                        .withRange(1, 500)
                ))
                .outputDescription("Returns { drafts: [...] }")
                .build(),

            OperationDef.create("get", "Get Draft")
                .description("Get draft content")
                .fields(List.of(
                    FieldDef.string("draftId", "Draft ID")
                        .required(),
                    FieldDef.string("format", "Format")
                        .withOptions(List.of("minimal", "full", "raw", "metadata"))
                        .withDefault("full")
                ))
                .outputDescription("Returns draft with message")
                .build(),

            OperationDef.create("create", "Create Draft")
                .description("Create a new draft")
                .fields(List.of(
                    FieldDef.string("to", "To")
                        .required(),
                    FieldDef.string("cc", "CC"),
                    FieldDef.string("bcc", "BCC"),
                    FieldDef.string("subject", "Subject")
                        .required(),
                    FieldDef.textarea("body", "Body")
                        .required(),
                    FieldDef.textarea("htmlBody", "HTML Body")
                ))
                .outputDescription("Returns created draft")
                .build(),

            OperationDef.create("update", "Update Draft")
                .description("Update an existing draft")
                .fields(List.of(
                    FieldDef.string("draftId", "Draft ID")
                        .required(),
                    FieldDef.string("to", "To"),
                    FieldDef.string("cc", "CC"),
                    FieldDef.string("bcc", "BCC"),
                    FieldDef.string("subject", "Subject"),
                    FieldDef.textarea("body", "Body"),
                    FieldDef.textarea("htmlBody", "HTML Body")
                ))
                .outputDescription("Returns updated draft")
                .build(),

            OperationDef.create("send", "Send Draft")
                .description("Send a draft")
                .fields(List.of(
                    FieldDef.string("draftId", "Draft ID")
                        .required()
                ))
                .outputDescription("Returns sent message")
                .build(),

            OperationDef.create("delete", "Delete Draft")
                .description("Delete a draft")
                .fields(List.of(
                    FieldDef.string("draftId", "Draft ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Label operations
        operations.put("label", List.of(
            OperationDef.create("list", "List Labels")
                .description("List all labels")
                .fields(List.of())
                .outputDescription("Returns { labels: [...] }")
                .build(),

            OperationDef.create("get", "Get Label")
                .description("Get label details")
                .fields(List.of(
                    FieldDef.string("labelId", "Label ID")
                        .required()
                ))
                .outputDescription("Returns label object")
                .build(),

            OperationDef.create("create", "Create Label")
                .description("Create a new label")
                .fields(List.of(
                    FieldDef.string("name", "Name")
                        .required(),
                    FieldDef.string("labelListVisibility", "List Visibility")
                        .withOptions(List.of("labelShow", "labelShowIfUnread", "labelHide"))
                        .withDefault("labelShow"),
                    FieldDef.string("messageListVisibility", "Message Visibility")
                        .withOptions(List.of("show", "hide"))
                        .withDefault("show")
                ))
                .outputDescription("Returns created label")
                .build(),

            OperationDef.create("update", "Update Label")
                .description("Update a label")
                .fields(List.of(
                    FieldDef.string("labelId", "Label ID")
                        .required(),
                    FieldDef.string("name", "Name"),
                    FieldDef.string("labelListVisibility", "List Visibility")
                        .withOptions(List.of("labelShow", "labelShowIfUnread", "labelHide")),
                    FieldDef.string("messageListVisibility", "Message Visibility")
                        .withOptions(List.of("show", "hide"))
                ))
                .outputDescription("Returns updated label")
                .build(),

            OperationDef.create("delete", "Delete Label")
                .description("Delete a label")
                .fields(List.of(
                    FieldDef.string("labelId", "Label ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Thread operations
        operations.put("thread", List.of(
            OperationDef.create("list", "List Threads")
                .description("List email threads")
                .fields(List.of(
                    FieldDef.string("query", "Search Query"),
                    FieldDef.string("labelIds", "Label IDs"),
                    FieldDef.integer("maxResults", "Max Results")
                        .withDefault(100)
                        .withRange(1, 500)
                ))
                .outputDescription("Returns { threads: [...] }")
                .build(),

            OperationDef.create("get", "Get Thread")
                .description("Get thread with all messages")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .required(),
                    FieldDef.string("format", "Format")
                        .withOptions(List.of("minimal", "full", "metadata"))
                        .withDefault("full")
                ))
                .outputDescription("Returns thread with messages")
                .build(),

            OperationDef.create("trash", "Move to Trash")
                .description("Move entire thread to trash")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .required()
                ))
                .outputDescription("Returns updated thread")
                .build(),

            OperationDef.create("delete", "Delete Thread")
                .description("Permanently delete thread")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        try {
            String accessToken = getCredentialValue(credential, "accessToken");

            if (accessToken == null || accessToken.isEmpty()) {
                return NodeExecutionResult.failure("Access token is required");
            }

            return switch (resource) {
                case "message" -> executeMessageOperation(accessToken, operation, params);
                case "draft" -> executeDraftOperation(accessToken, operation, params);
                case "label" -> executeLabelOperation(accessToken, operation, params);
                case "thread" -> executeThreadOperation(accessToken, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Gmail API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Gmail API error: " + e.getMessage());
        }
    }

    // ==================== Message Operations ====================

    private NodeExecutionResult executeMessageOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String query = getParam(params, "query", "");
                String labelIds = getParam(params, "labelIds", "");
                int maxResults = getIntParam(params, "maxResults", 100);
                boolean includeSpamTrash = getBoolParam(params, "includeSpamTrash", false);

                StringBuilder urlBuilder = new StringBuilder(GMAIL_API_BASE + "/messages");
                urlBuilder.append("?maxResults=").append(maxResults);
                urlBuilder.append("&includeSpamTrash=").append(includeSpamTrash);

                if (!query.isEmpty()) {
                    urlBuilder.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
                }
                if (!labelIds.isEmpty()) {
                    for (String labelId : labelIds.split(",")) {
                        urlBuilder.append("&labelIds=").append(labelId.trim());
                    }
                }

                yield executeGet(urlBuilder.toString(), accessToken);
            }
            case "get" -> {
                String messageId = getRequiredParam(params, "messageId");
                String format = getParam(params, "format", "full");

                String url = GMAIL_API_BASE + "/messages/" + messageId + "?format=" + format;
                yield executeGet(url, accessToken);
            }
            case "send" -> {
                String to = getRequiredParam(params, "to");
                String cc = getParam(params, "cc", "");
                String bcc = getParam(params, "bcc", "");
                String subject = getRequiredParam(params, "subject");
                String body = getRequiredParam(params, "body");
                String htmlBody = getParam(params, "htmlBody", "");
                String replyTo = getParam(params, "replyTo", "");

                String rawMessage = buildRawMessage(to, cc, bcc, subject, body, htmlBody, replyTo, null, null);

                Map<String, Object> requestBody = Map.of("raw", rawMessage);

                String url = GMAIL_API_BASE + "/messages/send";
                yield executePost(url, accessToken, requestBody);
            }
            case "reply" -> {
                String originalMessageId = getRequiredParam(params, "messageId");
                String body = getRequiredParam(params, "body");
                String htmlBody = getParam(params, "htmlBody", "");
                boolean replyAll = getBoolParam(params, "replyAll", false);

                // Get original message
                String getUrl = GMAIL_API_BASE + "/messages/" + originalMessageId + "?format=full";
                Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

                Map<String, Object> originalMessage;
                try (Response response = httpClient.newCall(getRequest).execute()) {
                    originalMessage = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
                }

                // Extract headers
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) originalMessage.get("payload");
                @SuppressWarnings("unchecked")
                List<Map<String, String>> headers = (List<Map<String, String>>) payload.get("headers");

                String originalFrom = "";
                String originalTo = "";
                String originalSubject = "";
                String originalMessageIdHeader = "";
                String references = "";

                for (Map<String, String> header : headers) {
                    String name = header.get("name").toLowerCase();
                    switch (name) {
                        case "from" -> originalFrom = header.get("value");
                        case "to" -> originalTo = header.get("value");
                        case "subject" -> originalSubject = header.get("value");
                        case "message-id" -> originalMessageIdHeader = header.get("value");
                        case "references" -> references = header.get("value");
                    }
                }

                String to = originalFrom;
                String cc = "";
                if (replyAll && !originalTo.isEmpty()) {
                    cc = originalTo;
                }

                String subject = originalSubject.startsWith("Re:") ? originalSubject : "Re: " + originalSubject;
                String threadId = (String) originalMessage.get("threadId");

                // Build references header
                String newReferences = references.isEmpty() ? originalMessageIdHeader : references + " " + originalMessageIdHeader;

                String rawMessage = buildRawMessage(to, cc, "", subject, body, htmlBody, "", originalMessageIdHeader, newReferences);

                Map<String, Object> requestBody = Map.of("raw", rawMessage, "threadId", threadId);

                String url = GMAIL_API_BASE + "/messages/send";
                yield executePost(url, accessToken, requestBody);
            }
            case "trash" -> {
                String messageId = getRequiredParam(params, "messageId");
                String url = GMAIL_API_BASE + "/messages/" + messageId + "/trash";
                yield executePost(url, accessToken, Map.of());
            }
            case "untrash" -> {
                String messageId = getRequiredParam(params, "messageId");
                String url = GMAIL_API_BASE + "/messages/" + messageId + "/untrash";
                yield executePost(url, accessToken, Map.of());
            }
            case "delete" -> {
                String messageId = getRequiredParam(params, "messageId");
                String url = GMAIL_API_BASE + "/messages/" + messageId;
                yield executeDelete(url, accessToken);
            }
            case "modify" -> {
                String messageId = getRequiredParam(params, "messageId");
                String addLabelIds = getParam(params, "addLabelIds", "");
                String removeLabelIds = getParam(params, "removeLabelIds", "");

                Map<String, Object> requestBody = new LinkedHashMap<>();
                if (!addLabelIds.isEmpty()) {
                    requestBody.put("addLabelIds", Arrays.asList(addLabelIds.split(",")));
                }
                if (!removeLabelIds.isEmpty()) {
                    requestBody.put("removeLabelIds", Arrays.asList(removeLabelIds.split(",")));
                }

                String url = GMAIL_API_BASE + "/messages/" + messageId + "/modify";
                yield executePost(url, accessToken, requestBody);
            }
            case "markRead" -> {
                String messageId = getRequiredParam(params, "messageId");
                String url = GMAIL_API_BASE + "/messages/" + messageId + "/modify";
                yield executePost(url, accessToken, Map.of("removeLabelIds", List.of("UNREAD")));
            }
            case "markUnread" -> {
                String messageId = getRequiredParam(params, "messageId");
                String url = GMAIL_API_BASE + "/messages/" + messageId + "/modify";
                yield executePost(url, accessToken, Map.of("addLabelIds", List.of("UNREAD")));
            }
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
    }

    // ==================== Draft Operations ====================

    private NodeExecutionResult executeDraftOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                int maxResults = getIntParam(params, "maxResults", 100);
                String url = GMAIL_API_BASE + "/drafts?maxResults=" + maxResults;
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String draftId = getRequiredParam(params, "draftId");
                String format = getParam(params, "format", "full");
                String url = GMAIL_API_BASE + "/drafts/" + draftId + "?format=" + format;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String to = getRequiredParam(params, "to");
                String cc = getParam(params, "cc", "");
                String bcc = getParam(params, "bcc", "");
                String subject = getRequiredParam(params, "subject");
                String body = getRequiredParam(params, "body");
                String htmlBody = getParam(params, "htmlBody", "");

                String rawMessage = buildRawMessage(to, cc, bcc, subject, body, htmlBody, "", null, null);

                Map<String, Object> requestBody = Map.of("message", Map.of("raw", rawMessage));

                String url = GMAIL_API_BASE + "/drafts";
                yield executePost(url, accessToken, requestBody);
            }
            case "update" -> {
                String draftId = getRequiredParam(params, "draftId");
                String to = getParam(params, "to", "");
                String cc = getParam(params, "cc", "");
                String bcc = getParam(params, "bcc", "");
                String subject = getParam(params, "subject", "");
                String body = getParam(params, "body", "");
                String htmlBody = getParam(params, "htmlBody", "");

                if (to.isEmpty() && subject.isEmpty() && body.isEmpty()) {
                    yield NodeExecutionResult.failure("At least one field must be provided to update");
                }

                String rawMessage = buildRawMessage(
                    to.isEmpty() ? "placeholder@example.com" : to,
                    cc, bcc,
                    subject.isEmpty() ? "(No Subject)" : subject,
                    body.isEmpty() ? " " : body,
                    htmlBody, "", null, null
                );

                Map<String, Object> requestBody = Map.of("message", Map.of("raw", rawMessage));

                String url = GMAIL_API_BASE + "/drafts/" + draftId;
                yield executePut(url, accessToken, requestBody);
            }
            case "send" -> {
                String draftId = getRequiredParam(params, "draftId");
                String url = GMAIL_API_BASE + "/drafts/send";
                yield executePost(url, accessToken, Map.of("id", draftId));
            }
            case "delete" -> {
                String draftId = getRequiredParam(params, "draftId");
                String url = GMAIL_API_BASE + "/drafts/" + draftId;
                yield executeDelete(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown draft operation: " + operation);
        };
    }

    // ==================== Label Operations ====================

    private NodeExecutionResult executeLabelOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String url = GMAIL_API_BASE + "/labels";
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String labelId = getRequiredParam(params, "labelId");
                String url = GMAIL_API_BASE + "/labels/" + labelId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String name = getRequiredParam(params, "name");
                String labelListVisibility = getParam(params, "labelListVisibility", "labelShow");
                String messageListVisibility = getParam(params, "messageListVisibility", "show");

                Map<String, Object> requestBody = Map.of(
                    "name", name,
                    "labelListVisibility", labelListVisibility,
                    "messageListVisibility", messageListVisibility
                );

                String url = GMAIL_API_BASE + "/labels";
                yield executePost(url, accessToken, requestBody);
            }
            case "update" -> {
                String labelId = getRequiredParam(params, "labelId");

                Map<String, Object> requestBody = new LinkedHashMap<>();
                String name = getParam(params, "name", "");
                String labelListVisibility = getParam(params, "labelListVisibility", "");
                String messageListVisibility = getParam(params, "messageListVisibility", "");

                if (!name.isEmpty()) {
                    requestBody.put("name", name);
                }
                if (!labelListVisibility.isEmpty()) {
                    requestBody.put("labelListVisibility", labelListVisibility);
                }
                if (!messageListVisibility.isEmpty()) {
                    requestBody.put("messageListVisibility", messageListVisibility);
                }

                String url = GMAIL_API_BASE + "/labels/" + labelId;
                yield executePut(url, accessToken, requestBody);
            }
            case "delete" -> {
                String labelId = getRequiredParam(params, "labelId");
                String url = GMAIL_API_BASE + "/labels/" + labelId;
                yield executeDelete(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown label operation: " + operation);
        };
    }

    // ==================== Thread Operations ====================

    private NodeExecutionResult executeThreadOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                String query = getParam(params, "query", "");
                String labelIds = getParam(params, "labelIds", "");
                int maxResults = getIntParam(params, "maxResults", 100);

                StringBuilder urlBuilder = new StringBuilder(GMAIL_API_BASE + "/threads");
                urlBuilder.append("?maxResults=").append(maxResults);

                if (!query.isEmpty()) {
                    urlBuilder.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
                }
                if (!labelIds.isEmpty()) {
                    for (String labelId : labelIds.split(",")) {
                        urlBuilder.append("&labelIds=").append(labelId.trim());
                    }
                }

                yield executeGet(urlBuilder.toString(), accessToken);
            }
            case "get" -> {
                String threadId = getRequiredParam(params, "threadId");
                String format = getParam(params, "format", "full");
                String url = GMAIL_API_BASE + "/threads/" + threadId + "?format=" + format;
                yield executeGet(url, accessToken);
            }
            case "trash" -> {
                String threadId = getRequiredParam(params, "threadId");
                String url = GMAIL_API_BASE + "/threads/" + threadId + "/trash";
                yield executePost(url, accessToken, Map.of());
            }
            case "delete" -> {
                String threadId = getRequiredParam(params, "threadId");
                String url = GMAIL_API_BASE + "/threads/" + threadId;
                yield executeDelete(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown thread operation: " + operation);
        };
    }

    // ==================== Helper Methods ====================

    private String buildRawMessage(
        String to, String cc, String bcc,
        String subject, String body, String htmlBody,
        String replyTo, String inReplyTo, String references
    ) {
        StringBuilder message = new StringBuilder();
        message.append("To: ").append(to).append("\r\n");

        if (!cc.isEmpty()) {
            message.append("Cc: ").append(cc).append("\r\n");
        }
        if (!bcc.isEmpty()) {
            message.append("Bcc: ").append(bcc).append("\r\n");
        }
        if (!replyTo.isEmpty()) {
            message.append("Reply-To: ").append(replyTo).append("\r\n");
        }
        if (inReplyTo != null && !inReplyTo.isEmpty()) {
            message.append("In-Reply-To: ").append(inReplyTo).append("\r\n");
        }
        if (references != null && !references.isEmpty()) {
            message.append("References: ").append(references).append("\r\n");
        }

        message.append("Subject: ").append(subject).append("\r\n");

        if (!htmlBody.isEmpty()) {
            String boundary = "====Part_" + System.currentTimeMillis() + "====";
            message.append("MIME-Version: 1.0\r\n");
            message.append("Content-Type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\n");
            message.append("\r\n");
            message.append("--").append(boundary).append("\r\n");
            message.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
            message.append("\r\n");
            message.append(body).append("\r\n");
            message.append("--").append(boundary).append("\r\n");
            message.append("Content-Type: text/html; charset=\"UTF-8\"\r\n");
            message.append("\r\n");
            message.append(htmlBody).append("\r\n");
            message.append("--").append(boundary).append("--\r\n");
        } else {
            message.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
            message.append("\r\n");
            message.append(body);
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(message.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ==================== HTTP Helpers ====================

    private NodeExecutionResult executeGet(String url, String accessToken) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .get()
            .build();
        return executeRequest(request);
    }

    private NodeExecutionResult executePost(String url, String accessToken, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .post(requestBody)
            .build();
        return executeRequest(request);
    }

    private NodeExecutionResult executePut(String url, String accessToken, Map<String, Object> body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .put(requestBody)
            .build();
        return executeRequest(request);
    }

    private NodeExecutionResult executeDelete(String url, String accessToken) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }
            return NodeExecutionResult.success(Map.of("success", true));
        }
    }

    private NodeExecutionResult executeRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";

            if (!response.isSuccessful()) {
                try {
                    Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});
                    if (result.containsKey("error")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> error = (Map<String, Object>) result.get("error");
                        String message = (String) error.getOrDefault("message", "Unknown error");
                        return NodeExecutionResult.failure("Gmail API error: " + message);
                    }
                } catch (Exception e) {
                    // Ignore parse error
                }
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});
            return NodeExecutionResult.success(result);
        }
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(Map.of("name", "input", "type", "any", "required", false)),
            "outputs", List.of(Map.of("name", "output", "type", "object"))
        );
    }
}
