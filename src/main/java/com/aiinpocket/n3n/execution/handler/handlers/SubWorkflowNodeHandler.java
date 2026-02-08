package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for sub-workflow nodes.
 * Executes another workflow as part of the current flow.
 *
 * Uses ObjectProvider to lazily inject ExecutionService
 * and break the circular dependency:
 * ExecutionService -> NodeHandlerRegistry -> SubWorkflowNodeHandler -> ExecutionService
 */
@Component
@Slf4j
public class SubWorkflowNodeHandler extends AbstractNodeHandler {

    private final ObjectProvider<ExecutionService> executionServiceProvider;
    private final FlowRepository flowRepository;
    private final ExecutionRepository executionRepository;

    public SubWorkflowNodeHandler(
            ObjectProvider<ExecutionService> executionServiceProvider,
            FlowRepository flowRepository,
            ExecutionRepository executionRepository) {
        this.executionServiceProvider = executionServiceProvider;
        this.flowRepository = flowRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    public String getType() {
        return "subWorkflow";
    }

    @Override
    public String getDisplayName() {
        return "Execute Sub-workflow";
    }

    @Override
    public String getDescription() {
        return "Executes another workflow and returns its output.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "workflow";
    }

    private static final int MAX_SUB_WORKFLOW_DEPTH = 10;
    private static final String DEPTH_KEY = "_subWorkflowDepth";

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String workflowId = getStringConfig(context, "workflowId", "");
        boolean waitForCompletion = getBooleanConfig(context, "waitForCompletion", true);
        int timeoutSeconds = getIntConfig(context, "timeoutSeconds", 300);

        // Check recursion depth to prevent infinite sub-workflow loops
        int currentDepth = 0;
        Map<String, Object> globalCtx = context.getGlobalContext();
        if (globalCtx != null && globalCtx.get(DEPTH_KEY) instanceof Number depthNum) {
            currentDepth = depthNum.intValue();
        }
        if (currentDepth >= MAX_SUB_WORKFLOW_DEPTH) {
            return NodeExecutionResult.failure(
                "Sub-workflow recursion depth limit exceeded (max " + MAX_SUB_WORKFLOW_DEPTH + ")");
        }

        if (workflowId.isEmpty()) {
            return NodeExecutionResult.failure("Workflow ID is required");
        }

        UUID subFlowId;
        try {
            subFlowId = UUID.fromString(workflowId);
        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failure("Invalid workflow ID format: " + workflowId);
        }

        // 驗證子工作流程存在
        Optional<Flow> subFlowOpt = flowRepository.findByIdAndIsDeletedFalse(subFlowId);
        if (subFlowOpt.isEmpty()) {
            return NodeExecutionResult.failure("Sub-workflow not found: " + workflowId);
        }
        Flow subFlow = subFlowOpt.get();

        ExecutionService executionService = executionServiceProvider.getIfAvailable();
        if (executionService == null) {
            return NodeExecutionResult.failure("ExecutionService not available");
        }

        // 準備子工作流程的輸入
        Map<String, Object> subInput = new HashMap<>(context.getInputData() != null ? context.getInputData() : Map.of());

        // 如果有 inputMapping，使用映射取代直接傳遞
        Map<String, Object> inputMapping = getMapConfig(context, "inputMapping");
        if (!inputMapping.isEmpty()) {
            subInput = applyInputMapping(inputMapping, context);
        }

        try {
            log.info("Triggering sub-workflow: {} (flowId: {}, wait: {})",
                    subFlow.getName(), subFlowId, waitForCompletion);

            // 觸發子工作流程
            ExecutionResponse subExecution = executionService.startExecution(
                    subFlowId, context.getUserId(), subInput);

            UUID subExecutionId = subExecution.getId();

            if (!waitForCompletion) {
                Map<String, Object> output = new HashMap<>();
                output.put("subExecutionId", subExecutionId.toString());
                output.put("status", "triggered");
                output.put("subFlowName", subFlow.getName());
                return NodeExecutionResult.success(output, Map.of(
                        "subWorkflowId", workflowId,
                        "subExecutionId", subExecutionId.toString()
                ));
            }

            // 等待子工作流程完成
            return pollForCompletion(executionService, subExecutionId, subFlow.getName(), timeoutSeconds);

        } catch (Exception e) {
            log.error("Sub-workflow execution failed for flow: {}", workflowId, e);
            return NodeExecutionResult.failure("Sub-workflow execution failed: " + e.getMessage());
        }
    }

    private NodeExecutionResult pollForCompletion(
            ExecutionService executionService,
            UUID subExecutionId,
            String subFlowName,
            int timeoutSeconds) {

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Optional<Execution> execOpt = executionRepository.findById(subExecutionId);
            if (execOpt.isEmpty()) break;

            Execution exec = execOpt.get();
            String status = exec.getStatus();

            if ("completed".equals(status)) {
                Map<String, Object> output = new HashMap<>();
                output.put("subExecutionId", subExecutionId.toString());
                output.put("subFlowName", subFlowName);
                output.put("status", "completed");
                output.put("durationMs", exec.getDurationMs());
                return NodeExecutionResult.success(output, Map.of(
                        "subExecutionId", subExecutionId.toString(),
                        "subFlowStatus", "completed"
                ));
            }

            if ("failed".equals(status)) {
                return NodeExecutionResult.failure(
                        "Sub-workflow failed: " + subFlowName + " (executionId: " + subExecutionId + ")");
            }

            if ("cancelled".equals(status)) {
                return NodeExecutionResult.failure(
                        "Sub-workflow cancelled: " + subFlowName + " (executionId: " + subExecutionId + ")");
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return NodeExecutionResult.failure("Sub-workflow polling interrupted");
            }
        }

        return NodeExecutionResult.failure(
                "Sub-workflow timed out after " + timeoutSeconds + " seconds: " + subFlowName);
    }

    private Map<String, Object> applyInputMapping(
            Map<String, Object> mapping, NodeExecutionContext context) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String targetKey = entry.getKey();
            Object sourceExpr = entry.getValue();
            if (sourceExpr instanceof String expr) {
                try {
                    Object resolved = context.evaluateExpression(expr);
                    result.put(targetKey, resolved);
                } catch (Exception e) {
                    log.warn("Failed to evaluate expression '{}', using literal value", expr);
                    result.put(targetKey, sourceExpr);
                }
            } else {
                result.put(targetKey, sourceExpr);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("workflowId"),
            "properties", Map.of(
                "workflowId", Map.of(
                    "type", "string",
                    "title", "Workflow ID",
                    "description", "ID of the workflow to execute"
                ),
                "workflowVersion", Map.of(
                    "type", "string",
                    "title", "Workflow Version",
                    "description", "Version of the workflow (or 'latest')",
                    "default", "latest"
                ),
                "waitForCompletion", Map.of(
                    "type", "boolean",
                    "title", "Wait for Completion",
                    "description", "Wait for sub-workflow to complete before continuing",
                    "default", true
                ),
                "timeoutSeconds", Map.of(
                    "type", "integer",
                    "title", "Timeout (seconds)",
                    "description", "Maximum wait time for sub-workflow completion",
                    "default", 300
                ),
                "inputMapping", Map.of(
                    "type", "object",
                    "title", "Input Mapping",
                    "description", "Map input fields to sub-workflow parameters"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
