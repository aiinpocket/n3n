package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 圖片縮放節點：把圖片縮放到指定寬高（等比或強制），存進作品庫。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageResizeNodeHandler extends AbstractNodeHandler {

    private final ArtifactService artifactService;

    @Override
    public String getType() {
        return "imageResize";
    }

    @Override
    public String getDisplayName() {
        return "Image Resize";
    }

    @Override
    public String getDescription() {
        return "Resize an image (from URL, base64 or the previous step) and save it to the artifact library. "
                + "縮放圖片並存入作品庫；來源可用網址或上一步的輸出。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "picture";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        int width = getIntConfig(context, "width", 0);
        int height = getIntConfig(context, "height", 0);
        if (width <= 0 && height <= 0) {
            return NodeExecutionResult.failure("Set width and/or height — 請填縮放後的寬或高（像素）");
        }

        try {
            BufferedImage source = ImageNodeSupport.loadImage(
                    context,
                    getStringConfig(context, "imageUrl", ""),
                    getStringConfig(context, "imageData", ""));

            boolean keepAspect = getBooleanConfig(context, "keepAspectRatio", true);
            int targetW = width;
            int targetH = height;
            if (keepAspect) {
                double ratio = (double) source.getWidth() / source.getHeight();
                if (targetW <= 0) {
                    targetW = (int) Math.round(targetH * ratio);
                } else if (targetH <= 0) {
                    targetH = (int) Math.round(targetW / ratio);
                } else {
                    // 兩者都填時取縮小比例較大的那邊，確保不超框
                    double scale = Math.min((double) targetW / source.getWidth(), (double) targetH / source.getHeight());
                    targetW = (int) Math.round(source.getWidth() * scale);
                    targetH = (int) Math.round(source.getHeight() * scale);
                }
            } else {
                if (targetW <= 0) targetW = source.getWidth();
                if (targetH <= 0) targetH = source.getHeight();
            }

            BufferedImage resized = new BufferedImage(targetW, targetH,
                    source.getTransparency() == java.awt.Transparency.OPAQUE
                            ? BufferedImage.TYPE_INT_RGB
                            : BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetW, targetH, null);
            g.dispose();

            String format = getStringConfig(context, "format", "png");
            Map<String, Object> output = ImageNodeSupport.saveAsArtifact(
                    artifactService, context, getType(), resized, format,
                    getStringConfig(context, "filename", ""));
            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            log.warn("Image resize failed: {}", e.getMessage());
            return NodeExecutionResult.failure("Image resize failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("imageUrl", Map.of(
                "type", "string",
                "title", "Image URL",
                "description", "Image to process; leave empty to use the previous step's output. "
                        + "圖片網址；留空則用上一步傳來的圖片"
        ));
        properties.put("width", Map.of("type", "integer", "title", "Width (px)"));
        properties.put("height", Map.of("type", "integer", "title", "Height (px)"));
        properties.put("keepAspectRatio", Map.of(
                "type", "boolean",
                "title", "Keep Aspect Ratio",
                "default", true
        ));
        properties.put("format", Map.of(
                "type", "string",
                "title", "Output Format",
                "enum", java.util.List.of("png", "jpg", "gif", "bmp"),
                "default", "png"
        ));
        properties.put("filename", Map.of("type", "string", "title", "Filename"));
        return Map.of("type", "object", "properties", properties);
    }
}
