package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that reads the text content of one of the user's artifacts.
 * Only text-like artifacts (text/*, application/json) are readable; binary
 * artifacts return metadata only. Ownership-checked, no existence leaks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReadArtifactTool implements AgentNodeTool {

    private static final int MAX_CONTENT_CHARS = 8000;
    /** Read at most 4 bytes per char budget to cover multi-byte UTF-8. */
    private static final int MAX_READ_BYTES = MAX_CONTENT_CHARS * 4;

    private final ArtifactService artifactService;

    @Override
    public String getId() {
        return "read_artifact";
    }

    @Override
    public String getName() {
        return "Read Artifact";
    }

    @Override
    public String getDescription() {
        return """
                Reads the text content of one of the current user's artifacts.
                Use list_artifacts first to find the artifactId. Only text-like
                artifacts (MIME type text/* or application/json) are readable;
                for binary artifacts only metadata is returned. Content longer
                than 8000 characters is truncated.

                Parameters:
                - artifactId: UUID of the artifact to read (required)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "artifactId", Map.of(
                                "type", "string",
                                "description", "UUID of the artifact to read"
                        )
                ),
                "required", List.of("artifactId")
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

        UUID artifactId = ToolSecurity.parseUuid(
                parameters.get("artifactId") instanceof String s ? s : null);
        if (artifactId == null) {
            return ToolResult.failure("Invalid or missing artifactId (must be a UUID)");
        }

        Artifact artifact;
        try {
            // Ownership check inside getOwned — non-owner gets the same generic
            // not-found as a missing artifact (no existence leak).
            artifact = artifactService.getOwned(artifactId, userId);
        } catch (ResourceNotFoundException e) {
            return ToolResult.failure("Artifact not found: " + artifactId);
        }

        if (!isTextLike(artifact.getMimeType())) {
            return ToolResult.success(
                    "Artifact is binary — content not readable as text.\n"
                            + metadataSummary(artifact),
                    metadataMap(artifact));
        }

        try {
            Resource resource = artifactService.openResource(artifact);
            String content = readBounded(resource);
            boolean truncated = content.length() > MAX_CONTENT_CHARS;
            if (truncated) {
                content = content.substring(0, MAX_CONTENT_CHARS);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(metadataSummary(artifact)).append("\nContent:\n").append(content);
            if (truncated) {
                sb.append("\n...(truncated at ").append(MAX_CONTENT_CHARS).append(" characters)");
            }
            return ToolResult.success(sb.toString(), metadataMap(artifact));

        } catch (ResourceNotFoundException e) {
            return ToolResult.failure("Artifact not found: " + artifactId);
        } catch (Exception e) {
            log.error("read_artifact tool failed for artifact {}", artifactId, e);
            return ToolResult.failure("Failed to read artifact content");
        }
    }

    private boolean isTextLike(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String normalized = mimeType.toLowerCase();
        return normalized.startsWith("text/") || normalized.startsWith("application/json");
    }

    private String readBounded(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            byte[] bytes = is.readNBytes(MAX_READ_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String metadataSummary(Artifact artifact) {
        return "Artifact: " + artifact.getFilename()
                + " (id: " + artifact.getId()
                + ", type: " + artifact.getMimeType()
                + ", size: " + artifact.getSizeBytes() + " bytes)";
    }

    private Map<String, Object> metadataMap(Artifact artifact) {
        return Map.of(
                "id", artifact.getId().toString(),
                "filename", artifact.getFilename(),
                "mimeType", artifact.getMimeType(),
                "sizeBytes", artifact.getSizeBytes()
        );
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
