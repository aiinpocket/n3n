package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that saves text content as an artifact in the current user's
 * library. The platform max-file-size limit is enforced by ArtifactStorageService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaveArtifactTool implements AgentNodeTool {

    private static final String DEFAULT_CONTENT_TYPE = "text/markdown";

    private final ArtifactService artifactService;

    @Override
    public String getId() {
        return "save_artifact";
    }

    @Override
    public String getName() {
        return "Save Artifact";
    }

    @Override
    public String getDescription() {
        return """
                Saves text content as a file (artifact) in the current user's artifact
                library, so the user can download it later. Use this to persist reports,
                summaries, generated documents, or data the user asked you to produce.
                Returns the artifactId and a download URL.

                Parameters:
                - filename: File name including extension, e.g. "report.md" (required)
                - content: The text content to save (required)
                - contentType: MIME type, defaults to "text/markdown"
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "filename", Map.of(
                                "type", "string",
                                "description", "File name including extension (e.g. \"report.md\")"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "Text content to save"
                        ),
                        "contentType", Map.of(
                                "type", "string",
                                "description", "MIME type of the content",
                                "default", DEFAULT_CONTENT_TYPE
                        )
                ),
                "required", List.of("filename", "content")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(parameters, context));
    }

    private ToolResult doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        UUID userId = ToolSecurity.parseUserId(context);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        String filename = parameters.get("filename") instanceof String f && !f.isBlank() ? f : null;
        if (filename == null) {
            return ToolResult.failure("Missing required parameter: filename");
        }

        String content = parameters.get("content") instanceof String c ? c : null;
        if (content == null) {
            return ToolResult.failure("Missing required parameter: content");
        }

        String contentType = parameters.get("contentType") instanceof String ct && !ct.isBlank()
                ? ct
                : DEFAULT_CONTENT_TYPE;

        ArtifactMeta meta = ArtifactMeta.builder()
                .filename(filename)
                .mimeType(contentType)
                .flowId(ToolSecurity.parseUuid(context.flowId()))
                .executionId(ToolSecurity.parseUuid(context.executionId()))
                .sourceNodeType("aiAgent")
                .build();

        try {
            Artifact artifact = artifactService.save(
                    userId, meta, content.getBytes(StandardCharsets.UTF_8));

            String downloadUrl = ArtifactService.downloadUrl(artifact.getId());
            return ToolResult.success(
                    "Artifact saved: " + artifact.getFilename()
                            + " (id: " + artifact.getId()
                            + ", size: " + artifact.getSizeBytes() + " bytes)\n"
                            + "Download URL: " + downloadUrl,
                    Map.of(
                            "artifactId", artifact.getId().toString(),
                            "filename", artifact.getFilename(),
                            "downloadUrl", downloadUrl,
                            "sizeBytes", artifact.getSizeBytes()
                    ));

        } catch (IllegalArgumentException e) {
            // e.g. max-file-size exceeded — message is safe (no secrets)
            return ToolResult.failure("Cannot save artifact: " + e.getMessage());
        } catch (Exception e) {
            log.error("save_artifact tool failed for user {}", userId, e);
            return ToolResult.failure("Failed to save artifact");
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
