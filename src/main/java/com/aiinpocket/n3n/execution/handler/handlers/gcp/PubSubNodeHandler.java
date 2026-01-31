package com.aiinpocket.n3n.execution.handler.handlers.gcp;

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

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Google Cloud Pub/Sub API node handler.
 *
 * Supports topic and subscription operations via Pub/Sub API v1.
 *
 * Features:
 * - Topic management (create, delete, list)
 * - Subscription management (create, delete, list)
 * - Message publishing
 * - Message pulling (synchronous)
 *
 * Credential schema:
 * - serviceAccountJson: Service Account JSON key
 * - projectId: GCP Project ID (optional)
 *
 * Required IAM roles:
 * - roles/pubsub.admin (full access)
 * - roles/pubsub.publisher (publish only)
 * - roles/pubsub.subscriber (subscribe only)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PubSubNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String PUBSUB_API_BASE = "https://pubsub.googleapis.com/v1";

    @Override
    public String getType() {
        return "googlePubSub";
    }

    @Override
    public String getDisplayName() {
        return "Google Pub/Sub";
    }

    @Override
    public String getDescription() {
        return "Google Cloud Pub/Sub. Publish and subscribe to messages.";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "googlePubSub";
    }

    @Override
    public String getCredentialType() {
        return "googlePubSub";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("topic", ResourceDef.of("topic", "Topic", "Topic management"));
        resources.put("subscription", ResourceDef.of("subscription", "Subscription", "Subscription management"));
        resources.put("message", ResourceDef.of("message", "Message", "Message operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Topic operations
        operations.put("topic", List.of(
            OperationDef.create("list", "List Topics")
                .description("List all topics in the project")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID")
                        .withDescription("GCP Project ID"),
                    FieldDef.integer("pageSize", "Page Size")
                        .withDefault(100)
                        .withRange(1, 1000)
                ))
                .outputDescription("Returns { topics: [...] }")
                .build(),

            OperationDef.create("get", "Get Topic")
                .description("Get topic details")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .withDescription("Topic name (without project prefix)")
                        .required()
                ))
                .outputDescription("Returns topic metadata")
                .build(),

            OperationDef.create("create", "Create Topic")
                .description("Create a new topic")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .withDescription("Topic name (without project prefix)")
                        .required(),
                    FieldDef.textarea("labels", "Labels")
                        .withDescription("JSON object of labels")
                ))
                .outputDescription("Returns created topic")
                .build(),

            OperationDef.create("delete", "Delete Topic")
                .description("Delete a topic")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("listSubscriptions", "List Topic Subscriptions")
                .description("List subscriptions for a topic")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .required(),
                    FieldDef.integer("pageSize", "Page Size")
                        .withDefault(100)
                ))
                .outputDescription("Returns { subscriptions: [...] }")
                .build()
        ));

        // Subscription operations
        operations.put("subscription", List.of(
            OperationDef.create("list", "List Subscriptions")
                .description("List all subscriptions in the project")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.integer("pageSize", "Page Size")
                        .withDefault(100)
                ))
                .outputDescription("Returns { subscriptions: [...] }")
                .build(),

            OperationDef.create("get", "Get Subscription")
                .description("Get subscription details")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required()
                ))
                .outputDescription("Returns subscription metadata")
                .build(),

            OperationDef.create("create", "Create Subscription")
                .description("Create a new subscription")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .withDescription("Subscription name")
                        .required(),
                    FieldDef.string("topicId", "Topic ID")
                        .withDescription("Topic to subscribe to")
                        .required(),
                    FieldDef.integer("ackDeadlineSeconds", "Ack Deadline")
                        .withDescription("Ack deadline in seconds")
                        .withDefault(10)
                        .withRange(10, 600),
                    FieldDef.integer("messageRetentionDuration", "Retention Duration")
                        .withDescription("Message retention in seconds (604800 = 7 days)")
                        .withDefault(604800),
                    FieldDef.bool("enableExactlyOnceDelivery", "Exactly Once")
                        .withDescription("Enable exactly-once delivery")
                        .withDefault(false),
                    FieldDef.string("pushEndpoint", "Push Endpoint")
                        .withDescription("HTTPS endpoint for push delivery"),
                    FieldDef.textarea("filter", "Filter")
                        .withDescription("Message filter expression")
                ))
                .outputDescription("Returns created subscription")
                .build(),

            OperationDef.create("delete", "Delete Subscription")
                .description("Delete a subscription")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("modifyAckDeadline", "Modify Ack Deadline")
                .description("Modify acknowledgment deadline for messages")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required(),
                    FieldDef.textarea("ackIds", "Ack IDs")
                        .withDescription("Comma-separated ack IDs")
                        .required(),
                    FieldDef.integer("ackDeadlineSeconds", "New Deadline")
                        .withDescription("New deadline in seconds")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("seek", "Seek Subscription")
                .description("Seek to a specific point in time or snapshot")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required(),
                    FieldDef.string("time", "Time")
                        .withDescription("RFC 3339 timestamp to seek to"),
                    FieldDef.string("snapshot", "Snapshot")
                        .withDescription("Snapshot name to seek to")
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Message operations
        operations.put("message", List.of(
            OperationDef.create("publish", "Publish Message")
                .description("Publish a message to a topic")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("Message data (will be base64 encoded)")
                        .required(),
                    FieldDef.textarea("attributes", "Attributes")
                        .withDescription("JSON object of message attributes"),
                    FieldDef.string("orderingKey", "Ordering Key")
                        .withDescription("Key for message ordering")
                ))
                .outputDescription("Returns { messageId }")
                .build(),

            OperationDef.create("publishBatch", "Publish Batch")
                .description("Publish multiple messages at once")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("topicId", "Topic ID")
                        .required(),
                    FieldDef.textarea("messages", "Messages")
                        .withDescription("JSON array: [{\"data\":\"...\",\"attributes\":{}}]")
                        .required()
                ))
                .outputDescription("Returns { messageIds: [...] }")
                .build(),

            OperationDef.create("pull", "Pull Messages")
                .description("Pull messages from a subscription")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required(),
                    FieldDef.integer("maxMessages", "Max Messages")
                        .withDescription("Maximum messages to pull")
                        .withDefault(10)
                        .withRange(1, 1000),
                    FieldDef.bool("returnImmediately", "Return Immediately")
                        .withDescription("Don't wait for messages if none available")
                        .withDefault(false)
                ))
                .outputDescription("Returns { receivedMessages: [...] }")
                .build(),

            OperationDef.create("acknowledge", "Acknowledge Messages")
                .description("Acknowledge received messages")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required(),
                    FieldDef.textarea("ackIds", "Ack IDs")
                        .withDescription("Comma-separated ack IDs from received messages")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("modifyPushConfig", "Modify Push Config")
                .description("Modify subscription push configuration")
                .fields(List.of(
                    FieldDef.string("projectId", "Project ID"),
                    FieldDef.string("subscriptionId", "Subscription ID")
                        .required(),
                    FieldDef.string("pushEndpoint", "Push Endpoint")
                        .withDescription("New push endpoint (empty for pull)")
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
            String serviceAccountJson = getCredentialValue(credential, "serviceAccountJson");

            if (serviceAccountJson == null || serviceAccountJson.isEmpty()) {
                return NodeExecutionResult.failure("Service account JSON is required");
            }

            String accessToken = getServiceAccountToken(serviceAccountJson);
            String projectId = getProjectId(credential, serviceAccountJson, params);

            return switch (resource) {
                case "topic" -> executeTopicOperation(accessToken, projectId, operation, params);
                case "subscription" -> executeSubscriptionOperation(accessToken, projectId, operation, params);
                case "message" -> executeMessageOperation(accessToken, projectId, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Pub/Sub API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Pub/Sub API error: " + e.getMessage());
        }
    }

    private String getProjectId(Map<String, Object> credential, String serviceAccountJson, Map<String, Object> params) throws Exception {
        String projectId = getParam(params, "projectId", "");
        if (!projectId.isEmpty()) {
            return projectId;
        }

        projectId = getCredentialValue(credential, "projectId");
        if (projectId != null && !projectId.isEmpty()) {
            return projectId;
        }

        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        return (String) sa.get("project_id");
    }

    private String getServiceAccountToken(String serviceAccountJson) throws Exception {
        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        String clientEmail = (String) sa.get("client_email");
        String privateKey = (String) sa.get("private_key");
        String tokenUri = (String) sa.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
            "iss", clientEmail,
            "scope", "https://www.googleapis.com/auth/pubsub",
            "aud", tokenUri,
            "iat", now,
            "exp", now + 3600
        );

        String jwt = createJwt(header, claims, privateKey);

        RequestBody body = new FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build();

        Request request = new Request.Builder()
            .url(tokenUri)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
            return (String) tokenResponse.get("access_token");
        }
    }

    private String createJwt(Map<String, Object> header, Map<String, Object> claims, String privateKeyPem) throws Exception {
        String headerJson = objectMapper.writeValueAsString(header);
        String claimsJson = objectMapper.writeValueAsString(claims);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String claimsBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));

        String signatureInput = headerBase64 + "." + claimsBase64;

        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyContent);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
        java.security.PrivateKey pk = keyFactory.generatePrivate(keySpec);

        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(pk);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return signatureInput + "." + signatureBase64;
    }

    // ==================== Topic Operations ====================

    private NodeExecutionResult executeTopicOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                int pageSize = getIntParam(params, "pageSize", 100);
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics?pageSize=" + pageSize;
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String topicId = getRequiredParam(params, "topicId");
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String topicId = getRequiredParam(params, "topicId");
                String labelsJson = getParam(params, "labels", "");

                Map<String, Object> body = new LinkedHashMap<>();
                if (!labelsJson.isEmpty()) {
                    Map<String, String> labels = objectMapper.readValue(labelsJson, new TypeReference<>() {});
                    body.put("labels", labels);
                }

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId;
                yield executePut(url, accessToken, body);
            }
            case "delete" -> {
                String topicId = getRequiredParam(params, "topicId");
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId;
                yield executeDelete(url, accessToken);
            }
            case "listSubscriptions" -> {
                String topicId = getRequiredParam(params, "topicId");
                int pageSize = getIntParam(params, "pageSize", 100);
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId
                    + "/subscriptions?pageSize=" + pageSize;
                yield executeGet(url, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown topic operation: " + operation);
        };
    }

    // ==================== Subscription Operations ====================

    private NodeExecutionResult executeSubscriptionOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "list" -> {
                int pageSize = getIntParam(params, "pageSize", 100);
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions?pageSize=" + pageSize;
                yield executeGet(url, accessToken);
            }
            case "get" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId;
                yield executeGet(url, accessToken);
            }
            case "create" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String topicId = getRequiredParam(params, "topicId");
                int ackDeadline = getIntParam(params, "ackDeadlineSeconds", 10);
                int retention = getIntParam(params, "messageRetentionDuration", 604800);
                boolean exactlyOnce = getBoolParam(params, "enableExactlyOnceDelivery", false);
                String pushEndpoint = getParam(params, "pushEndpoint", "");
                String filter = getParam(params, "filter", "");

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("topic", "projects/" + projectId + "/topics/" + topicId);
                body.put("ackDeadlineSeconds", ackDeadline);
                body.put("messageRetentionDuration", retention + "s");
                body.put("enableExactlyOnceDelivery", exactlyOnce);

                if (!pushEndpoint.isEmpty()) {
                    body.put("pushConfig", Map.of("pushEndpoint", pushEndpoint));
                }
                if (!filter.isEmpty()) {
                    body.put("filter", filter);
                }

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId;
                yield executePut(url, accessToken, body);
            }
            case "delete" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId;
                yield executeDelete(url, accessToken);
            }
            case "modifyAckDeadline" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String ackIdsStr = getRequiredParam(params, "ackIds");
                int deadline = getIntParam(params, "ackDeadlineSeconds", 10);

                List<String> ackIds = Arrays.asList(ackIdsStr.split(","));

                Map<String, Object> body = Map.of(
                    "ackIds", ackIds,
                    "ackDeadlineSeconds", deadline
                );

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId
                    + ":modifyAckDeadline";
                yield executePost(url, accessToken, body);
            }
            case "seek" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String time = getParam(params, "time", "");
                String snapshot = getParam(params, "snapshot", "");

                Map<String, Object> body = new LinkedHashMap<>();
                if (!time.isEmpty()) {
                    body.put("time", time);
                } else if (!snapshot.isEmpty()) {
                    body.put("snapshot", "projects/" + projectId + "/snapshots/" + snapshot);
                } else {
                    yield NodeExecutionResult.failure("Either time or snapshot is required");
                }

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId
                    + ":seek";
                yield executePost(url, accessToken, body);
            }
            default -> NodeExecutionResult.failure("Unknown subscription operation: " + operation);
        };
    }

    // ==================== Message Operations ====================

    private NodeExecutionResult executeMessageOperation(
        String accessToken, String projectId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "publish" -> {
                String topicId = getRequiredParam(params, "topicId");
                String data = getRequiredParam(params, "data");
                String attributesJson = getParam(params, "attributes", "");
                String orderingKey = getParam(params, "orderingKey", "");

                String encodedData = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));

                Map<String, Object> message = new LinkedHashMap<>();
                message.put("data", encodedData);

                if (!attributesJson.isEmpty()) {
                    Map<String, String> attributes = objectMapper.readValue(attributesJson, new TypeReference<>() {});
                    message.put("attributes", attributes);
                }
                if (!orderingKey.isEmpty()) {
                    message.put("orderingKey", orderingKey);
                }

                Map<String, Object> body = Map.of("messages", List.of(message));

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId + ":publish";
                NodeExecutionResult result = executePost(url, accessToken, body);

                if (result.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    List<String> messageIds = (List<String>) result.getOutput().get("messageIds");
                    yield NodeExecutionResult.success(Map.of(
                        "messageId", messageIds != null && !messageIds.isEmpty() ? messageIds.get(0) : null,
                        "messageIds", messageIds
                    ));
                }
                yield result;
            }
            case "publishBatch" -> {
                String topicId = getRequiredParam(params, "topicId");
                String messagesJson = getRequiredParam(params, "messages");

                List<Map<String, Object>> inputMessages = objectMapper.readValue(messagesJson, new TypeReference<>() {});
                List<Map<String, Object>> pubsubMessages = new ArrayList<>();

                for (Map<String, Object> msg : inputMessages) {
                    String msgData = (String) msg.get("data");
                    String encodedData = Base64.getEncoder().encodeToString(msgData.getBytes(StandardCharsets.UTF_8));

                    Map<String, Object> pubsubMsg = new LinkedHashMap<>();
                    pubsubMsg.put("data", encodedData);

                    if (msg.containsKey("attributes")) {
                        pubsubMsg.put("attributes", msg.get("attributes"));
                    }
                    if (msg.containsKey("orderingKey")) {
                        pubsubMsg.put("orderingKey", msg.get("orderingKey"));
                    }

                    pubsubMessages.add(pubsubMsg);
                }

                Map<String, Object> body = Map.of("messages", pubsubMessages);

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/topics/" + topicId + ":publish";
                yield executePost(url, accessToken, body);
            }
            case "pull" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                int maxMessages = getIntParam(params, "maxMessages", 10);
                boolean returnImmediately = getBoolParam(params, "returnImmediately", false);

                Map<String, Object> body = Map.of(
                    "maxMessages", maxMessages,
                    "returnImmediately", returnImmediately
                );

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId
                    + ":pull";

                NodeExecutionResult result = executePost(url, accessToken, body);

                // Decode message data
                if (result.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> receivedMessages = (List<Map<String, Object>>) result.getOutput().get("receivedMessages");
                    if (receivedMessages != null) {
                        for (Map<String, Object> rm : receivedMessages) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> message = (Map<String, Object>) rm.get("message");
                            if (message != null && message.containsKey("data")) {
                                String encodedData = (String) message.get("data");
                                String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
                                message.put("decodedData", decodedData);
                            }
                        }
                    }
                    yield NodeExecutionResult.success(result.getOutput());
                }
                yield result;
            }
            case "acknowledge" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String ackIdsStr = getRequiredParam(params, "ackIds");

                List<String> ackIds = Arrays.stream(ackIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

                Map<String, Object> body = Map.of("ackIds", ackIds);

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId
                    + ":acknowledge";

                NodeExecutionResult result = executePost(url, accessToken, body);
                if (result.isSuccess()) {
                    yield NodeExecutionResult.success(Map.of("success", true, "acknowledgedCount", ackIds.size()));
                }
                yield result;
            }
            case "modifyPushConfig" -> {
                String subscriptionId = getRequiredParam(params, "subscriptionId");
                String pushEndpoint = getParam(params, "pushEndpoint", "");

                Map<String, Object> pushConfig = pushEndpoint.isEmpty()
                    ? Map.of()
                    : Map.of("pushEndpoint", pushEndpoint);

                Map<String, Object> body = Map.of("pushConfig", pushConfig);

                String url = PUBSUB_API_BASE + "/projects/" + projectId + "/subscriptions/" + subscriptionId
                    + ":modifyPushConfig";
                yield executePost(url, accessToken, body);
            }
            default -> NodeExecutionResult.failure("Unknown message operation: " + operation);
        };
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
                        return NodeExecutionResult.failure("Pub/Sub API error: " + message);
                    }
                } catch (Exception e) {
                    // Ignore parse error
                }
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }

            // Handle empty response (e.g., acknowledge)
            if (body.isEmpty() || body.equals("{}")) {
                return NodeExecutionResult.success(Map.of("success", true));
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
