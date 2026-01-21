package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.dto.NodeExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.expression.N3nExpressionEvaluator;
import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.flow.service.DagParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service("executionService")
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final DagParser dagParser;
    private final StateManager stateManager;
    private final ExecutionNotificationService notificationService;
    private final NodeHandlerRegistry handlerRegistry;
    private final N3nExpressionEvaluator expressionEvaluator;

    public Page<ExecutionResponse> listExecutions(Pageable pageable) {
        return executionRepository.findAllByOrderByStartedAtDesc(pageable)
            .map(e -> enrichExecution(e));
    }

    public Page<ExecutionResponse> listExecutionsByFlow(UUID flowId, Pageable pageable) {
        // Find all versions of the flow, then find executions for those versions
        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId);
        if (versions.isEmpty()) {
            return Page.empty(pageable);
        }

        // For simplicity, get the published version's executions
        FlowVersion published = versions.stream()
            .filter(v -> "published".equals(v.getStatus()))
            .findFirst()
            .orElse(versions.get(0));

        return executionRepository.findByFlowVersionIdOrderByStartedAtDesc(published.getId(), pageable)
            .map(e -> enrichExecution(e));
    }

    public ExecutionResponse getExecution(UUID id) {
        Execution execution = executionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id));
        return enrichExecution(execution);
    }

    public List<NodeExecutionResponse> getNodeExecutions(UUID executionId) {
        if (!executionRepository.existsById(executionId)) {
            throw new ResourceNotFoundException("Execution not found: " + executionId);
        }
        return nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId)
            .stream()
            .map(NodeExecutionResponse::from)
            .toList();
    }

    @Transactional
    public ExecutionResponse createExecution(CreateExecutionRequest request, UUID userId) {
        // Find flow and version
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(request.getFlowId())
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + request.getFlowId()));

        FlowVersion version;
        if (request.getVersion() != null) {
            version = flowVersionRepository.findByFlowIdAndVersion(flow.getId(), request.getVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + request.getVersion()));
        } else {
            // Use published version
            version = flowVersionRepository.findByFlowIdAndStatus(flow.getId(), "published")
                .orElseThrow(() -> new IllegalArgumentException("No published version available for flow: " + flow.getName()));
        }

        // Validate DAG
        DagParser.ParseResult parseResult = dagParser.parse(version.getDefinition());
        if (!parseResult.isValid()) {
            throw new IllegalArgumentException("Invalid flow definition: " + String.join(", ", parseResult.getErrors()));
        }

        // Create execution
        UUID executionId = UUID.randomUUID();
        Execution execution = Execution.builder()
            .id(executionId)
            .flowVersionId(version.getId())
            .status("pending")
            .triggerInput(request.getInput())
            .triggerContext(request.getContext())
            .triggeredBy(userId)
            .triggerType("manual")
            .build();

        execution = executionRepository.save(execution);
        log.info("Execution created: id={}, flowId={}, version={}", executionId, flow.getId(), version.getVersion());

        // Initialize state in Redis
        stateManager.initExecution(executionId, version.getDefinition());

        // Start async execution
        runExecutionAsync(executionId);

        return enrichExecution(execution);
    }

    @Transactional
    public ExecutionResponse cancelExecution(UUID id, UUID userId, String reason) {
        Execution execution = executionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id));

        if (!"running".equals(execution.getStatus()) && !"pending".equals(execution.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel execution in status: " + execution.getStatus());
        }

        execution.setStatus("cancelled");
        execution.setCancelledBy(userId);
        execution.setCancelledAt(Instant.now());
        execution.setCancelReason(reason);

        if (execution.getStartedAt() != null) {
            execution.setCompletedAt(Instant.now());
            execution.setDurationMs((int) (execution.getCompletedAt().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
        }

        execution = executionRepository.save(execution);
        stateManager.updateExecutionStatus(id, "cancelled");
        notificationService.notifyExecutionCancelled(id, reason);
        log.info("Execution cancelled: id={}, reason={}", id, reason);

        return enrichExecution(execution);
    }

    public Map<String, Object> getExecutionOutput(UUID executionId) {
        if (!executionRepository.existsById(executionId)) {
            throw new ResourceNotFoundException("Execution not found: " + executionId);
        }
        return stateManager.getExecutionOutput(executionId);
    }

    @Transactional
    public ExecutionResponse retryExecution(UUID originalExecutionId, UUID userId) {
        Execution original = executionRepository.findById(originalExecutionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + originalExecutionId));

        // Can only retry failed or cancelled executions
        if (!"failed".equals(original.getStatus()) && !"cancelled".equals(original.getStatus())) {
            throw new IllegalArgumentException("Can only retry failed or cancelled executions. Current status: " + original.getStatus());
        }

        // Check retry count
        int currentRetryCount = original.getRetryCount() != null ? original.getRetryCount() : 0;
        int maxRetries = original.getMaxRetries() != null ? original.getMaxRetries() : 3;
        if (currentRetryCount >= maxRetries) {
            throw new IllegalArgumentException("Maximum retry count reached: " + maxRetries);
        }

        // Create new execution as retry
        UUID newExecutionId = UUID.randomUUID();
        Execution retry = Execution.builder()
            .id(newExecutionId)
            .flowVersionId(original.getFlowVersionId())
            .status("pending")
            .triggerInput(original.getTriggerInput())
            .triggerContext(original.getTriggerContext())
            .triggeredBy(userId)
            .triggerType("retry")
            .retryOf(originalExecutionId)
            .retryCount(currentRetryCount + 1)
            .maxRetries(maxRetries)
            .build();

        retry = executionRepository.save(retry);
        log.info("Retry execution created: id={}, retryOf={}, retryCount={}",
            newExecutionId, originalExecutionId, retry.getRetryCount());

        // Get flow version for initialization
        FlowVersion version = flowVersionRepository.findById(retry.getFlowVersionId())
            .orElseThrow(() -> new ResourceNotFoundException("Flow version not found"));

        // Initialize state in Redis
        stateManager.initExecution(newExecutionId, version.getDefinition());

        // Start async execution
        runExecutionAsync(newExecutionId);

        return enrichExecution(retry);
    }

    @Async
    public void runExecutionAsync(UUID executionId) {
        try {
            runExecution(executionId);
        } catch (Exception e) {
            log.error("Execution failed: {}", executionId, e);
            updateExecutionFailed(executionId, e.getMessage());
        }
    }

    @Transactional
    public void runExecution(UUID executionId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));

        FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId())
            .orElseThrow(() -> new ResourceNotFoundException("Flow version not found"));

        // Update status to running
        execution.setStatus("running");
        execution.setStartedAt(Instant.now());
        executionRepository.save(execution);
        stateManager.updateExecutionStatus(executionId, "running");
        notificationService.notifyExecutionStarted(executionId, version.getId());

        // Parse the flow definition
        DagParser.ParseResult parseResult = dagParser.parse(version.getDefinition());
        List<String> executionOrder = parseResult.getExecutionOrder();
        Map<String, Set<String>> dependencies = parseResult.getDependencies();

        // Execute nodes in order
        Map<String, Object> context = new HashMap<>();
        if (execution.getTriggerInput() != null) {
            context.put("input", execution.getTriggerInput());
        }
        if (execution.getTriggerContext() != null) {
            context.putAll(execution.getTriggerContext());
        }

        Map<String, Object> nodeOutputs = new HashMap<>();

        for (String nodeId : executionOrder) {
            // Check if execution was cancelled
            String currentStatus = stateManager.getExecutionStatus(executionId);
            if ("cancelled".equals(currentStatus)) {
                log.info("Execution cancelled, stopping: {}", executionId);
                return;
            }

            try {
                Map<String, Object> nodeOutput = executeNode(executionId, nodeId, version.getDefinition(), context, nodeOutputs);
                nodeOutputs.put(nodeId, nodeOutput);
                context.put(nodeId, nodeOutput);
            } catch (Exception e) {
                log.error("Node execution failed: executionId={}, nodeId={}", executionId, nodeId, e);
                updateExecutionFailed(executionId, "Node " + nodeId + " failed: " + e.getMessage());
                return;
            }
        }

        // Mark execution as completed
        execution = executionRepository.findById(executionId).orElseThrow();
        execution.setStatus("completed");
        execution.setCompletedAt(Instant.now());
        execution.setDurationMs((int) (execution.getCompletedAt().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
        executionRepository.save(execution);

        stateManager.updateExecutionStatus(executionId, "completed");
        stateManager.setExecutionOutput(executionId, nodeOutputs);
        notificationService.notifyExecutionCompleted(executionId, nodeOutputs);

        log.info("Execution completed: id={}, duration={}ms", executionId, execution.getDurationMs());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeNode(UUID executionId, String nodeId, Map<String, Object> definition,
                                             Map<String, Object> context, Map<String, Object> nodeOutputs) {
        // Find node in definition
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        Map<String, Object> nodeData = nodes.stream()
            .filter(n -> nodeId.equals(n.get("id")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Node not found: " + nodeId));

        String nodeType = (String) nodeData.get("type");
        Map<String, Object> data = (Map<String, Object>) nodeData.getOrDefault("data", Map.of());

        // Create node execution record
        NodeExecution nodeExecution = NodeExecution.builder()
            .executionId(executionId)
            .nodeId(nodeId)
            .componentName(nodeType != null ? nodeType : "action")
            .componentVersion("1.0.0")
            .status("running")
            .startedAt(Instant.now())
            .build();
        nodeExecution = nodeExecutionRepository.save(nodeExecution);
        stateManager.markNodeStarted(executionId, nodeId);
        notificationService.notifyNodeStarted(executionId, nodeId);

        try {
            // Execute using handler registry
            Map<String, Object> output = executeNodeWithHandler(
                executionId, nodeId, nodeType, data, context, nodeOutputs);

            // Update node execution
            nodeExecution.setStatus("completed");
            nodeExecution.setCompletedAt(Instant.now());
            nodeExecution.setDurationMs((int) (nodeExecution.getCompletedAt().toEpochMilli() - nodeExecution.getStartedAt().toEpochMilli()));
            nodeExecutionRepository.save(nodeExecution);

            stateManager.markNodeCompleted(executionId, nodeId, output);
            notificationService.notifyNodeCompleted(executionId, nodeId, output);

            return output;
        } catch (Exception e) {
            nodeExecution.setStatus("failed");
            nodeExecution.setCompletedAt(Instant.now());
            nodeExecution.setDurationMs((int) (nodeExecution.getCompletedAt().toEpochMilli() - nodeExecution.getStartedAt().toEpochMilli()));
            nodeExecution.setErrorMessage(e.getMessage());
            nodeExecution.setErrorStack(e.getClass().getName());
            nodeExecutionRepository.save(nodeExecution);

            stateManager.markNodeFailed(executionId, nodeId, e.getMessage());
            notificationService.notifyNodeFailed(executionId, nodeId, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeNodeWithHandler(UUID executionId, String nodeId, String nodeType,
                                                        Map<String, Object> data, Map<String, Object> context,
                                                        Map<String, Object> nodeOutputs) {
        // Normalize node type
        String handlerType = normalizeNodeType(nodeType);

        // Get handler from registry
        NodeHandler handler;
        if (handlerRegistry.hasHandler(handlerType)) {
            handler = handlerRegistry.getHandler(handlerType);
        } else {
            // Fall back to action handler for unknown types
            log.debug("No handler for type '{}', using action handler", nodeType);
            handler = handlerRegistry.getHandler("action");
        }

        // Build execution context
        Map<String, Object> inputData = buildInputData(nodeId, context, nodeOutputs);
        Map<String, Object> nodeConfig = data != null ? new HashMap<>(data) : new HashMap<>();

        // Get flow info from execution
        Execution execution = executionRepository.findById(executionId).orElse(null);
        UUID flowId = null;
        String flowVersion = "1";
        UUID userId = null;

        if (execution != null) {
            FlowVersion fv = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
            if (fv != null) {
                flowId = fv.getFlowId();
                flowVersion = fv.getVersion();
            }
            userId = execution.getTriggeredBy();
        }

        NodeExecutionContext execContext = NodeExecutionContext.builder()
            .executionId(executionId)
            .nodeId(nodeId)
            .nodeType(handlerType)
            .nodeConfig(nodeConfig)
            .inputData(inputData)
            .globalContext(context)
            .previousOutputs(nodeOutputs)
            .flowId(flowId)
            .flowVersion(flowVersion)
            .userId(userId)
            .expressionEvaluator(expressionEvaluator)
            .build();

        // Evaluate expressions in node config
        Map<String, Object> evaluatedConfig = expressionEvaluator.evaluateConfig(nodeConfig, execContext);
        execContext = NodeExecutionContext.builder()
            .executionId(executionId)
            .nodeId(nodeId)
            .nodeType(handlerType)
            .nodeConfig(evaluatedConfig)
            .inputData(inputData)
            .globalContext(context)
            .previousOutputs(nodeOutputs)
            .flowId(flowId)
            .flowVersion(flowVersion)
            .userId(userId)
            .expressionEvaluator(expressionEvaluator)
            .build();

        // Execute handler
        log.debug("Executing node {} with handler {}", nodeId, handler.getType());
        NodeExecutionResult result = handler.execute(execContext);

        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage() != null ?
                result.getErrorMessage() : "Node execution failed");
        }

        return result.getOutput() != null ? result.getOutput() : new HashMap<>();
    }

    private String normalizeNodeType(String nodeType) {
        if (nodeType == null || nodeType.isEmpty()) {
            return "action";
        }

        // Map common aliases
        return switch (nodeType.toLowerCase()) {
            case "input", "start" -> "trigger";
            case "end" -> "output";
            case "if", "switch", "branch" -> "condition";
            case "foreach", "iterate" -> "loop";
            case "http", "api", "request" -> "httpRequest";
            case "script", "js", "javascript" -> "code";
            case "cron", "schedule" -> "scheduleTrigger";
            case "delay", "sleep" -> "wait";
            default -> nodeType.toLowerCase();
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInputData(String nodeId, Map<String, Object> context,
                                                Map<String, Object> nodeOutputs) {
        Map<String, Object> inputData = new HashMap<>();

        // Add trigger input if available
        if (context.containsKey("input")) {
            Object input = context.get("input");
            if (input instanceof Map) {
                inputData.putAll((Map<String, Object>) input);
            } else {
                inputData.put("data", input);
            }
        }

        // Find the most recent output to use as input
        // This is a simplified approach - a real implementation would follow edges
        if (!nodeOutputs.isEmpty()) {
            // Get the last node's output as the default input
            String lastNodeId = null;
            for (String key : nodeOutputs.keySet()) {
                lastNodeId = key;
            }
            if (lastNodeId != null) {
                Object lastOutput = nodeOutputs.get(lastNodeId);
                if (lastOutput instanceof Map) {
                    inputData.putAll((Map<String, Object>) lastOutput);
                }
            }
        }

        return inputData;
    }

    /**
     * Start execution from scheduler or external trigger.
     * This method is called by WorkflowExecutionJob.
     */
    @Transactional
    public ExecutionResponse startExecution(UUID flowId, UUID userId, Map<String, Object> triggerData) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        FlowVersion version = flowVersionRepository.findByFlowIdAndStatus(flow.getId(), "published")
            .orElseThrow(() -> new IllegalArgumentException("No published version for flow: " + flow.getName()));

        CreateExecutionRequest request = new CreateExecutionRequest();
        request.setFlowId(flowId);
        request.setInput(triggerData);

        return createExecution(request, userId);
    }

    private void updateExecutionFailed(UUID executionId, String errorMessage) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus("failed");
            execution.setCompletedAt(Instant.now());
            if (execution.getStartedAt() != null) {
                execution.setDurationMs((int) (execution.getCompletedAt().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
            }
            executionRepository.save(execution);
        });
        stateManager.updateExecutionStatus(executionId, "failed");
        notificationService.notifyExecutionFailed(executionId, errorMessage);
    }

    private ExecutionResponse enrichExecution(Execution execution) {
        FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
        if (version != null) {
            Flow flow = flowRepository.findById(version.getFlowId()).orElse(null);
            if (flow != null) {
                return ExecutionResponse.from(execution, flow.getName(), version.getVersion());
            }
        }
        return ExecutionResponse.from(execution);
    }
}
