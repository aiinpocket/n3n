package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 儲存內容為使用者 artifact（產出檔案庫）的通用節點。
 *
 * 將前置節點產生的文字/HTML/Markdown/JSON 或 base64 內容
 * 存入個人 artifact 庫，可於 Artifacts 頁面瀏覽與下載。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaveArtifactNodeHandler extends MultiOperationNodeHandler {

    private static final String CONTENT_TYPE_CUSTOM = "custom";

    private final ArtifactService artifactService;

    @Override
    public String getType() {
        return "saveArtifact";
    }

    @Override
    public String getDisplayName() {
        return "Save Artifact";
    }

    @Override
    public String getDescription() {
        return "Save content (text, HTML, Markdown, JSON, or base64 data) into your artifact library "
                + "for browsing and downloading later.";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "save";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("artifact", ResourceDef.of("artifact", "Artifact", "Generated file library"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        operations.put("artifact", List.of(
            OperationDef.create("save", "Save Content")
                .description("Save content as a file in your artifact library")
                .fields(List.of(
                    FieldDef.string("filename", "Filename")
                        .withPlaceholder("report.html")
                        .withDescription("File name including extension")
                        .required(),
                    FieldDef.select("contentType", "Content Type", List.of(
                            "text/plain", "text/html", "text/markdown",
                            "application/json", "text/csv", CONTENT_TYPE_CUSTOM))
                        .withDefault("text/plain")
                        .withDescription("MIME type of the saved file"),
                    FieldDef.string("customMimeType", "Custom MIME Type")
                        .withPlaceholder("application/pdf")
                        .withDescription("MIME type to use when Content Type is 'custom'"),
                    FieldDef.textarea("content", "Content")
                        .withPlaceholder("{{previousNode.output}} or literal content...")
                        .withDescription("Content to save; supports {{expressions}}")
                        .required(),
                    FieldDef.select("encoding", "Encoding", List.of("utf8", "base64"))
                        .withOptionLabels(List.of("UTF-8 Text", "Base64"))
                        .withDefault("utf8")
                        .withDescription("How to interpret the content: plain text or base64-encoded binary")
                ))
                .outputDescription("Returns 'artifactId', 'downloadUrl', 'filename', 'mimeType', 'sizeBytes'")
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

        if (!"save".equals(operation)) {
            return NodeExecutionResult.failure("Unknown operation: " + resource + "." + operation);
        }

        String filename = getRequiredParam(params, "filename");
        String content = getRequiredParam(params, "content");
        String encoding = getParam(params, "encoding", "utf8");
        String mimeType = resolveMimeType(params);

        byte[] data;
        if ("base64".equals(encoding)) {
            try {
                data = Base64.getDecoder().decode(content.trim());
            } catch (IllegalArgumentException e) {
                return NodeExecutionResult.failure("Content is not valid base64");
            }
        } else {
            data = content.getBytes(StandardCharsets.UTF_8);
        }

        ArtifactMeta meta = ArtifactMeta.builder()
                .filename(filename)
                .mimeType(mimeType)
                .flowId(context.getFlowId())
                .executionId(context.getExecutionId())
                .nodeId(context.getNodeId())
                .sourceNodeType(getType())
                .build();

        Artifact artifact = artifactService.save(context.getUserId(), meta, data);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("artifactId", artifact.getId().toString());
        output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
        output.put("filename", artifact.getFilename());
        output.put("mimeType", artifact.getMimeType());
        output.put("sizeBytes", artifact.getSizeBytes());
        return NodeExecutionResult.success(output);
    }

    private String resolveMimeType(Map<String, Object> params) {
        String contentType = getParam(params, "contentType", "text/plain");
        if (CONTENT_TYPE_CUSTOM.equals(contentType)) {
            String custom = getParam(params, "customMimeType", "");
            return custom.isBlank() ? "application/octet-stream" : custom;
        }
        return contentType;
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "content", "type", "string", "required", false,
                       "description", "Content to save (can also come from node config)")
            ),
            "outputs", List.of(
                Map.of("name", "artifactId", "type", "string",
                       "description", "Saved artifact ID"),
                Map.of("name", "downloadUrl", "type", "string",
                       "description", "Relative download URL"),
                Map.of("name", "filename", "type", "string",
                       "description", "Stored filename"),
                Map.of("name", "mimeType", "type", "string",
                       "description", "MIME type of the saved file"),
                Map.of("name", "sizeBytes", "type", "integer",
                       "description", "File size in bytes")
            )
        );
    }
}
