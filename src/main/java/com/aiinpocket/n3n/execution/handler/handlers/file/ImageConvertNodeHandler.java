package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 圖片格式轉換節點：PNG / JPG / GIF / BMP 互轉，存進作品庫。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageConvertNodeHandler extends AbstractNodeHandler {

    private final ArtifactService artifactService;

    @Override
    public String getType() {
        return "imageConvert";
    }

    @Override
    public String getDisplayName() {
        return "Image Convert";
    }

    @Override
    public String getDescription() {
        return "Convert an image to another format (PNG/JPG/GIF/BMP) and save it to the artifact library. "
                + "轉換圖片格式並存入作品庫；來源可用網址或上一步的輸出。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-image";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String format = getStringConfig(context, "format", "png");
        try {
            BufferedImage source = ImageNodeSupport.loadImage(
                    context,
                    getStringConfig(context, "imageUrl", ""),
                    getStringConfig(context, "imageData", ""));

            Map<String, Object> output = ImageNodeSupport.saveAsArtifact(
                    artifactService, context, getType(), source, format,
                    getStringConfig(context, "filename", ""));
            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            log.warn("Image convert failed: {}", e.getMessage());
            return NodeExecutionResult.failure("Image convert failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("imageUrl", Map.of(
                "type", "string",
                "title", "Image URL",
                "description", "Image to convert; leave empty to use the previous step's output. "
                        + "圖片網址；留空則用上一步傳來的圖片"
        ));
        properties.put("format", Map.of(
                "type", "string",
                "title", "Output Format",
                "enum", java.util.List.of("png", "jpg", "gif", "bmp"),
                "default", "png"
        ));
        properties.put("filename", Map.of("type", "string", "title", "Filename"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", java.util.List.of("format")
        );
    }
}
