package com.aiinpocket.n3n.execution.handler.handlers.integrations;

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

import java.util.*;

/**
 * Threads API node handler.
 *
 * Supports Threads (by Meta) operations via Threads API v1.0+
 *
 * Features:
 * - Profile information
 * - Create and manage threads (posts)
 * - Reply to threads
 * - Get thread insights
 * - Media publishing (text, images, videos, carousels)
 *
 * Credential schema:
 * - accessToken: Threads User Access Token
 * - threadsUserId: Threads User ID
 *
 * Required permissions:
 * - threads_basic
 * - threads_content_publish
 * - threads_manage_insights
 * - threads_manage_replies
 * - threads_read_replies
 *
 * Note: Threads API is separate from Instagram/Facebook Graph API
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ThreadsNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String THREADS_API_BASE = "https://graph.threads.net/v1.0";

    @Override
    public String getType() {
        return "threads";
    }

    @Override
    public String getDisplayName() {
        return "Threads";
    }

    @Override
    public String getDescription() {
        return "Threads API. Create posts, reply to threads, and get insights.";
    }

    @Override
    public String getCategory() {
        return "Social Media";
    }

    @Override
    public String getIcon() {
        return "threads";
    }

    @Override
    public String getCredentialType() {
        return "threads";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("profile", ResourceDef.of("profile", "Profile", "User profile information"));
        resources.put("thread", ResourceDef.of("thread", "Thread", "Create and manage threads"));
        resources.put("reply", ResourceDef.of("reply", "Reply", "Manage replies"));
        resources.put("insights", ResourceDef.of("insights", "Insights", "Thread and profile insights"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Profile operations
        operations.put("profile", List.of(
            OperationDef.create("getInfo", "Get Profile Info")
                .description("Get Threads profile information")
                .fields(List.of(
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields to retrieve")
                        .withDefault("id,username,name,threads_profile_picture_url,threads_biography")
                ))
                .outputDescription("Returns profile information")
                .build(),

            OperationDef.create("getThreads", "Get User Threads")
                .description("Get threads posted by the user")
                .fields(List.of(
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100),
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields for each thread")
                        .withDefault("id,text,media_type,media_url,permalink,timestamp,shortcode,is_quote_post"),
                    FieldDef.string("since", "Since")
                        .withDescription("Start date (Unix timestamp)"),
                    FieldDef.string("until", "Until")
                        .withDescription("End date (Unix timestamp)")
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build()
        ));

        // Thread operations
        operations.put("thread", List.of(
            OperationDef.create("createText", "Create Text Thread")
                .description("Create a text-only thread")
                .fields(List.of(
                    FieldDef.textarea("text", "Text")
                        .withDescription("Thread text content (max 500 characters)")
                        .required(),
                    FieldDef.string("replyControl", "Reply Control")
                        .withDescription("Who can reply")
                        .withOptions(List.of("everyone", "accounts_you_follow", "mentioned_only"))
                        .withDefault("everyone")
                ))
                .outputDescription("Returns { id: \"thread_id\" }")
                .build(),

            OperationDef.create("createImage", "Create Image Thread")
                .description("Create a thread with an image")
                .fields(List.of(
                    FieldDef.string("imageUrl", "Image URL")
                        .withDescription("Public URL of the image (JPEG/PNG, max 8MB)")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Thread text content"),
                    FieldDef.string("replyControl", "Reply Control")
                        .withDescription("Who can reply")
                        .withOptions(List.of("everyone", "accounts_you_follow", "mentioned_only"))
                        .withDefault("everyone")
                ))
                .outputDescription("Returns { id: \"thread_id\" }")
                .build(),

            OperationDef.create("createVideo", "Create Video Thread")
                .description("Create a thread with a video")
                .fields(List.of(
                    FieldDef.string("videoUrl", "Video URL")
                        .withDescription("Public URL of the video (MP4, max 5 min, max 1GB)")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Thread text content"),
                    FieldDef.string("replyControl", "Reply Control")
                        .withDescription("Who can reply")
                        .withOptions(List.of("everyone", "accounts_you_follow", "mentioned_only"))
                        .withDefault("everyone")
                ))
                .outputDescription("Returns { id: \"thread_id\" }")
                .build(),

            OperationDef.create("createCarousel", "Create Carousel Thread")
                .description("Create a thread with multiple images/videos")
                .fields(List.of(
                    FieldDef.textarea("items", "Items")
                        .withDescription("JSON array: [{\"type\":\"IMAGE\",\"url\":\"...\"}] or [{\"type\":\"VIDEO\",\"url\":\"...\"}]")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Thread text content"),
                    FieldDef.string("replyControl", "Reply Control")
                        .withDescription("Who can reply")
                        .withOptions(List.of("everyone", "accounts_you_follow", "mentioned_only"))
                        .withDefault("everyone")
                ))
                .outputDescription("Returns { id: \"thread_id\" }")
                .build(),

            OperationDef.create("get", "Get Thread")
                .description("Get a specific thread by ID")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .withDescription("The thread ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,text,media_type,media_url,permalink,timestamp,username,is_quote_post")
                ))
                .outputDescription("Returns thread data")
                .build(),

            OperationDef.create("quote", "Quote Thread")
                .description("Create a quote post of another thread")
                .fields(List.of(
                    FieldDef.string("quotedThreadId", "Quoted Thread ID")
                        .withDescription("ID of the thread to quote")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Your commentary on the quoted thread"),
                    FieldDef.string("replyControl", "Reply Control")
                        .withDescription("Who can reply")
                        .withOptions(List.of("everyone", "accounts_you_follow", "mentioned_only"))
                        .withDefault("everyone")
                ))
                .outputDescription("Returns { id: \"thread_id\" }")
                .build()
        ));

        // Reply operations
        operations.put("reply", List.of(
            OperationDef.create("create", "Create Reply")
                .description("Reply to a thread")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .withDescription("ID of the thread to reply to")
                        .required(),
                    FieldDef.textarea("text", "Text")
                        .withDescription("Reply text content")
                        .required(),
                    FieldDef.string("imageUrl", "Image URL")
                        .withDescription("Optional image to attach"),
                    FieldDef.string("videoUrl", "Video URL")
                        .withDescription("Optional video to attach")
                ))
                .outputDescription("Returns { id: \"reply_id\" }")
                .build(),

            OperationDef.create("getReplies", "Get Replies")
                .description("Get replies to a thread")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .withDescription("The thread ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,text,media_type,media_url,permalink,timestamp,username"),
                    FieldDef.bool("reverse", "Reverse")
                        .withDescription("Oldest first")
                        .withDefault(false)
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getConversation", "Get Conversation")
                .description("Get the conversation thread (all replies)")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .withDescription("The thread ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,text,media_type,media_url,permalink,timestamp,username")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("hide", "Hide Reply")
                .description("Hide a reply from your thread")
                .fields(List.of(
                    FieldDef.string("replyId", "Reply ID")
                        .withDescription("The reply ID to hide")
                        .required(),
                    FieldDef.bool("hide", "Hide")
                        .withDescription("Whether to hide or unhide")
                        .withDefault(true)
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Insights operations
        operations.put("insights", List.of(
            OperationDef.create("getThreadInsights", "Get Thread Insights")
                .description("Get insights for a specific thread")
                .fields(List.of(
                    FieldDef.string("threadId", "Thread ID")
                        .withDescription("The thread ID")
                        .required(),
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("views,likes,replies,reposts,quotes")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getProfileInsights", "Get Profile Insights")
                .description("Get profile-level insights")
                .fields(List.of(
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("views,likes,replies,reposts,quotes,followers_count"),
                    FieldDef.string("since", "Since")
                        .withDescription("Start date (Unix timestamp)"),
                    FieldDef.string("until", "Until")
                        .withDescription("End date (Unix timestamp)")
                ))
                .outputDescription("Returns { data: [...] }")
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
            String threadsUserId = getCredentialValue(credential, "threadsUserId");

            if (accessToken == null || accessToken.isEmpty()) {
                return NodeExecutionResult.failure("Access token is required");
            }
            if (threadsUserId == null || threadsUserId.isEmpty()) {
                return NodeExecutionResult.failure("Threads User ID is required");
            }

            return switch (resource) {
                case "profile" -> executeProfileOperation(accessToken, threadsUserId, operation, params);
                case "thread" -> executeThreadOperation(accessToken, threadsUserId, operation, params);
                case "reply" -> executeReplyOperation(accessToken, threadsUserId, operation, params);
                case "insights" -> executeInsightsOperation(accessToken, threadsUserId, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Threads API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Threads API error: " + e.getMessage());
        }
    }

    // ==================== Profile Operations ====================

    private NodeExecutionResult executeProfileOperation(
        String accessToken, String threadsUserId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getInfo" -> {
                String fields = getParam(params, "fields", "id,username,name,threads_profile_picture_url,threads_biography");
                String url = THREADS_API_BASE + "/" + threadsUserId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getThreads" -> {
                int limit = getIntParam(params, "limit", 25);
                String fields = getParam(params, "fields", "id,text,media_type,media_url,permalink,timestamp");
                String since = getParam(params, "since", "");
                String until = getParam(params, "until", "");

                StringBuilder url = new StringBuilder(THREADS_API_BASE + "/" + threadsUserId + "/threads");
                url.append("?limit=").append(limit);
                url.append("&fields=").append(fields);
                url.append("&access_token=").append(accessToken);

                if (!since.isEmpty()) url.append("&since=").append(since);
                if (!until.isEmpty()) url.append("&until=").append(until);

                yield executeGet(url.toString());
            }
            default -> NodeExecutionResult.failure("Unknown profile operation: " + operation);
        };
    }

    // ==================== Thread Operations ====================

    private NodeExecutionResult executeThreadOperation(
        String accessToken, String threadsUserId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "createText" -> {
                String text = getRequiredParam(params, "text");
                String replyControl = getParam(params, "replyControl", "everyone");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("media_type", "TEXT");
                containerData.put("text", text);
                containerData.put("reply_control", replyControl);
                containerData.put("access_token", accessToken);

                yield createAndPublishThread(threadsUserId, containerData, accessToken);
            }
            case "createImage" -> {
                String imageUrl = getRequiredParam(params, "imageUrl");
                String text = getParam(params, "text", "");
                String replyControl = getParam(params, "replyControl", "everyone");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("media_type", "IMAGE");
                containerData.put("image_url", imageUrl);
                containerData.put("reply_control", replyControl);
                containerData.put("access_token", accessToken);
                if (!text.isEmpty()) containerData.put("text", text);

                yield createAndPublishThread(threadsUserId, containerData, accessToken);
            }
            case "createVideo" -> {
                String videoUrl = getRequiredParam(params, "videoUrl");
                String text = getParam(params, "text", "");
                String replyControl = getParam(params, "replyControl", "everyone");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("media_type", "VIDEO");
                containerData.put("video_url", videoUrl);
                containerData.put("reply_control", replyControl);
                containerData.put("access_token", accessToken);
                if (!text.isEmpty()) containerData.put("text", text);

                yield createAndPublishVideoThread(threadsUserId, containerData, accessToken);
            }
            case "createCarousel" -> {
                String itemsJson = getRequiredParam(params, "items");
                String text = getParam(params, "text", "");
                String replyControl = getParam(params, "replyControl", "everyone");

                List<Map<String, Object>> items = objectMapper.readValue(itemsJson, new TypeReference<>() {});

                // Create child containers
                List<String> childIds = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    String type = (String) item.get("type");
                    String url = (String) item.get("url");

                    Map<String, String> childData = new LinkedHashMap<>();
                    childData.put("is_carousel_item", "true");
                    childData.put("access_token", accessToken);

                    if ("IMAGE".equals(type)) {
                        childData.put("media_type", "IMAGE");
                        childData.put("image_url", url);
                    } else if ("VIDEO".equals(type)) {
                        childData.put("media_type", "VIDEO");
                        childData.put("video_url", url);
                    }

                    String containerUrl = THREADS_API_BASE + "/" + threadsUserId + "/threads";
                    NodeExecutionResult childResult = executePost(containerUrl, childData);

                    if (!childResult.isSuccess()) yield childResult;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> childResponse = (Map<String, Object>) childResult.getOutput();
                    childIds.add((String) childResponse.get("id"));
                }

                // Create carousel container
                Map<String, String> carouselData = new LinkedHashMap<>();
                carouselData.put("media_type", "CAROUSEL");
                carouselData.put("children", String.join(",", childIds));
                carouselData.put("reply_control", replyControl);
                carouselData.put("access_token", accessToken);
                if (!text.isEmpty()) carouselData.put("text", text);

                yield createAndPublishThread(threadsUserId, carouselData, accessToken);
            }
            case "get" -> {
                String threadId = getRequiredParam(params, "threadId");
                String fields = getParam(params, "fields", "id,text,media_type,media_url,permalink,timestamp,username");
                String url = THREADS_API_BASE + "/" + threadId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "quote" -> {
                String quotedThreadId = getRequiredParam(params, "quotedThreadId");
                String text = getParam(params, "text", "");
                String replyControl = getParam(params, "replyControl", "everyone");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("media_type", "TEXT");
                containerData.put("quote_post_id", quotedThreadId);
                containerData.put("reply_control", replyControl);
                containerData.put("access_token", accessToken);
                if (!text.isEmpty()) containerData.put("text", text);

                yield createAndPublishThread(threadsUserId, containerData, accessToken);
            }
            default -> NodeExecutionResult.failure("Unknown thread operation: " + operation);
        };
    }

    private NodeExecutionResult createAndPublishThread(
        String threadsUserId, Map<String, String> containerData, String accessToken
    ) throws Exception {
        // Step 1: Create container
        String containerUrl = THREADS_API_BASE + "/" + threadsUserId + "/threads";
        NodeExecutionResult containerResult = executePost(containerUrl, containerData);

        if (!containerResult.isSuccess()) return containerResult;

        @SuppressWarnings("unchecked")
        Map<String, Object> containerResponse = (Map<String, Object>) containerResult.getOutput();
        String containerId = (String) containerResponse.get("id");

        // Step 2: Publish
        return publishThread(threadsUserId, containerId, accessToken);
    }

    private NodeExecutionResult createAndPublishVideoThread(
        String threadsUserId, Map<String, String> containerData, String accessToken
    ) throws Exception {
        // Step 1: Create container
        String containerUrl = THREADS_API_BASE + "/" + threadsUserId + "/threads";
        NodeExecutionResult containerResult = executePost(containerUrl, containerData);

        if (!containerResult.isSuccess()) return containerResult;

        @SuppressWarnings("unchecked")
        Map<String, Object> containerResponse = (Map<String, Object>) containerResult.getOutput();
        String containerId = (String) containerResponse.get("id");

        // Step 2: Wait for processing and publish
        return waitAndPublishThread(threadsUserId, containerId, accessToken);
    }

    private NodeExecutionResult publishThread(String threadsUserId, String containerId, String accessToken) throws Exception {
        Map<String, String> publishData = new LinkedHashMap<>();
        publishData.put("creation_id", containerId);
        publishData.put("access_token", accessToken);

        String publishUrl = THREADS_API_BASE + "/" + threadsUserId + "/threads_publish";
        return executePost(publishUrl, publishData);
    }

    private NodeExecutionResult waitAndPublishThread(String threadsUserId, String containerId, String accessToken) throws Exception {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            String statusUrl = THREADS_API_BASE + "/" + containerId + "?fields=status&access_token=" + accessToken;
            NodeExecutionResult statusResult = executeGet(statusUrl);

            if (statusResult.isSuccess()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) statusResult.getOutput();
                String statusCode = (String) status.get("status");

                if ("FINISHED".equals(statusCode)) {
                    return publishThread(threadsUserId, containerId, accessToken);
                } else if ("ERROR".equals(statusCode)) {
                    return NodeExecutionResult.failure("Video processing failed");
                }
            }

            Thread.sleep(2000);
            attempt++;
        }

        return NodeExecutionResult.failure("Video processing timeout");
    }

    // ==================== Reply Operations ====================

    private NodeExecutionResult executeReplyOperation(
        String accessToken, String threadsUserId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> {
                String threadId = getRequiredParam(params, "threadId");
                String text = getRequiredParam(params, "text");
                String imageUrl = getParam(params, "imageUrl", "");
                String videoUrl = getParam(params, "videoUrl", "");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("reply_to_id", threadId);
                containerData.put("access_token", accessToken);

                if (!videoUrl.isEmpty()) {
                    containerData.put("media_type", "VIDEO");
                    containerData.put("video_url", videoUrl);
                    if (!text.isEmpty()) containerData.put("text", text);
                    yield createAndPublishVideoThread(threadsUserId, containerData, accessToken);
                } else if (!imageUrl.isEmpty()) {
                    containerData.put("media_type", "IMAGE");
                    containerData.put("image_url", imageUrl);
                    if (!text.isEmpty()) containerData.put("text", text);
                    yield createAndPublishThread(threadsUserId, containerData, accessToken);
                } else {
                    containerData.put("media_type", "TEXT");
                    containerData.put("text", text);
                    yield createAndPublishThread(threadsUserId, containerData, accessToken);
                }
            }
            case "getReplies" -> {
                String threadId = getRequiredParam(params, "threadId");
                String fields = getParam(params, "fields", "id,text,media_type,media_url,permalink,timestamp,username");
                boolean reverse = getBoolParam(params, "reverse", false);

                String url = THREADS_API_BASE + "/" + threadId + "/replies?fields=" + fields +
                    "&reverse=" + reverse + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getConversation" -> {
                String threadId = getRequiredParam(params, "threadId");
                String fields = getParam(params, "fields", "id,text,media_type,media_url,permalink,timestamp,username");

                String url = THREADS_API_BASE + "/" + threadId + "/conversation?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "hide" -> {
                String replyId = getRequiredParam(params, "replyId");
                boolean hide = getBoolParam(params, "hide", true);

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("hide", String.valueOf(hide));
                formData.put("access_token", accessToken);

                String url = THREADS_API_BASE + "/" + replyId + "/manage_reply";
                yield executePost(url, formData);
            }
            default -> NodeExecutionResult.failure("Unknown reply operation: " + operation);
        };
    }

    // ==================== Insights Operations ====================

    private NodeExecutionResult executeInsightsOperation(
        String accessToken, String threadsUserId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getThreadInsights" -> {
                String threadId = getRequiredParam(params, "threadId");
                String metrics = getParam(params, "metrics", "views,likes,replies,reposts,quotes");

                String url = THREADS_API_BASE + "/" + threadId + "/insights?metric=" + metrics + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getProfileInsights" -> {
                String metrics = getParam(params, "metrics", "views,likes,replies,reposts,quotes,followers_count");
                String since = getParam(params, "since", "");
                String until = getParam(params, "until", "");

                StringBuilder url = new StringBuilder(THREADS_API_BASE + "/" + threadsUserId + "/threads_insights");
                url.append("?metric=").append(metrics);
                url.append("&access_token=").append(accessToken);

                if (!since.isEmpty()) url.append("&since=").append(since);
                if (!until.isEmpty()) url.append("&until=").append(until);

                yield executeGet(url.toString());
            }
            default -> NodeExecutionResult.failure("Unknown insights operation: " + operation);
        };
    }

    // ==================== HTTP Helpers ====================

    private NodeExecutionResult executeGet(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        return executeRequest(request);
    }

    private NodeExecutionResult executePost(String url, Map<String, String> formData) throws Exception {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build();

        return executeRequest(request);
    }

    private NodeExecutionResult executeRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});

            if (!response.isSuccessful()) {
                if (result.containsKey("error")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) result.get("error");
                    String message = (String) error.getOrDefault("message", "Unknown error");
                    return NodeExecutionResult.failure("Threads API error: " + message);
                }
                return NodeExecutionResult.failure("HTTP " + response.code() + ": " + body);
            }

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
