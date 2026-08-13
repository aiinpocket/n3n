package com.aiinpocket.n3n.execution.handler.handlers.ai.media;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.ai.service.AiProviderService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * fal.ai 多模態生成節點：AI 圖片生成（FLUX 系列）與 AI 影片生成（Veo / Kling / Hailuo / WAN）。
 *
 * 影片生成使用 fal 佇列 API（提交 → 輪詢 → 取結果），支援純文字劇情生成影片，
 * 以及「參考圖片 + 劇情描述」的圖生影片模式。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FalAiNodeHandler extends MultiOperationNodeHandler {

    private static final String SYNC_BASE = "https://fal.run/";
    private static final String QUEUE_BASE = "https://queue.fal.run/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long POLL_INTERVAL_MS = 5000;

    private final ObjectMapper objectMapper;
    private final ArtifactService artifactService;
    private final AiProviderService aiProviderService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String getType() {
        return "falAi";
    }

    @Override
    public String getDisplayName() {
        return "AI Image / Video (fal.ai)";
    }

    @Override
    public String getDescription() {
        return "Generate images (FLUX) and videos (Veo, Kling, Hailuo) with fal.ai. "
                + "Supports text-to-video from a storyline and image-to-video with reference images.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "video-camera";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public String getCredentialType() {
        return "fal";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("image", ResourceDef.of("image", "Image", "AI image generation"));
        resources.put("video", ResourceDef.of("video", "Video", "AI video generation"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("image", List.of(
            OperationDef.create("generate", "Generate Image")
                .description("Generate images from a text prompt")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "fal-ai/flux/schnell",
                            "fal-ai/flux/dev",
                            "fal-ai/flux-pro/v1.1-ultra",
                            "fal-ai/recraft/v3/text-to-image"))
                        .withDefault("fal-ai/flux/schnell")
                        .withDescription("Image generation model")
                        .required(),
                    FieldDef.textarea("prompt", "Prompt")
                        .withPlaceholder("A cinematic photo of...")
                        .withDescription("Text description of the image to generate")
                        .required(),
                    FieldDef.select("imageSize", "Image Size", List.of(
                            "square_hd", "square", "portrait_4_3", "portrait_16_9",
                            "landscape_4_3", "landscape_16_9"))
                        .withDefault("landscape_16_9")
                        .withDescription("Aspect ratio / size preset"),
                    FieldDef.integer("numImages", "Number of Images")
                        .withDefault(1)
                        .withRange(1, 4)
                ))
                .outputDescription("Returns 'images' (list of URLs) and 'raw' response")
                .build()
        ));

        operations.put("video", List.of(
            OperationDef.create("textToVideo", "Text to Video")
                .description("Generate a video from a storyline / script prompt")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "fal-ai/veo3/fast",
                            "fal-ai/kling-video/v2.1/standard/text-to-video",
                            "fal-ai/minimax/hailuo-02/standard/text-to-video",
                            "fal-ai/wan/v2.2-a14b/text-to-video"))
                        .withDefault("fal-ai/kling-video/v2.1/standard/text-to-video")
                        .withDescription("Video generation model")
                        .required(),
                    FieldDef.textarea("prompt", "Storyline / Prompt")
                        .withPlaceholder("Describe the scene, story and camera movement...")
                        .withDescription("Storyline description used to generate the video")
                        .required(),
                    FieldDef.select("aspectRatio", "Aspect Ratio", List.of("16:9", "9:16", "1:1"))
                        .withDefault("16:9"),
                    FieldDef.select("duration", "Duration (seconds)", List.of("5", "8", "10"))
                        .withDefault("5")
                        .withDescription("Video duration; supported values depend on the model"),
                    FieldDef.integer("timeoutSeconds", "Timeout (seconds)")
                        .withDefault(600)
                        .withRange(60, 1800)
                        .withDescription("Max time to wait for generation to finish")
                ))
                .outputDescription("Returns 'videoUrl' and 'raw' response")
                .build(),
            OperationDef.create("imageToVideo", "Image to Video")
                .description("Animate a reference image into a video guided by a prompt")
                .fields(List.of(
                    FieldDef.select("model", "Model", List.of(
                            "fal-ai/kling-video/v2.1/standard/image-to-video",
                            "fal-ai/veo3/fast/image-to-video",
                            "fal-ai/minimax/hailuo-02/standard/image-to-video",
                            "fal-ai/wan/v2.2-a14b/image-to-video"))
                        .withDefault("fal-ai/kling-video/v2.1/standard/image-to-video")
                        .withDescription("Image-to-video model")
                        .required(),
                    FieldDef.string("imageUrl", "Reference Image URL")
                        .withFormat("uri")
                        .withPlaceholder("https://... or output from a previous node")
                        .withDescription("Reference image to animate")
                        .required(),
                    FieldDef.textarea("prompt", "Storyline / Prompt")
                        .withPlaceholder("Describe how the image should move / what happens...")
                        .withDescription("Storyline guiding the animation")
                        .required(),
                    FieldDef.select("duration", "Duration (seconds)", List.of("5", "8", "10"))
                        .withDefault("5"),
                    FieldDef.integer("timeoutSeconds", "Timeout (seconds)")
                        .withDefault(600)
                        .withRange(60, 1800)
                ))
                .outputDescription("Returns 'videoUrl' and 'raw' response")
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
            Map<String, Object> params) {

        // 金鑰解析順序：節點憑證 → 平台共用 AI 設定（管理員在 AI 設定輸入的 fal.ai 金鑰）→ 環境變數
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = aiProviderService.resolveSharedApiKey("fal").orElse(null);
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("FAL_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return NodeExecutionResult.failure(
                "fal.ai API key is missing. Configure it in AI Settings (platform-wide) or attach a fal credential.");
        }

        try {
            if ("image".equals(resource) && "generate".equals(operation)) {
                return generateImage(context, apiKey, params);
            }
            if ("video".equals(resource)) {
                return generateVideo(context, apiKey, operation, params);
            }
            return NodeExecutionResult.failure("Unknown operation: " + resource + "." + operation);
        } catch (Exception e) {
            log.error("fal.ai operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("fal.ai generation failed: " + safeMessage(e));
        }
    }

    // ==================== Image (sync endpoint) ====================

    private NodeExecutionResult generateImage(NodeExecutionContext context, String apiKey, Map<String, Object> params) throws IOException {
        String model = getRequiredParam(params, "model");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", getRequiredParam(params, "prompt"));
        body.put("image_size", getParam(params, "imageSize", "landscape_16_9"));
        body.put("num_images", getIntParam(params, "numImages", 1));

        Request request = new Request.Builder()
                .url(SYNC_BASE + model)
                .header("Authorization", "Key " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("fal.ai image generation failed with status {}: {}", response.code(), responseBody);
                return NodeExecutionResult.failure("fal.ai image generation failed (HTTP " + response.code() + ")");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            List<String> images = new ArrayList<>();
            for (JsonNode img : root.path("images")) {
                String url = img.path("url").asText("");
                if (!url.isBlank()) {
                    images.add(url);
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("images", images);
            output.put("imageUrl", images.isEmpty() ? null : images.get(0));

            // 將遠端圖片（URL 會過期）存入使用者 artifact 庫；失敗時仍回傳遠端 URL
            List<String> artifactIds = new ArrayList<>();
            for (String url : images) {
                Artifact artifact = saveUrlAsArtifact(context, url, "image/png", "image", "png");
                if (artifact != null) {
                    artifactIds.add(artifact.getId().toString());
                }
            }
            if (!artifactIds.isEmpty()) {
                output.put("artifactIds", artifactIds);
                output.put("artifactId", artifactIds.get(0));
                output.put("downloadUrl", ArtifactService.downloadUrl(UUID.fromString(artifactIds.get(0))));
            }

            output.put("raw", objectMapper.convertValue(root, Map.class));
            return NodeExecutionResult.success(output);
        }
    }

    // ==================== Video (queue endpoint + polling) ====================

    private NodeExecutionResult generateVideo(NodeExecutionContext context, String apiKey, String operation, Map<String, Object> params)
            throws IOException, InterruptedException {
        String model = getRequiredParam(params, "model");
        int timeoutSeconds = getIntParam(params, "timeoutSeconds", 600);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", getRequiredParam(params, "prompt"));
        String duration = getParam(params, "duration", "5");
        body.put("duration", duration);

        if ("imageToVideo".equals(operation)) {
            body.put("image_url", getRequiredParam(params, "imageUrl"));
        } else {
            String aspectRatio = getParam(params, "aspectRatio", "16:9");
            body.put("aspect_ratio", aspectRatio);
        }

        // 1. 提交至佇列
        Request submitRequest = new Request.Builder()
                .url(QUEUE_BASE + model)
                .header("Authorization", "Key " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        String statusUrl;
        String responseUrl;
        try (Response response = httpClient.newCall(submitRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("fal.ai video submit failed with status {}: {}", response.code(), responseBody);
                return NodeExecutionResult.failure("fal.ai video submission failed (HTTP " + response.code() + ")");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            statusUrl = root.path("status_url").asText("");
            responseUrl = root.path("response_url").asText("");
            if (statusUrl.isBlank() || responseUrl.isBlank()) {
                return NodeExecutionResult.failure("fal.ai queue response is missing status/response URL");
            }
        }

        // 2. 輪詢直到完成（節點在 Virtual Thread 上執行，阻塞輪詢是安全的）
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);

            Request statusRequest = new Request.Builder()
                    .url(statusUrl)
                    .header("Authorization", "Key " + apiKey)
                    .get()
                    .build();

            String status;
            try (Response response = httpClient.newCall(statusRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    continue;
                }
                status = objectMapper.readTree(responseBody).path("status").asText("");
            }

            if ("COMPLETED".equals(status)) {
                return fetchVideoResult(context, apiKey, responseUrl);
            }
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                return NodeExecutionResult.failure("fal.ai video generation " + status.toLowerCase());
            }
        }

        return NodeExecutionResult.failure("fal.ai video generation timed out after " + timeoutSeconds + "s");
    }

    private NodeExecutionResult fetchVideoResult(NodeExecutionContext context, String apiKey, String responseUrl) throws IOException {
        Request resultRequest = new Request.Builder()
                .url(responseUrl)
                .header("Authorization", "Key " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(resultRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return NodeExecutionResult.failure("fal.ai result fetch failed (HTTP " + response.code() + ")");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String videoUrl = root.path("video").path("url").asText("");
            if (videoUrl.isBlank()) {
                // 部分模型回傳 videos 陣列
                videoUrl = root.path("videos").path(0).path("url").asText("");
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("videoUrl", videoUrl.isBlank() ? null : videoUrl);

            // 將遠端影片（URL 會過期）存入使用者 artifact 庫；失敗時仍回傳遠端 URL
            if (!videoUrl.isBlank()) {
                Artifact artifact = saveUrlAsArtifact(context, videoUrl, "video/mp4", "video", "mp4");
                if (artifact != null) {
                    output.put("artifactId", artifact.getId().toString());
                    output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
                }
            }

            output.put("raw", objectMapper.convertValue(root, Map.class));
            return NodeExecutionResult.success(output);
        }
    }

    /**
     * 從遠端 URL 儲存 artifact；失敗時記 warning 並回傳 null（不讓節點失敗）。
     */
    private Artifact saveUrlAsArtifact(NodeExecutionContext context, String url,
                                       String defaultMimeType, String prefix, String defaultExt) {
        try {
            ArtifactMeta meta = ArtifactMeta.builder()
                    .filename(filenameFromUrl(url, prefix, defaultExt))
                    .mimeType(defaultMimeType)
                    .flowId(context.getFlowId())
                    .executionId(context.getExecutionId())
                    .nodeId(context.getNodeId())
                    .sourceNodeType(getType())
                    .build();
            return artifactService.saveFromUrl(context.getUserId(), meta, url);
        } catch (Exception e) {
            log.warn("Failed to save fal.ai artifact from URL: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 從 URL path 取得檔名，取不到時使用 "{prefix}-{timestamp}.{ext}"。
     */
    private static String filenameFromUrl(String url, String prefix, String defaultExt) {
        try {
            String path = java.net.URI.create(url).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                if (!name.isBlank() && name.contains(".")) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // fall through to generated name
        }
        return prefix + "-" + System.currentTimeMillis() + "." + defaultExt;
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.length() > 200 ? msg.substring(0, 200) : String.valueOf(msg);
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "prompt", "type", "string", "required", false,
                       "description", "Prompt / storyline (can also come from node config)"),
                Map.of("name", "imageUrl", "type", "string", "required", false,
                       "description", "Reference image URL for image-to-video")
            ),
            "outputs", List.of(
                Map.of("name", "videoUrl", "type", "string",
                       "description", "Generated video URL"),
                Map.of("name", "images", "type", "array",
                       "description", "Generated image URLs"),
                Map.of("name", "artifactId", "type", "string",
                       "description", "Saved artifact ID (local copy of the generated media)"),
                Map.of("name", "downloadUrl", "type", "string",
                       "description", "Relative download URL of the saved artifact"),
                Map.of("name", "raw", "type", "object",
                       "description", "Full provider response")
            )
        );
    }
}
