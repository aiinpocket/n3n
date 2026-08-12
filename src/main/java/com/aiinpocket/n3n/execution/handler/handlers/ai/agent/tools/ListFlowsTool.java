package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.common.constant.Status;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that lists the current user's flows.
 * Lets the AI agent discover flows it can run via the run_flow tool.
 */
@Component
@Slf4j
public class ListFlowsTool implements AgentNodeTool {

    private static final int MAX_RESULTS = 20;

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;

    public ListFlowsTool(FlowRepository flowRepository,
                         FlowVersionRepository flowVersionRepository) {
        this.flowRepository = flowRepository;
        this.flowVersionRepository = flowVersionRepository;
    }

    @Override
    public String getId() {
        return "list_flows";
    }

    @Override
    public String getName() {
        return "List Flows";
    }

    @Override
    public String getDescription() {
        return """
                Lists the user's own workflows (flows) on this platform, so you can find one to run.

                Returns up to 20 flows with their id, name, description, and whether they have a
                published version. Only flows with a published version can be executed with the
                run_flow tool.

                Parameters:
                - query: Optional name filter (case-insensitive substring match)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Optional filter: only return flows whose name contains this text (case-insensitive)"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID userId = parseUserId(context);
                if (userId == null) {
                    return ToolResult.failure("No authenticated user in execution context");
                }

                String query = parameters.get("query") instanceof String s ? s.trim() : "";

                List<Flow> flows = flowRepository
                        .findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                        .stream()
                        .filter(f -> query.isEmpty()
                                || (f.getName() != null && f.getName().toLowerCase().contains(query.toLowerCase())))
                        .limit(MAX_RESULTS)
                        .toList();

                if (flows.isEmpty()) {
                    return ToolResult.success(
                            query.isEmpty() ? "No flows found." : "No flows found matching: " + query,
                            Map.of("flows", List.of()));
                }

                // Batch query published versions to avoid N+1
                List<UUID> flowIds = flows.stream().map(Flow::getId).toList();
                Set<UUID> publishedFlowIds = new HashSet<>();
                for (FlowVersion version : flowVersionRepository
                        .findByFlowIdInAndStatus(flowIds, Status.FlowVersion.PUBLISHED)) {
                    publishedFlowIds.add(version.getFlowId());
                }

                List<Map<String, Object>> flowList = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Found %d flow(s):%n", flows.size()));

                for (Flow flow : flows) {
                    boolean hasPublished = publishedFlowIds.contains(flow.getId());

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", flow.getId().toString());
                    entry.put("name", flow.getName());
                    entry.put("description", flow.getDescription() != null ? flow.getDescription() : "");
                    entry.put("hasPublishedVersion", hasPublished);
                    flowList.add(entry);

                    sb.append(String.format("- %s (id: %s)%s%s%n",
                            flow.getName(),
                            flow.getId(),
                            hasPublished ? " [published]" : " [not published]",
                            flow.getDescription() != null && !flow.getDescription().isBlank()
                                    ? " - " + flow.getDescription()
                                    : ""));
                }

                return ToolResult.success(sb.toString(), Map.of("flows", flowList));

            } catch (Exception e) {
                log.error("list_flows tool failed", e);
                return ToolResult.failure("Failed to list flows");
            }
        });
    }

    private UUID parseUserId(ToolExecutionContext context) {
        if (context == null || context.userId() == null) {
            return null;
        }
        try {
            return UUID.fromString(context.userId());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
