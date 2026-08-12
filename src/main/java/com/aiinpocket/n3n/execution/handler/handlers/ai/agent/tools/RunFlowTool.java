package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.execution.service.StateManager;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that runs one of the user's published flows and waits for it to finish.
 *
 * Uses ObjectProvider to lazily inject ExecutionService and break the circular dependency:
 * ExecutionService -> NodeHandlerRegistry -> AiAgentNodeHandler -> AgentNodeToolRegistry
 * -> RunFlowTool -> ExecutionService
 */
@Component
@Slf4j
public class RunFlowTool implements AgentNodeTool {

    private static final String DEPTH_KEY = "_subWorkflowDepth";
    private static final int MAX_DEPTH = 5;
    private static final int DEFAULT_WAIT_SECONDS = 120;
    private static final int MIN_WAIT_SECONDS = 5;
    private static final int MAX_WAIT_SECONDS = 300;
    private static final int OUTPUT_MAX_CHARS = 4000;
    private static final long POLL_INTERVAL_MS = 1000L;

    private final ObjectProvider<ExecutionService> executionServiceProvider;
    private final FlowRepository flowRepository;
    private final ExecutionRepository executionRepository;
    private final StateManager stateManager;
    private final ObjectMapper objectMapper;

    public RunFlowTool(ObjectProvider<ExecutionService> executionServiceProvider,
                       FlowRepository flowRepository,
                       ExecutionRepository executionRepository,
                       StateManager stateManager,
                       ObjectMapper objectMapper) {
        this.executionServiceProvider = executionServiceProvider;
        this.flowRepository = flowRepository;
        this.executionRepository = executionRepository;
        this.stateManager = stateManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getId() {
        return "run_flow";
    }

    @Override
    public String getName() {
        return "Run Flow";
    }

    @Override
    public String getDescription() {
        return """
                Runs one of the user's own flows on this platform and waits for it to finish,
                returning its output. The flow must have a published version — use the
                list_flows tool first to find a flow id and check hasPublishedVersion.

                Parameters:
                - flowId: UUID of the flow to run (required)
                - input: Optional object passed to the flow as trigger data
                - waitSeconds: How long to wait for completion (default 120, min 5, max 300)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "flowId", Map.of(
                                "type", "string",
                                "description", "UUID of the flow to run (must have a published version)"
                        ),
                        "input", Map.of(
                                "type", "object",
                                "description", "Optional input object passed to the flow as trigger data"
                        ),
                        "waitSeconds", Map.of(
                                "type", "integer",
                                "description", "Maximum seconds to wait for the flow to finish",
                                "default", DEFAULT_WAIT_SECONDS,
                                "minimum", MIN_WAIT_SECONDS,
                                "maximum", MAX_WAIT_SECONDS
                        )
                ),
                "required", List.of("flowId")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("run-flow-tool").start(() -> {
            try {
                future.complete(doExecute(parameters, context));
            } catch (Exception e) {
                log.error("run_flow tool failed", e);
                future.complete(ToolResult.failure("Flow execution failed"));
            }
        });
        return future;
    }

    private ToolResult doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        UUID userId = parseUuid(context != null ? context.userId() : null);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        UUID flowId = parseUuid(parameters.get("flowId") instanceof String s ? s : null);
        if (flowId == null) {
            return ToolResult.failure("Invalid or missing flowId (must be a UUID)");
        }

        int waitSeconds = clampWaitSeconds(parameters.get("waitSeconds"));

        // Ownership check — do not leak existence of other users' flows
        Optional<Flow> flowOpt = flowRepository.findByIdAndIsDeletedFalse(flowId);
        if (flowOpt.isEmpty() || !userId.equals(flowOpt.get().getCreatedBy())) {
            return ToolResult.failure("Flow not found: " + flowId);
        }

        // Recursion guard — bound nested run_flow / subWorkflow calls
        int currentDepth = 0;
        Map<String, Object> flowVariables = context.flowVariables();
        if (flowVariables != null && flowVariables.get(DEPTH_KEY) instanceof Number depthNum) {
            currentDepth = depthNum.intValue();
        }
        if (currentDepth >= MAX_DEPTH) {
            return ToolResult.failure(
                    "Flow recursion depth limit exceeded (max " + MAX_DEPTH + ")");
        }

        ExecutionService executionService = executionServiceProvider.getIfAvailable();
        if (executionService == null) {
            return ToolResult.failure("ExecutionService not available");
        }

        Map<String, Object> triggerData = new HashMap<>();
        triggerData.put("triggeredBy", "aiAgent");
        triggerData.put("parentExecutionId", context.executionId());
        triggerData.put(DEPTH_KEY, currentDepth + 1);
        if (parameters.get("input") instanceof Map<?, ?> input) {
            triggerData.put("input", input);
        }

        ExecutionResponse execution;
        try {
            execution = executionService.startExecution(flowId, userId, triggerData);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // No published version / concurrency limits
            return ToolResult.failure("Cannot run flow: " + e.getMessage());
        }

        return pollForCompletion(execution.getId(), flowOpt.get().getName(), waitSeconds);
    }

    private ToolResult pollForCompletion(UUID executionId, String flowName, int waitSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = waitSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Optional<Execution> execOpt = executionRepository.findById(executionId);
            if (execOpt.isPresent()) {
                Execution exec = execOpt.get();
                String status = exec.getStatus();

                if ("completed".equals(status)) {
                    return buildSuccessResult(executionId, flowName, exec);
                }
                if ("failed".equals(status)) {
                    return ToolResult.failure(
                            "Flow failed: " + flowName + " (executionId: " + executionId + ")");
                }
                if ("cancelled".equals(status)) {
                    String reason = exec.getCancelReason();
                    return ToolResult.failure(
                            "Flow cancelled: " + flowName + " (executionId: " + executionId + ")"
                                    + (reason != null && !reason.isBlank() ? " - " + reason : ""));
                }
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.failure("Flow polling interrupted");
            }
        }

        return ToolResult.failure(
                "Flow still running after " + waitSeconds + "s, executionId=" + executionId
                        + " — the user can check its status later with this id");
    }

    private ToolResult buildSuccessResult(UUID executionId, String flowName, Execution exec) {
        String outputJson = serializeOutput(executionId);

        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId.toString());
        data.put("status", "completed");
        data.put("durationMs", exec.getDurationMs());
        data.put("output", outputJson);

        return ToolResult.success(
                "Flow completed: " + flowName
                        + " (executionId: " + executionId
                        + ", durationMs: " + exec.getDurationMs() + ")\nOutput:\n" + outputJson,
                data);
    }

    private String serializeOutput(UUID executionId) {
        try {
            Map<String, Object> output = stateManager.getExecutionOutput(executionId);
            String json = objectMapper.writeValueAsString(output != null ? output : Map.of());
            if (json.length() > OUTPUT_MAX_CHARS) {
                return json.substring(0, OUTPUT_MAX_CHARS) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to serialize output for execution {}", executionId, e);
            return "{}";
        }
    }

    private int clampWaitSeconds(Object value) {
        int waitSeconds = value instanceof Number n ? n.intValue() : DEFAULT_WAIT_SECONDS;
        return Math.min(MAX_WAIT_SECONDS, Math.max(MIN_WAIT_SECONDS, waitSeconds));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int getTimeoutSeconds() {
        return MAX_WAIT_SECONDS;
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
