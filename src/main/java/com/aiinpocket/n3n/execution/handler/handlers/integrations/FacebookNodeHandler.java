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
 * Facebook Graph API node handler.
 *
 * Supports Facebook Page operations via Graph API v18.0+
 *
 * Features:
 * - Page management (get info, get posts)
 * - Post operations (create, get, delete)
 * - Comment operations (get, create, delete)
 * - Photo/Video upload
 * - Page insights
 *
 * Credential schema:
 * - accessToken: Page Access Token (long-lived recommended)
 * - pageId: Facebook Page ID
 * - appId: Facebook App ID (optional, for app-level operations)
 * - appSecret: Facebook App Secret (optional)
 *
 * Required permissions:
 * - pages_read_engagement
 * - pages_manage_posts
 * - pages_read_user_content
 * - pages_manage_engagement (for comments)
 * - read_insights (for analytics)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FacebookNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";

    @Override
    public String getType() {
        return "facebook";
    }

    @Override
    public String getDisplayName() {
        return "Facebook";
    }

    @Override
    public String getDescription() {
        return "Facebook Graph API. Manage pages, posts, comments, and insights.";
    }

    @Override
    public String getCategory() {
        return "Social Media";
    }

    @Override
    public String getIcon() {
        return "facebook";
    }

    @Override
    public String getCredentialType() {
        return "facebook";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("page", ResourceDef.of("page", "Page", "Page information and management"));
        resources.put("post", ResourceDef.of("post", "Post", "Create and manage posts"));
        resources.put("comment", ResourceDef.of("comment", "Comment", "Manage comments"));
        resources.put("media", ResourceDef.of("media", "Media", "Upload photos and videos"));
        resources.put("insights", ResourceDef.of("insights", "Insights", "Page analytics and insights"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Page operations
        operations.put("page", List.of(
            OperationDef.create("getInfo", "Get Page Info")
                .description("Get information about the page")
                .fields(List.of(
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields to retrieve")
                        .withDefault("id,name,about,fan_count,followers_count,link,picture")
                ))
                .outputDescription("Returns page information")
                .build(),

            OperationDef.create("getFeed", "Get Page Feed")
                .description("Get posts from the page feed")
                .fields(List.of(
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100),
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields for each post")
                        .withDefault("id,message,created_time,permalink_url,shares,likes.summary(true),comments.summary(true)")
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build()
        ));

        // Post operations
        operations.put("post", List.of(
            OperationDef.create("create", "Create Post")
                .description("Create a new post on the page")
                .fields(List.of(
                    FieldDef.textarea("message", "Message")
                        .withDescription("Post content")
                        .required(),
                    FieldDef.string("link", "Link")
                        .withDescription("URL to attach to the post"),
                    FieldDef.bool("published", "Published")
                        .withDescription("Whether to publish immediately")
                        .withDefault(true),
                    FieldDef.string("scheduledPublishTime", "Scheduled Time")
                        .withDescription("Unix timestamp for scheduled post (if not published)")
                ))
                .outputDescription("Returns { id: \"post_id\" }")
                .build(),

            OperationDef.create("get", "Get Post")
                .description("Get a specific post by ID")
                .fields(List.of(
                    FieldDef.string("postId", "Post ID")
                        .withDescription("The post ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,message,created_time,permalink_url,shares,likes.summary(true),comments.summary(true)")
                ))
                .outputDescription("Returns post data")
                .build(),

            OperationDef.create("delete", "Delete Post")
                .description("Delete a post")
                .fields(List.of(
                    FieldDef.string("postId", "Post ID")
                        .withDescription("The post ID to delete")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("getComments", "Get Post Comments")
                .description("Get comments on a post")
                .fields(List.of(
                    FieldDef.string("postId", "Post ID")
                        .withDescription("The post ID")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100),
                    FieldDef.string("order", "Order")
                        .withDescription("Comment order")
                        .withOptions(List.of("chronological", "reverse_chronological"))
                        .withDefault("reverse_chronological")
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build()
        ));

        // Comment operations
        operations.put("comment", List.of(
            OperationDef.create("create", "Create Comment")
                .description("Add a comment to a post")
                .fields(List.of(
                    FieldDef.string("postId", "Post ID")
                        .withDescription("The post ID to comment on")
                        .required(),
                    FieldDef.textarea("message", "Message")
                        .withDescription("Comment content")
                        .required()
                ))
                .outputDescription("Returns { id: \"comment_id\" }")
                .build(),

            OperationDef.create("reply", "Reply to Comment")
                .description("Reply to an existing comment")
                .fields(List.of(
                    FieldDef.string("commentId", "Comment ID")
                        .withDescription("The comment ID to reply to")
                        .required(),
                    FieldDef.textarea("message", "Message")
                        .withDescription("Reply content")
                        .required()
                ))
                .outputDescription("Returns { id: \"comment_id\" }")
                .build(),

            OperationDef.create("delete", "Delete Comment")
                .description("Delete a comment")
                .fields(List.of(
                    FieldDef.string("commentId", "Comment ID")
                        .withDescription("The comment ID to delete")
                        .required()
                ))
                .outputDescription("Returns { success: true }")
                .build(),

            OperationDef.create("hide", "Hide Comment")
                .description("Hide a comment from public view")
                .fields(List.of(
                    FieldDef.string("commentId", "Comment ID")
                        .withDescription("The comment ID to hide")
                        .required(),
                    FieldDef.bool("isHidden", "Hidden")
                        .withDescription("Whether to hide or unhide")
                        .withDefault(true)
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Media operations
        operations.put("media", List.of(
            OperationDef.create("uploadPhoto", "Upload Photo")
                .description("Upload a photo to the page")
                .fields(List.of(
                    FieldDef.string("url", "Photo URL")
                        .withDescription("URL of the photo to upload")
                        .required(),
                    FieldDef.textarea("caption", "Caption")
                        .withDescription("Photo caption"),
                    FieldDef.bool("published", "Published")
                        .withDescription("Whether to publish immediately")
                        .withDefault(true)
                ))
                .outputDescription("Returns { id: \"photo_id\", post_id: \"...\" }")
                .build(),

            OperationDef.create("getPhotos", "Get Photos")
                .description("Get photos from the page")
                .fields(List.of(
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100)
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build(),

            OperationDef.create("getVideos", "Get Videos")
                .description("Get videos from the page")
                .fields(List.of(
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100)
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build()
        ));

        // Insights operations
        operations.put("insights", List.of(
            OperationDef.create("getPageInsights", "Get Page Insights")
                .description("Get page-level insights and metrics")
                .fields(List.of(
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("page_impressions,page_engaged_users,page_fans,page_views_total"),
                    FieldDef.string("period", "Period")
                        .withDescription("Aggregation period")
                        .withOptions(List.of("day", "week", "days_28"))
                        .withDefault("day"),
                    FieldDef.string("since", "Since")
                        .withDescription("Start date (YYYY-MM-DD or Unix timestamp)"),
                    FieldDef.string("until", "Until")
                        .withDescription("End date (YYYY-MM-DD or Unix timestamp)")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getPostInsights", "Get Post Insights")
                .description("Get insights for a specific post")
                .fields(List.of(
                    FieldDef.string("postId", "Post ID")
                        .withDescription("The post ID")
                        .required(),
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("post_impressions,post_engaged_users,post_reactions_by_type_total")
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
            String pageId = getCredentialValue(credential, "pageId");

            if (accessToken == null || accessToken.isEmpty()) {
                return NodeExecutionResult.failure("Access token is required");
            }
            if (pageId == null || pageId.isEmpty()) {
                return NodeExecutionResult.failure("Page ID is required");
            }

            return switch (resource) {
                case "page" -> executePageOperation(accessToken, pageId, operation, params);
                case "post" -> executePostOperation(accessToken, pageId, operation, params);
                case "comment" -> executeCommentOperation(accessToken, operation, params);
                case "media" -> executeMediaOperation(accessToken, pageId, operation, params);
                case "insights" -> executeInsightsOperation(accessToken, pageId, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Facebook API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Facebook API error: " + e.getMessage());
        }
    }

    // ==================== Page Operations ====================

    private NodeExecutionResult executePageOperation(
        String accessToken, String pageId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getInfo" -> {
                String fields = getParam(params, "fields", "id,name,about,fan_count,followers_count,link,picture");
                String url = GRAPH_API_BASE + "/" + pageId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getFeed" -> {
                int limit = getIntParam(params, "limit", 25);
                String fields = getParam(params, "fields", "id,message,created_time,permalink_url");
                String url = GRAPH_API_BASE + "/" + pageId + "/feed?limit=" + limit + "&fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown page operation: " + operation);
        };
    }

    // ==================== Post Operations ====================

    private NodeExecutionResult executePostOperation(
        String accessToken, String pageId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> {
                String message = getRequiredParam(params, "message");
                String link = getParam(params, "link", "");
                boolean published = getBoolParam(params, "published", true);
                String scheduledTime = getParam(params, "scheduledPublishTime", "");

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("message", message);
                formData.put("access_token", accessToken);

                if (!link.isEmpty()) {
                    formData.put("link", link);
                }
                if (!published) {
                    formData.put("published", "false");
                    if (!scheduledTime.isEmpty()) {
                        formData.put("scheduled_publish_time", scheduledTime);
                    }
                }

                String url = GRAPH_API_BASE + "/" + pageId + "/feed";
                yield executePost(url, formData);
            }
            case "get" -> {
                String postId = getRequiredParam(params, "postId");
                String fields = getParam(params, "fields", "id,message,created_time,permalink_url");
                String url = GRAPH_API_BASE + "/" + postId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "delete" -> {
                String postId = getRequiredParam(params, "postId");
                String url = GRAPH_API_BASE + "/" + postId + "?access_token=" + accessToken;
                yield executeDelete(url);
            }
            case "getComments" -> {
                String postId = getRequiredParam(params, "postId");
                int limit = getIntParam(params, "limit", 25);
                String order = getParam(params, "order", "reverse_chronological");
                String url = GRAPH_API_BASE + "/" + postId + "/comments?limit=" + limit + "&order=" + order + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown post operation: " + operation);
        };
    }

    // ==================== Comment Operations ====================

    private NodeExecutionResult executeCommentOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> {
                String postId = getRequiredParam(params, "postId");
                String message = getRequiredParam(params, "message");

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("message", message);
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + postId + "/comments";
                yield executePost(url, formData);
            }
            case "reply" -> {
                String commentId = getRequiredParam(params, "commentId");
                String message = getRequiredParam(params, "message");

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("message", message);
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + commentId + "/comments";
                yield executePost(url, formData);
            }
            case "delete" -> {
                String commentId = getRequiredParam(params, "commentId");
                String url = GRAPH_API_BASE + "/" + commentId + "?access_token=" + accessToken;
                yield executeDelete(url);
            }
            case "hide" -> {
                String commentId = getRequiredParam(params, "commentId");
                boolean isHidden = getBoolParam(params, "isHidden", true);

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("is_hidden", String.valueOf(isHidden));
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + commentId;
                yield executePost(url, formData);
            }
            default -> NodeExecutionResult.failure("Unknown comment operation: " + operation);
        };
    }

    // ==================== Media Operations ====================

    private NodeExecutionResult executeMediaOperation(
        String accessToken, String pageId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "uploadPhoto" -> {
                String photoUrl = getRequiredParam(params, "url");
                String caption = getParam(params, "caption", "");
                boolean published = getBoolParam(params, "published", true);

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("url", photoUrl);
                formData.put("access_token", accessToken);
                formData.put("published", String.valueOf(published));

                if (!caption.isEmpty()) {
                    formData.put("caption", caption);
                }

                String url = GRAPH_API_BASE + "/" + pageId + "/photos";
                yield executePost(url, formData);
            }
            case "getPhotos" -> {
                int limit = getIntParam(params, "limit", 25);
                String url = GRAPH_API_BASE + "/" + pageId + "/photos?limit=" + limit + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getVideos" -> {
                int limit = getIntParam(params, "limit", 25);
                String url = GRAPH_API_BASE + "/" + pageId + "/videos?limit=" + limit + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown media operation: " + operation);
        };
    }

    // ==================== Insights Operations ====================

    private NodeExecutionResult executeInsightsOperation(
        String accessToken, String pageId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getPageInsights" -> {
                String metrics = getParam(params, "metrics", "page_impressions,page_engaged_users,page_fans");
                String period = getParam(params, "period", "day");
                String since = getParam(params, "since", "");
                String until = getParam(params, "until", "");

                StringBuilder url = new StringBuilder(GRAPH_API_BASE + "/" + pageId + "/insights");
                url.append("?metric=").append(metrics);
                url.append("&period=").append(period);
                url.append("&access_token=").append(accessToken);

                if (!since.isEmpty()) {
                    url.append("&since=").append(since);
                }
                if (!until.isEmpty()) {
                    url.append("&until=").append(until);
                }

                yield executeGet(url.toString());
            }
            case "getPostInsights" -> {
                String postId = getRequiredParam(params, "postId");
                String metrics = getParam(params, "metrics", "post_impressions,post_engaged_users");

                String url = GRAPH_API_BASE + "/" + postId + "/insights?metric=" + metrics + "&access_token=" + accessToken;
                yield executeGet(url);
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

    private NodeExecutionResult executeDelete(String url) throws Exception {
        Request request = new Request.Builder().url(url).delete().build();
        return executeRequest(request);
    }

    private NodeExecutionResult executeRequest(Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";

            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<>() {});

            if (!response.isSuccessful()) {
                // Extract error message from Facebook error format
                if (result.containsKey("error")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) result.get("error");
                    String message = (String) error.getOrDefault("message", "Unknown error");
                    return NodeExecutionResult.failure("Facebook API error: " + message);
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
