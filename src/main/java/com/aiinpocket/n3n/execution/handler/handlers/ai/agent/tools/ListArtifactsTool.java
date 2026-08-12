package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that lists the current user's artifacts (files produced by
 * flow nodes or saved by the agent). Strictly scoped to the requesting user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListArtifactsTool implements AgentNodeTool {

    private static final int MAX_RESULTS = 20;

    private final ArtifactService artifactService;

    @Override
    public String getId() {
        return "list_artifacts";
    }

    @Override
    public String getName() {
        return "List Artifacts";
    }

    @Override
    public String getDescription() {
        return """
                Lists the current user's artifacts (files produced by flows or saved
                by the agent) with id, filename, MIME type, size, and creation time.
                Use this to find an artifactId before calling read_artifact, or to
                check what files the user already has. Returns at most 20 newest items.

                Parameters:
                - type: Optional MIME type prefix filter, e.g. "text/", "image/", "application/json"
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "description", "Optional MIME type prefix filter (e.g. \"text/\", \"image/\")"
                        )
                ),
                "required", List.of()
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

        String typeFilter = parameters.get("type") instanceof String s && !s.isBlank() ? s : null;

        try {
            Page<Artifact> page = artifactService.list(
                    userId, typeFilter, PageRequest.of(0, MAX_RESULTS));

            if (page.isEmpty()) {
                return ToolResult.success("No artifacts found"
                        + (typeFilter != null ? " for type filter: " + typeFilter : ""));
            }

            List<Map<String, Object>> items = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(page.getNumberOfElements()).append(" artifact(s)");
            if (page.getTotalElements() > MAX_RESULTS) {
                sb.append(" (showing newest ").append(MAX_RESULTS)
                        .append(" of ").append(page.getTotalElements()).append(")");
            }
            sb.append(":\n");

            for (Artifact artifact : page.getContent()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", artifact.getId().toString());
                item.put("filename", artifact.getFilename());
                item.put("mimeType", artifact.getMimeType());
                item.put("sizeBytes", artifact.getSizeBytes());
                item.put("createdAt", artifact.getCreatedAt() != null
                        ? artifact.getCreatedAt().toString() : null);
                items.add(item);

                sb.append("- ").append(artifact.getFilename())
                        .append(" (id: ").append(artifact.getId())
                        .append(", type: ").append(artifact.getMimeType())
                        .append(", size: ").append(artifact.getSizeBytes()).append(" bytes")
                        .append(")\n");
            }

            return ToolResult.success(sb.toString(), Map.of("artifacts", items));

        } catch (Exception e) {
            log.error("list_artifacts tool failed for user {}", userId, e);
            return ToolResult.failure("Failed to list artifacts");
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
