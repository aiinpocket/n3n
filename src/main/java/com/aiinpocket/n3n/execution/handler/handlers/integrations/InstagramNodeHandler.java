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
 * Instagram Graph API node handler.
 *
 * Supports Instagram Business/Creator account operations via Graph API v18.0+
 *
 * Features:
 * - Account information
 * - Media management (get, publish)
 * - Comment management
 * - Insights and analytics
 * - Hashtag search
 * - Story mentions
 *
 * Credential schema:
 * - accessToken: Instagram/Facebook Page Access Token
 * - instagramAccountId: Instagram Business Account ID
 *
 * Required permissions:
 * - instagram_basic
 * - instagram_content_publish
 * - instagram_manage_comments
 * - instagram_manage_insights
 *
 * Note: Requires a Facebook Page connected to an Instagram Business/Creator account
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InstagramNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";

    @Override
    public String getType() {
        return "instagram";
    }

    @Override
    public String getDisplayName() {
        return "Instagram";
    }

    @Override
    public String getDescription() {
        return "Instagram Graph API. Manage posts, comments, stories, and insights for Business/Creator accounts.";
    }

    @Override
    public String getCategory() {
        return "Social Media";
    }

    @Override
    public String getIcon() {
        return "instagram";
    }

    @Override
    public String getCredentialType() {
        return "instagram";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("account", ResourceDef.of("account", "Account", "Account information"));
        resources.put("media", ResourceDef.of("media", "Media", "Photos, videos, carousels, reels"));
        resources.put("comment", ResourceDef.of("comment", "Comment", "Comment management"));
        resources.put("insights", ResourceDef.of("insights", "Insights", "Analytics and metrics"));
        resources.put("hashtag", ResourceDef.of("hashtag", "Hashtag", "Hashtag search"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Account operations
        operations.put("account", List.of(
            OperationDef.create("getInfo", "Get Account Info")
                .description("Get Instagram account information")
                .fields(List.of(
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields to retrieve")
                        .withDefault("id,username,name,biography,followers_count,follows_count,media_count,profile_picture_url,website")
                ))
                .outputDescription("Returns account information")
                .build(),

            OperationDef.create("getMedia", "Get Account Media")
                .description("Get media from the account")
                .fields(List.of(
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100),
                    FieldDef.string("fields", "Fields")
                        .withDescription("Comma-separated fields for each media")
                        .withDefault("id,caption,media_type,media_url,permalink,thumbnail_url,timestamp,like_count,comments_count")
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build(),

            OperationDef.create("getStories", "Get Stories")
                .description("Get current stories from the account")
                .fields(List.of(
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,media_type,media_url,timestamp")
                ))
                .outputDescription("Returns { data: [...] }")
                .build()
        ));

        // Media operations
        operations.put("media", List.of(
            OperationDef.create("get", "Get Media")
                .description("Get a specific media item by ID")
                .fields(List.of(
                    FieldDef.string("mediaId", "Media ID")
                        .withDescription("The media ID")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,caption,media_type,media_url,permalink,timestamp,like_count,comments_count")
                ))
                .outputDescription("Returns media data")
                .build(),

            OperationDef.create("createPhoto", "Create Photo Post")
                .description("Publish a photo to Instagram")
                .fields(List.of(
                    FieldDef.string("imageUrl", "Image URL")
                        .withDescription("Public URL of the image (JPEG only, max 8MB)")
                        .required(),
                    FieldDef.textarea("caption", "Caption")
                        .withDescription("Post caption (max 2200 characters, 30 hashtags)"),
                    FieldDef.string("locationId", "Location ID")
                        .withDescription("Facebook Location Page ID (optional)"),
                    FieldDef.textarea("userTags", "User Tags")
                        .withDescription("JSON array of user tags: [{\"username\":\"user\",\"x\":0.5,\"y\":0.5}]")
                ))
                .outputDescription("Returns { id: \"media_id\" }")
                .build(),

            OperationDef.create("createVideo", "Create Video/Reel")
                .description("Publish a video or reel to Instagram")
                .fields(List.of(
                    FieldDef.string("videoUrl", "Video URL")
                        .withDescription("Public URL of the video (MP4, max 100MB)")
                        .required(),
                    FieldDef.textarea("caption", "Caption")
                        .withDescription("Post caption"),
                    FieldDef.string("mediaType", "Media Type")
                        .withDescription("Type of video content")
                        .withOptions(List.of("REELS", "VIDEO"))
                        .withDefault("REELS"),
                    FieldDef.string("coverUrl", "Cover Image URL")
                        .withDescription("Custom cover image URL (optional)"),
                    FieldDef.string("thumbOffset", "Thumbnail Offset")
                        .withDescription("Thumbnail offset in milliseconds")
                        .withDefault("0"),
                    FieldDef.bool("shareToFeed", "Share to Feed")
                        .withDescription("Share reel to feed")
                        .withDefault(true)
                ))
                .outputDescription("Returns { id: \"media_id\" }")
                .build(),

            OperationDef.create("createCarousel", "Create Carousel")
                .description("Publish a carousel (multiple images/videos)")
                .fields(List.of(
                    FieldDef.textarea("items", "Items")
                        .withDescription("JSON array of items: [{\"type\":\"IMAGE\",\"url\":\"...\"}] or [{\"type\":\"VIDEO\",\"url\":\"...\"}]")
                        .required(),
                    FieldDef.textarea("caption", "Caption")
                        .withDescription("Post caption")
                ))
                .outputDescription("Returns { id: \"media_id\" }")
                .build(),

            OperationDef.create("getComments", "Get Media Comments")
                .description("Get comments on a media item")
                .fields(List.of(
                    FieldDef.string("mediaId", "Media ID")
                        .withDescription("The media ID")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDefault(25)
                        .withRange(1, 100)
                ))
                .outputDescription("Returns { data: [...], paging: {...} }")
                .build()
        ));

        // Comment operations
        operations.put("comment", List.of(
            OperationDef.create("create", "Create Comment")
                .description("Add a comment to a media item")
                .fields(List.of(
                    FieldDef.string("mediaId", "Media ID")
                        .withDescription("The media ID to comment on")
                        .required(),
                    FieldDef.textarea("message", "Message")
                        .withDescription("Comment text")
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
                        .withDescription("Reply text")
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
                    FieldDef.bool("hide", "Hide")
                        .withDescription("Whether to hide or show")
                        .withDefault(true)
                ))
                .outputDescription("Returns { success: true }")
                .build()
        ));

        // Insights operations
        operations.put("insights", List.of(
            OperationDef.create("getAccountInsights", "Get Account Insights")
                .description("Get account-level insights")
                .fields(List.of(
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("impressions,reach,profile_views,follower_count"),
                    FieldDef.string("period", "Period")
                        .withDescription("Time period")
                        .withOptions(List.of("day", "week", "days_28", "lifetime"))
                        .withDefault("day"),
                    FieldDef.string("since", "Since")
                        .withDescription("Start date (Unix timestamp)"),
                    FieldDef.string("until", "Until")
                        .withDescription("End date (Unix timestamp)")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getMediaInsights", "Get Media Insights")
                .description("Get insights for a specific media item")
                .fields(List.of(
                    FieldDef.string("mediaId", "Media ID")
                        .withDescription("The media ID")
                        .required(),
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics (varies by media type)")
                        .withDefault("impressions,reach,engagement,saved")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getAudienceInsights", "Get Audience Insights")
                .description("Get audience demographics")
                .fields(List.of(
                    FieldDef.string("metrics", "Metrics")
                        .withDescription("Comma-separated metrics")
                        .withDefault("audience_city,audience_country,audience_gender_age")
                ))
                .outputDescription("Returns { data: [...] }")
                .build()
        ));

        // Hashtag operations
        operations.put("hashtag", List.of(
            OperationDef.create("search", "Search Hashtag")
                .description("Search for a hashtag ID")
                .fields(List.of(
                    FieldDef.string("q", "Query")
                        .withDescription("Hashtag to search (without #)")
                        .required()
                ))
                .outputDescription("Returns { data: [{id, name}] }")
                .build(),

            OperationDef.create("getTopMedia", "Get Top Media")
                .description("Get top media for a hashtag")
                .fields(List.of(
                    FieldDef.string("hashtagId", "Hashtag ID")
                        .withDescription("The hashtag ID (from search)")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,caption,media_type,permalink,like_count,comments_count")
                ))
                .outputDescription("Returns { data: [...] }")
                .build(),

            OperationDef.create("getRecentMedia", "Get Recent Media")
                .description("Get recent media for a hashtag")
                .fields(List.of(
                    FieldDef.string("hashtagId", "Hashtag ID")
                        .withDescription("The hashtag ID (from search)")
                        .required(),
                    FieldDef.string("fields", "Fields")
                        .withDefault("id,caption,media_type,permalink,like_count,comments_count")
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
            String igAccountId = getCredentialValue(credential, "instagramAccountId");

            if (accessToken == null || accessToken.isEmpty()) {
                return NodeExecutionResult.failure("Access token is required");
            }
            if (igAccountId == null || igAccountId.isEmpty()) {
                return NodeExecutionResult.failure("Instagram Account ID is required");
            }

            return switch (resource) {
                case "account" -> executeAccountOperation(accessToken, igAccountId, operation, params);
                case "media" -> executeMediaOperation(accessToken, igAccountId, operation, params);
                case "comment" -> executeCommentOperation(accessToken, operation, params);
                case "insights" -> executeInsightsOperation(accessToken, igAccountId, operation, params);
                case "hashtag" -> executeHashtagOperation(accessToken, igAccountId, operation, params);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Instagram API error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Instagram API error: " + e.getMessage());
        }
    }

    // ==================== Account Operations ====================

    private NodeExecutionResult executeAccountOperation(
        String accessToken, String igAccountId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getInfo" -> {
                String fields = getParam(params, "fields", "id,username,name,biography,followers_count,follows_count,media_count");
                String url = GRAPH_API_BASE + "/" + igAccountId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getMedia" -> {
                int limit = getIntParam(params, "limit", 25);
                String fields = getParam(params, "fields", "id,caption,media_type,media_url,permalink,timestamp");
                String url = GRAPH_API_BASE + "/" + igAccountId + "/media?limit=" + limit + "&fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getStories" -> {
                String fields = getParam(params, "fields", "id,media_type,media_url,timestamp");
                String url = GRAPH_API_BASE + "/" + igAccountId + "/stories?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown account operation: " + operation);
        };
    }

    // ==================== Media Operations ====================

    private NodeExecutionResult executeMediaOperation(
        String accessToken, String igAccountId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "get" -> {
                String mediaId = getRequiredParam(params, "mediaId");
                String fields = getParam(params, "fields", "id,caption,media_type,media_url,permalink,timestamp");
                String url = GRAPH_API_BASE + "/" + mediaId + "?fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "createPhoto" -> {
                // Step 1: Create container
                String imageUrl = getRequiredParam(params, "imageUrl");
                String caption = getParam(params, "caption", "");
                String locationId = getParam(params, "locationId", "");
                String userTags = getParam(params, "userTags", "");

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("image_url", imageUrl);
                containerData.put("access_token", accessToken);
                if (!caption.isEmpty()) containerData.put("caption", caption);
                if (!locationId.isEmpty()) containerData.put("location_id", locationId);
                if (!userTags.isEmpty()) containerData.put("user_tags", userTags);

                String containerUrl = GRAPH_API_BASE + "/" + igAccountId + "/media";
                NodeExecutionResult containerResult = executePost(containerUrl, containerData);

                if (!containerResult.isSuccess()) yield containerResult;

                @SuppressWarnings("unchecked")
                Map<String, Object> containerResponse = (Map<String, Object>) containerResult.getOutput();
                String containerId = (String) containerResponse.get("id");

                // Step 2: Publish container
                yield publishMediaContainer(accessToken, igAccountId, containerId);
            }
            case "createVideo" -> {
                String videoUrl = getRequiredParam(params, "videoUrl");
                String caption = getParam(params, "caption", "");
                String mediaType = getParam(params, "mediaType", "REELS");
                String coverUrl = getParam(params, "coverUrl", "");
                String thumbOffset = getParam(params, "thumbOffset", "0");
                boolean shareToFeed = getBoolParam(params, "shareToFeed", true);

                Map<String, String> containerData = new LinkedHashMap<>();
                containerData.put("video_url", videoUrl);
                containerData.put("media_type", mediaType);
                containerData.put("access_token", accessToken);
                if (!caption.isEmpty()) containerData.put("caption", caption);
                if (!coverUrl.isEmpty()) containerData.put("cover_url", coverUrl);
                containerData.put("thumb_offset", thumbOffset);
                if (mediaType.equals("REELS")) {
                    containerData.put("share_to_feed", String.valueOf(shareToFeed));
                }

                String containerUrl = GRAPH_API_BASE + "/" + igAccountId + "/media";
                NodeExecutionResult containerResult = executePost(containerUrl, containerData);

                if (!containerResult.isSuccess()) yield containerResult;

                @SuppressWarnings("unchecked")
                Map<String, Object> containerResponse = (Map<String, Object>) containerResult.getOutput();
                String containerId = (String) containerResponse.get("id");

                // Wait for video processing then publish
                yield waitAndPublishVideo(accessToken, igAccountId, containerId);
            }
            case "createCarousel" -> {
                String itemsJson = getRequiredParam(params, "items");
                String caption = getParam(params, "caption", "");

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
                        childData.put("image_url", url);
                    } else if ("VIDEO".equals(type)) {
                        childData.put("video_url", url);
                        childData.put("media_type", "VIDEO");
                    }

                    String containerUrl = GRAPH_API_BASE + "/" + igAccountId + "/media";
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
                carouselData.put("access_token", accessToken);
                if (!caption.isEmpty()) carouselData.put("caption", caption);

                String carouselUrl = GRAPH_API_BASE + "/" + igAccountId + "/media";
                NodeExecutionResult carouselResult = executePost(carouselUrl, carouselData);

                if (!carouselResult.isSuccess()) yield carouselResult;

                @SuppressWarnings("unchecked")
                Map<String, Object> carouselResponse = (Map<String, Object>) carouselResult.getOutput();
                String carouselId = (String) carouselResponse.get("id");

                yield publishMediaContainer(accessToken, igAccountId, carouselId);
            }
            case "getComments" -> {
                String mediaId = getRequiredParam(params, "mediaId");
                int limit = getIntParam(params, "limit", 25);
                String url = GRAPH_API_BASE + "/" + mediaId + "/comments?limit=" + limit + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown media operation: " + operation);
        };
    }

    private NodeExecutionResult publishMediaContainer(String accessToken, String igAccountId, String containerId) throws Exception {
        Map<String, String> publishData = new LinkedHashMap<>();
        publishData.put("creation_id", containerId);
        publishData.put("access_token", accessToken);

        String publishUrl = GRAPH_API_BASE + "/" + igAccountId + "/media_publish";
        return executePost(publishUrl, publishData);
    }

    private NodeExecutionResult waitAndPublishVideo(String accessToken, String igAccountId, String containerId) throws Exception {
        // Poll for video status (simplified - in production, use webhooks)
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            String statusUrl = GRAPH_API_BASE + "/" + containerId + "?fields=status_code&access_token=" + accessToken;
            NodeExecutionResult statusResult = executeGet(statusUrl);

            if (statusResult.isSuccess()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) statusResult.getOutput();
                String statusCode = (String) status.get("status_code");

                if ("FINISHED".equals(statusCode)) {
                    return publishMediaContainer(accessToken, igAccountId, containerId);
                } else if ("ERROR".equals(statusCode)) {
                    return NodeExecutionResult.failure("Video processing failed");
                }
            }

            Thread.sleep(2000); // Wait 2 seconds before retry
            attempt++;
        }

        return NodeExecutionResult.failure("Video processing timeout");
    }

    // ==================== Comment Operations ====================

    private NodeExecutionResult executeCommentOperation(
        String accessToken, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "create" -> {
                String mediaId = getRequiredParam(params, "mediaId");
                String message = getRequiredParam(params, "message");

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("message", message);
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + mediaId + "/comments";
                yield executePost(url, formData);
            }
            case "reply" -> {
                String commentId = getRequiredParam(params, "commentId");
                String message = getRequiredParam(params, "message");

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("message", message);
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + commentId + "/replies";
                yield executePost(url, formData);
            }
            case "delete" -> {
                String commentId = getRequiredParam(params, "commentId");
                String url = GRAPH_API_BASE + "/" + commentId + "?access_token=" + accessToken;
                yield executeDelete(url);
            }
            case "hide" -> {
                String commentId = getRequiredParam(params, "commentId");
                boolean hide = getBoolParam(params, "hide", true);

                Map<String, String> formData = new LinkedHashMap<>();
                formData.put("hide", String.valueOf(hide));
                formData.put("access_token", accessToken);

                String url = GRAPH_API_BASE + "/" + commentId;
                yield executePost(url, formData);
            }
            default -> NodeExecutionResult.failure("Unknown comment operation: " + operation);
        };
    }

    // ==================== Insights Operations ====================

    private NodeExecutionResult executeInsightsOperation(
        String accessToken, String igAccountId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "getAccountInsights" -> {
                String metrics = getParam(params, "metrics", "impressions,reach,profile_views");
                String period = getParam(params, "period", "day");
                String since = getParam(params, "since", "");
                String until = getParam(params, "until", "");

                StringBuilder url = new StringBuilder(GRAPH_API_BASE + "/" + igAccountId + "/insights");
                url.append("?metric=").append(metrics);
                url.append("&period=").append(period);
                url.append("&access_token=").append(accessToken);

                if (!since.isEmpty()) url.append("&since=").append(since);
                if (!until.isEmpty()) url.append("&until=").append(until);

                yield executeGet(url.toString());
            }
            case "getMediaInsights" -> {
                String mediaId = getRequiredParam(params, "mediaId");
                String metrics = getParam(params, "metrics", "impressions,reach,engagement,saved");
                String url = GRAPH_API_BASE + "/" + mediaId + "/insights?metric=" + metrics + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getAudienceInsights" -> {
                String metrics = getParam(params, "metrics", "audience_city,audience_country,audience_gender_age");
                String url = GRAPH_API_BASE + "/" + igAccountId + "/insights?metric=" + metrics + "&period=lifetime&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown insights operation: " + operation);
        };
    }

    // ==================== Hashtag Operations ====================

    private NodeExecutionResult executeHashtagOperation(
        String accessToken, String igAccountId, String operation, Map<String, Object> params
    ) throws Exception {
        return switch (operation) {
            case "search" -> {
                String query = getRequiredParam(params, "q");
                String url = GRAPH_API_BASE + "/ig_hashtag_search?user_id=" + igAccountId + "&q=" + query + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getTopMedia" -> {
                String hashtagId = getRequiredParam(params, "hashtagId");
                String fields = getParam(params, "fields", "id,caption,media_type,permalink");
                String url = GRAPH_API_BASE + "/" + hashtagId + "/top_media?user_id=" + igAccountId + "&fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            case "getRecentMedia" -> {
                String hashtagId = getRequiredParam(params, "hashtagId");
                String fields = getParam(params, "fields", "id,caption,media_type,permalink");
                String url = GRAPH_API_BASE + "/" + hashtagId + "/recent_media?user_id=" + igAccountId + "&fields=" + fields + "&access_token=" + accessToken;
                yield executeGet(url);
            }
            default -> NodeExecutionResult.failure("Unknown hashtag operation: " + operation);
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
                if (result.containsKey("error")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) result.get("error");
                    String message = (String) error.getOrDefault("message", "Unknown error");
                    return NodeExecutionResult.failure("Instagram API error: " + message);
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
