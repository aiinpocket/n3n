package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 圖片節點共用邏輯：載入來源（網址 / base64 / 上游輸出）、輸出成作品庫檔案。
 */
final class ImageNodeSupport {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ImageNodeSupport() {
    }

    /**
     * 依序嘗試：config.imageUrl（下載）→ config.imageData（base64）→
     * 上游輸出的 imageUrl / imageData / images[0]。
     */
    static BufferedImage loadImage(NodeExecutionContext context, String imageUrl, String imageData)
            throws IOException, InterruptedException {
        if (imageUrl != null && !imageUrl.isBlank()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl.trim()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IOException("Failed to download image, HTTP " + response.statusCode());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IOException("Downloaded content is not a readable image");
            }
            return image;
        }

        if (imageData != null && !imageData.isBlank()) {
            String base64 = imageData.trim();
            int comma = base64.indexOf(',');
            if (base64.startsWith("data:") && comma > 0) {
                base64 = base64.substring(comma + 1);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            if (image == null) {
                throw new IOException("imageData is not a readable image");
            }
            return image;
        }

        // 上游輸出（例如 falAi 的 images 陣列或 httpRequest 抓回的網址）
        Map<String, Object> input = context.getInputData();
        if (input != null) {
            Object url = input.get("imageUrl");
            if (url == null && input.get("images") instanceof java.util.List<?> list && !list.isEmpty()) {
                url = list.get(0);
            }
            if (url instanceof String s && !s.isBlank()) {
                return loadImage(context, s, null);
            }
            Object data = input.get("imageData");
            if (data instanceof String s && !s.isBlank()) {
                return loadImage(context, null, s);
            }
        }

        throw new IOException("No image source — set Image URL or Image Data, or feed one from the previous step. "
                + "沒有圖片來源：請填圖片網址，或由上一步傳入");
    }

    /** 輸出成作品庫檔案並回傳統一的輸出欄位 */
    static Map<String, Object> saveAsArtifact(ArtifactService artifactService, NodeExecutionContext context,
                                              String sourceNodeType, BufferedImage image,
                                              String format, String filename) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String normalized = normalizeFormat(format);
        // JPEG 不支援透明通道，先鋪白底
        BufferedImage toWrite = image;
        if (("jpg".equals(normalized)) && image.getTransparency() != java.awt.Transparency.OPAQUE) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            toWrite = rgb;
        }
        if (!ImageIO.write(toWrite, normalized, out)) {
            throw new IOException("Unsupported output format: " + format);
        }

        String finalName = filename == null || filename.isBlank() ? "image." + extensionOf(normalized) : filename;
        if (!finalName.toLowerCase().endsWith("." + extensionOf(normalized))) {
            finalName = finalName + "." + extensionOf(normalized);
        }

        ArtifactMeta meta = ArtifactMeta.builder()
                .filename(finalName)
                .mimeType(mimeOf(normalized))
                .flowId(context.getFlowId())
                .executionId(context.getExecutionId())
                .nodeId(context.getNodeId())
                .sourceNodeType(sourceNodeType)
                .build();
        Artifact artifact = artifactService.save(context.getUserId(), meta, out.toByteArray());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("artifactId", artifact.getId().toString());
        output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
        output.put("imageUrl", ArtifactService.downloadUrl(artifact.getId()));
        output.put("filename", artifact.getFilename());
        output.put("width", image.getWidth());
        output.put("height", image.getHeight());
        output.put("format", normalized);
        return output;
    }

    static String normalizeFormat(String format) {
        String f = format == null ? "png" : format.trim().toLowerCase();
        return switch (f) {
            case "jpeg", "jpg" -> "jpg";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "png";
        };
    }

    private static String extensionOf(String normalized) {
        return normalized;
    }

    private static String mimeOf(String normalized) {
        return switch (normalized) {
            case "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }
}
