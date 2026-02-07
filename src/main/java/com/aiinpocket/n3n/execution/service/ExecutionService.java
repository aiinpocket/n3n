package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.common.logging.LogContext;
import com.aiinpocket.n3n.credential.service.CredentialService;
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
import com.aiinpocket.n3n.flow.service.FlowShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    private final CredentialService credentialService;
    private final ActivityService activityService;
    private final FlowShareService flowShareService;

    public Page<ExecutionResponse> listExecutions(UUID userId, Pageable pageable) {
        return executionRepository.findByTriggeredByOrderByStartedAtDesc(userId, pageable)
            .map(e -> enrichExecution(e));
    }

    public Page<ExecutionResponse> listExecutions(UUID userId, Pageable pageable, String status, String search) {
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        Page<Execution> page;
        if (hasStatus && hasSearch) {
            page = executionRepository.findByUserAndStatusAndFlowNameContaining(userId, status, search, pageable);
        } else if (hasStatus) {
            page = executionRepository.findByTriggeredByAndStatusOrderByStartedAtDesc(userId, status, pageable);
        } else if (hasSearch) {
            page = executionRepository.findByUserAndFlowNameContaining(userId, search, pageable);
        } else {
            page = executionRepository.findByTriggeredByOrderByStartedAtDesc(userId, pageable);
        }
        return page.map(e -> enrichExecution(e));
    }

    public Page<ExecutionResponse> listExecutionsByFlow(UUID flowId, UUID userId, Pageable pageable) {
        // Verify user has access to this flow
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to flow: " + flowId);
        }
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

    private Execution findExecutionWithOwnerCheck(UUID id, UUID userId) {
        Execution execution = executionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id));
        if (!execution.getTriggeredBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return execution;
    }

    public ExecutionResponse getExecution(UUID id, UUID userId) {
        Execution execution = findExecutionWithOwnerCheck(id, userId);
        return enrichExecution(execution);
    }

    @Transactional
    public void deleteExecution(UUID id, UUID userId) {
        Execution execution = findExecutionWithOwnerCheck(id, userId);
        nodeExecutionRepository.deleteByExecutionId(id);
        executionRepository.delete(execution);
    }

    public Map<String, Object> getNodeData(UUID executionId, String nodeId, UUID userId) {
        findExecutionWithOwnerCheck(executionId, userId);
        Map<String, Object> output = stateManager.getNodeOutput(executionId, nodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("input", Map.of());  // Input is derived from upstream outputs
        result.put("output", output != null ? output : Map.of());
        return result;
    }

    public List<NodeExecutionResponse> getNodeExecutions(UUID executionId, UUID userId) {
        findExecutionWithOwnerCheck(executionId, userId);
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

        // Verify user has at least view access to execute (edit access for non-owner)
        if (!flowShareService.hasAccess(flow.getId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to flow: " + flow.getId());
        }

        FlowVersion version;
        if (request.getVersion() != null) {
            version = flowVersionRepository.findByFlowIdAndVersion(flow.getId(), request.getVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + request.getVersion()));
        } else {
            // Use published version
            // Try published version first, then latest version
            version = flowVersionRepository.findByFlowIdAndStatus(flow.getId(), "published")
                .orElseGet(() -> {
                    List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flow.getId());
                    if (versions.isEmpty()) {
                        throw new ResourceNotFoundException("No version available for flow: " + flow.getName());
                    }
                    return versions.get(0);
                });
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

        // Set logging context
        LogContext.setExecutionContext(executionId, flow.getId(), null);
        LogContext.setUserContext(userId);
        log.info("EXECUTION_CREATED flowVersion={}", version.getVersion());

        // Log activity
        activityService.logExecutionStart(userId, executionId, flow.getId(), flow.getName(), "manual");

        // Initialize state in Redis
        stateManager.initExecution(executionId, version.getDefinition());

        // Start async execution
        runExecutionAsync(executionId);

        return enrichExecution(execution);
    }

    @Transactional
    public ExecutionResponse pauseExecution(UUID id, UUID userId, String reason) {
        Execution execution = findExecutionWithOwnerCheck(id, userId);

        if (!"running".equals(execution.getStatus())) {
            throw new IllegalArgumentException("Cannot pause execution in status: " + execution.getStatus());
        }

        execution.setStatus("waiting");
        execution.setPausedAt(Instant.now());
        execution.setPauseReason(reason != null ? reason : "Manually paused by user");
        execution = executionRepository.save(execution);

        stateManager.updateExecutionStatus(id, "waiting");
        notificationService.notifyExecutionWaiting(id, null, execution.getPauseReason(), null);

        LogContext.setExecutionContext(id, null, null);
        log.info("EXECUTION_PAUSED reason={}", reason);
        LogContext.clearExecutionContext();

        activityService.logExecutionPause(userId, id, null, execution.getPauseReason());

        return enrichExecution(execution);
    }

    @Transactional
    public ExecutionResponse cancelExecution(UUID id, UUID userId, String reason) {
        Execution execution = findExecutionWithOwnerCheck(id, userId);

        if (!"running".equals(execution.getStatus()) && !"pending".equals(execution.getStatus()) && !"waiting".equals(execution.getStatus())) {
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
        LogContext.setExecutionContext(id, null, null);
        log.info("EXECUTION_CANCELLED reason={}", reason);
        LogContext.clearExecutionContext();

        // Log activity
        activityService.logExecutionCancel(userId, id, reason);

        return enrichExecution(execution);
    }

    public Map<String, Object> getExecutionOutput(UUID executionId, UUID userId) {
        findExecutionWithOwnerCheck(executionId, userId);
        return stateManager.getExecutionOutput(executionId);
    }

    @Transactional
    public ExecutionResponse retryExecution(UUID originalExecutionId, UUID userId) {
        Execution original = findExecutionWithOwnerCheck(originalExecutionId, userId);

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
        LogContext.setExecutionContext(newExecutionId, null, null);
        log.info("EXECUTION_RETRY retryOf={} retryCount={}", originalExecutionId, retry.getRetryCount());

        // Log activity
        activityService.logExecutionRetry(userId, newExecutionId, originalExecutionId, retry.getRetryCount());

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
            LogContext.setExecutionContext(executionId, null, null);
            runExecution(executionId);
        } catch (Exception e) {
            log.error("EXECUTION_FAILED error={}", e.getMessage(), e);
            updateExecutionFailed(executionId, e.getMessage());
        } finally {
            LogContext.clearExecutionContext();
        }
    }

    public void runExecution(UUID executionId) {
        runExecution(executionId, null);
    }

    /**
     * Run or resume execution.
     * Each repository.save() call uses its own implicit transaction.
     * Removing @Transactional to avoid long-running transactions spanning entire flow execution.
     *
     * @param executionId The execution ID
     * @param resumeData Optional resume data when resuming from waiting state
     */
    public void runExecution(UUID executionId, Map<String, Object> resumeData) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));

        FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId())
            .orElseThrow(() -> new ResourceNotFoundException("Flow version not found"));

        boolean isResume = "waiting".equals(execution.getStatus());
        String waitingNodeId = isResume ? execution.getWaitingNodeId() : null;

        // Update status to running
        execution.setStatus("running");
        if (!isResume) {
            execution.setStartedAt(Instant.now());
        }
        // Clear pause-related fields when resuming
        execution.setPausedAt(null);
        execution.setWaitingNodeId(null);
        execution.setPauseReason(null);
        execution.setResumeCondition(null);
        executionRepository.save(execution);
        stateManager.updateExecutionStatus(executionId, "running");

        if (isResume) {
            notificationService.notifyExecutionResumed(executionId, waitingNodeId);
            log.info("EXECUTION_RESUMED fromNode={}", waitingNodeId);
        } else {
            notificationService.notifyExecutionStarted(executionId, version.getId());
            log.info("EXECUTION_STARTED flowVersionId={}", version.getId());
        }

        // Parse the flow definition
        DagParser.ParseResult parseResult = dagParser.parse(version.getDefinition());
        List<String> executionOrder = parseResult.getExecutionOrder();
        log.debug("EXECUTION_ORDER nodes={}", executionOrder);

        // Determine which nodes to skip (already completed)
        Set<String> completedNodes = stateManager.getCompletedNodes(executionId);
        List<String> nodesToExecute;

        if (isResume && waitingNodeId != null) {
            // When resuming, start from the waiting node
            int waitingIndex = executionOrder.indexOf(waitingNodeId);
            if (waitingIndex >= 0) {
                nodesToExecute = executionOrder.subList(waitingIndex, executionOrder.size());
            } else {
                nodesToExecute = executionOrder;
            }
            // Clear the waiting status for this node
            stateManager.clearNodeWaiting(executionId, waitingNodeId);

            // Store resume data if provided
            if (resumeData != null && !resumeData.isEmpty()) {
                stateManager.setResumeData(executionId, waitingNodeId, resumeData);
            }
            log.info("Resume: skipping completed nodes, starting from {}", waitingNodeId);
        } else {
            nodesToExecute = executionOrder.stream()
                .filter(nodeId -> !completedNodes.contains(nodeId))
                .collect(Collectors.toList());
        }

        // Build context from trigger input
        Map<String, Object> context = new HashMap<>();
        if (execution.getTriggerInput() != null) {
            context.put("input", execution.getTriggerInput());
        }
        if (execution.getTriggerContext() != null) {
            context.putAll(execution.getTriggerContext());
        }

        // Restore node outputs from completed nodes (for resume scenarios)
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        if (isResume) {
            // Restore partial outputs from Redis
            Map<String, Object> partialOutputs = stateManager.getPartialNodeOutputs(executionId);
            nodeOutputs.putAll(partialOutputs);
            context.putAll(partialOutputs);
            log.info("Restored {} node outputs from previous execution", partialOutputs.size());
        }

        for (String nodeId : nodesToExecute) {
            // Check if execution was cancelled or should stop
            String currentStatus = stateManager.getExecutionStatus(executionId);
            if ("cancelled".equals(currentStatus) || "waiting".equals(currentStatus)) {
                log.info("Execution stopped (status={}): {}", currentStatus, executionId);
                return;
            }

            try {
                log.info("Executing node: {} (current nodeOutputs keys: {})", nodeId, nodeOutputs.keySet());

                // Check if this is the resume node and has resume data
                Map<String, Object> nodeResumeData = null;
                if (isResume && nodeId.equals(waitingNodeId)) {
                    nodeResumeData = stateManager.getResumeData(executionId, nodeId);
                    if (!nodeResumeData.isEmpty()) {
                        context.put("_resumeData", nodeResumeData);
                        log.info("Injected resume data for node {}", nodeId);
                    }
                }

                ExecuteNodeResult result = executeNodeWithPauseSupport(executionId, nodeId, version.getDefinition(), context, nodeOutputs);

                if (result.isPauseRequested()) {
                    // Handle pause request
                    handleExecutionPause(execution, nodeId, result.getPauseReason(), result.getResumeCondition(), nodeOutputs);
                    return;
                }

                Map<String, Object> nodeOutput = result.getOutput();
                log.info("Node {} completed with output keys: {}", nodeId, nodeOutput != null ? nodeOutput.keySet() : "null");
                nodeOutputs.put(nodeId, nodeOutput);
                context.put(nodeId, nodeOutput);

                // Clear resume data after successful execution
                if (isResume && nodeId.equals(waitingNodeId)) {
                    stateManager.clearResumeData(executionId, nodeId);
                    context.remove("_resumeData");
                }
            } catch (Exception e) {
                log.error("Node execution failed: executionId={}, nodeId={}", executionId, nodeId, e);

                // Check for error handling edges
                Map<String, List<DagParser.FlowEdge>> outgoingEdges =
                    dagParser.getOutgoingEdgesByType(version.getDefinition(), nodeId);
                List<DagParser.FlowEdge> errorEdges = outgoingEdges.get("error");
                List<DagParser.FlowEdge> alwaysEdges = outgoingEdges.get("always");

                boolean hasErrorPath = (errorEdges != null && !errorEdges.isEmpty()) ||
                                       (alwaysEdges != null && !alwaysEdges.isEmpty());

                if (hasErrorPath) {
                    // Store error info in context for error handling nodes
                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("error", true);
                    errorInfo.put("errorMessage", e.getMessage());
                    errorInfo.put("errorType", e.getClass().getName());
                    errorInfo.put("failedNodeId", nodeId);
                    nodeOutputs.put(nodeId, errorInfo);
                    context.put(nodeId, errorInfo);
                    context.put("_lastError", errorInfo);

                    log.info("Node {} failed but has error handling path, continuing with error route", nodeId);

                    // Execute error path nodes
                    Set<String> errorPathNodes = new HashSet<>();
                    if (errorEdges != null) {
                        for (DagParser.FlowEdge edge : errorEdges) {
                            errorPathNodes.add(edge.getTarget());
                        }
                    }
                    if (alwaysEdges != null) {
                        for (DagParser.FlowEdge edge : alwaysEdges) {
                            errorPathNodes.add(edge.getTarget());
                        }
                    }

                    // Continue execution with error path nodes
                    // Note: This is a simplified implementation that adds error path nodes to continue
                    for (String errorNodeId : errorPathNodes) {
                        if (!completedNodes.contains(errorNodeId)) {
                            try {
                                log.info("Executing error handler node: {}", errorNodeId);
                                ExecuteNodeResult errorResult = executeNodeWithPauseSupport(
                                    executionId, errorNodeId, version.getDefinition(), context, nodeOutputs);

                                if (errorResult.isPauseRequested()) {
                                    handleExecutionPause(execution, errorNodeId,
                                        errorResult.getPauseReason(), errorResult.getResumeCondition(), nodeOutputs);
                                    return;
                                }

                                Map<String, Object> errorNodeOutput = errorResult.getOutput();
                                nodeOutputs.put(errorNodeId, errorNodeOutput);
                                context.put(errorNodeId, errorNodeOutput);
                                completedNodes.add(errorNodeId);
                            } catch (Exception errorEx) {
                                log.error("Error handler node {} also failed: {}", errorNodeId, errorEx.getMessage());
                            }
                        }
                    }
                    // Continue to next node in the main flow (skip failed node's success path)
                    continue;
                } else {
                    // No error handling path, fail the execution
                    updateExecutionFailed(executionId, "Node " + nodeId + " failed: " + e.getMessage());
                    return;
                }
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

        // Log activity
        Flow flow = flowRepository.findById(version.getFlowId()).orElse(null);
        UUID flowId = flow != null ? flow.getId() : version.getFlowId();
        activityService.logExecutionComplete(execution.getTriggeredBy(), executionId, flowId, execution.getDurationMs());

        log.info("Execution completed: id={}, duration={}ms", executionId, execution.getDurationMs());
    }

    /**
     * Handle execution pause request from a node.
     */
    private void handleExecutionPause(Execution execution, String nodeId, String pauseReason,
                                       Map<String, Object> resumeCondition, Map<String, Object> nodeOutputs) {
        UUID executionId = execution.getId();

        // Update execution to waiting status
        execution.setStatus("waiting");
        execution.setPausedAt(Instant.now());
        execution.setWaitingNodeId(nodeId);
        execution.setPauseReason(pauseReason);
        execution.setResumeCondition(resumeCondition);
        executionRepository.save(execution);

        // Update Redis state
        stateManager.updateExecutionStatus(executionId, "waiting");
        stateManager.markNodeWaiting(executionId, nodeId, pauseReason);
        stateManager.setPartialNodeOutputs(executionId, nodeOutputs);

        // Send notification
        notificationService.notifyExecutionWaiting(executionId, nodeId, pauseReason, resumeCondition);

        // Log activity
        activityService.logExecutionPause(execution.getTriggeredBy(), executionId, nodeId, pauseReason);

        log.info("Execution paused: id={}, node={}, reason={}", executionId, nodeId, pauseReason);
    }

    /**
     * Resume a waiting execution with provided data.
     */
    @Transactional
    public ExecutionResponse resumeExecution(UUID executionId, Map<String, Object> resumeData, UUID userId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));

        if (!"waiting".equals(execution.getStatus())) {
            throw new IllegalArgumentException("Cannot resume execution in status: " + execution.getStatus());
        }

        log.info("Resuming execution: id={}, waitingNode={}, userId={}", executionId, execution.getWaitingNodeId(), userId);

        // Log activity
        activityService.logExecutionResume(userId, executionId, execution.getWaitingNodeId());

        // Start async resume
        runExecutionAsync(executionId, resumeData);

        return enrichExecution(execution);
    }

    @Async
    public void runExecutionAsync(UUID executionId, Map<String, Object> resumeData) {
        try {
            runExecution(executionId, resumeData);
        } catch (Exception e) {
            log.error("Execution failed: {}", executionId, e);
            updateExecutionFailed(executionId, e.getMessage());
        }
    }

    /**
     * Internal result class for node execution with pause support.
     */
    private static class ExecuteNodeResult {
        private final Map<String, Object> output;
        private final boolean pauseRequested;
        private final String pauseReason;
        private final Map<String, Object> resumeCondition;

        private ExecuteNodeResult(Map<String, Object> output, boolean pauseRequested,
                                  String pauseReason, Map<String, Object> resumeCondition) {
            this.output = output;
            this.pauseRequested = pauseRequested;
            this.pauseReason = pauseReason;
            this.resumeCondition = resumeCondition;
        }

        static ExecuteNodeResult success(Map<String, Object> output) {
            return new ExecuteNodeResult(output, false, null, null);
        }

        static ExecuteNodeResult pause(String reason, Map<String, Object> condition, Map<String, Object> partialOutput) {
            return new ExecuteNodeResult(partialOutput, true, reason, condition);
        }

        Map<String, Object> getOutput() { return output; }
        boolean isPauseRequested() { return pauseRequested; }
        String getPauseReason() { return pauseReason; }
        Map<String, Object> getResumeCondition() { return resumeCondition; }
    }

    /**
     * Execute a node with pause support.
     * Returns an ExecuteNodeResult that may indicate a pause request.
     */
    @SuppressWarnings("unchecked")
    private ExecuteNodeResult executeNodeWithPauseSupport(UUID executionId, String nodeId, Map<String, Object> definition,
                                                           Map<String, Object> context, Map<String, Object> nodeOutputs) {
        // Find node in definition
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        Map<String, Object> nodeData = nodes.stream()
            .filter(n -> nodeId.equals(n.get("id")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Node not found: " + nodeId));

        String nodeType = (String) nodeData.get("type");
        Map<String, Object> data = (Map<String, Object>) nodeData.getOrDefault("data", Map.of());

        // Check for pinned data - if present, use it instead of executing the node
        Execution execution = executionRepository.findById(executionId).orElse(null);
        if (execution != null) {
            FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
            if (version != null && version.getPinnedData() != null && !version.getPinnedData().isEmpty()) {
                Object pinnedOutput = version.getPinnedData().get(nodeId);
                if (pinnedOutput != null) {
                    log.info("Using pinned data for node {} instead of executing", nodeId);
                    // Mark node as completed with pinned data
                    NodeExecution pinnedNodeExecution = NodeExecution.builder()
                        .executionId(executionId)
                        .nodeId(nodeId)
                        .componentName(nodeType != null ? nodeType : "action")
                        .componentVersion("1.0.0")
                        .status("completed")
                        .startedAt(Instant.now())
                        .completedAt(Instant.now())
                        .durationMs(0)
                        .build();
                    nodeExecutionRepository.save(pinnedNodeExecution);
                    stateManager.markNodeStarted(executionId, nodeId);
                    notificationService.notifyNodeStarted(executionId, nodeId);

                    Map<String, Object> pinnedDataOutput = pinnedOutput instanceof Map
                        ? (Map<String, Object>) pinnedOutput
                        : Map.of("data", pinnedOutput);
                    stateManager.markNodeCompleted(executionId, nodeId, pinnedDataOutput);
                    notificationService.notifyNodeCompleted(executionId, nodeId, pinnedDataOutput);
                    return ExecuteNodeResult.success(pinnedDataOutput);
                }
            }
        }

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
            NodeExecutionResult result = executeNodeHandler(
                executionId, nodeId, nodeType, data, context, nodeOutputs);

            // Check for pause request
            if (result.isPauseRequested()) {
                // Update node to waiting status
                nodeExecution.setStatus("waiting");
                nodeExecutionRepository.save(nodeExecution);
                stateManager.markNodeWaiting(executionId, nodeId, result.getPauseReason());
                notificationService.notifyNodeWaiting(executionId, nodeId, result.getPauseReason());
                return ExecuteNodeResult.pause(result.getPauseReason(), result.getResumeCondition(), result.getOutput());
            }

            // Update node execution as completed
            nodeExecution.setStatus("completed");
            nodeExecution.setCompletedAt(Instant.now());
            nodeExecution.setDurationMs((int) (nodeExecution.getCompletedAt().toEpochMilli() - nodeExecution.getStartedAt().toEpochMilli()));
            nodeExecutionRepository.save(nodeExecution);

            Map<String, Object> output = result.getOutput() != null ? result.getOutput() : new HashMap<>();
            stateManager.markNodeCompleted(executionId, nodeId, output);
            notificationService.notifyNodeCompleted(executionId, nodeId, output);

            return ExecuteNodeResult.success(output);
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
    private NodeExecutionResult executeNodeHandler(UUID executionId, String nodeId, String nodeType,
                                                    Map<String, Object> data, Map<String, Object> context,
                                                    Map<String, Object> nodeOutputs) {
        // Normalize node type
        String handlerType = normalizeNodeType(nodeType);
        log.info("Node {} has type '{}', normalized to '{}'", nodeId, nodeType, handlerType);

        // Get handler from registry
        NodeHandler handler;
        if (handlerRegistry.hasHandler(handlerType)) {
            handler = handlerRegistry.getHandler(handlerType);
            log.info("Using handler: {} for node {}", handler.getType(), nodeId);
        } else {
            // Fall back to action handler for unknown types
            log.warn("No handler for type '{}' (original: '{}'), using action handler", handlerType, nodeType);
            handler = handlerRegistry.getHandler("action");
        }

        // Build execution context
        Map<String, Object> inputData = buildInputData(nodeId, context, nodeOutputs);
        // Extract config from data.config (React Flow node structure)
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeConfig = data != null && data.get("config") instanceof Map
            ? new HashMap<>((Map<String, Object>) data.get("config"))
            : (data != null ? new HashMap<>(data) : new HashMap<>());

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

        // Create credential resolver using CredentialService
        final UUID finalUserId = userId;
        CredentialResolver credentialResolver = new CredentialResolver() {
            @Override
            public Map<String, Object> resolve(UUID credentialId, UUID uid) {
                return credentialService.getDecryptedData(credentialId, uid);
            }

            @Override
            public boolean canAccess(UUID credentialId, UUID uid) {
                try {
                    credentialService.getDecryptedData(credentialId, uid);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };

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
            .credentialResolver(credentialResolver)
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
            .credentialResolver(credentialResolver)
            .build();

        // Execute handler
        log.debug("Executing node {} with handler {}", nodeId, handler.getType());
        NodeExecutionResult result = handler.execute(execContext);

        if (!result.isSuccess() && !result.isPauseRequested()) {
            throw new RuntimeException(result.getErrorMessage() != null ?
                result.getErrorMessage() : "Node execution failed");
        }

        return result;
    }

    private String normalizeNodeType(String nodeType) {
        if (nodeType == null || nodeType.isEmpty()) {
            return "action";
        }

        // Map common aliases (return exact handler names as registered)
        return switch (nodeType.toLowerCase()) {
            case "input", "start" -> "trigger";
            case "end" -> "output";
            case "if", "branch" -> "condition";
            case "switch" -> "switch";  // Multi-way switch node
            case "foreach", "iterate" -> "loop";
            case "http", "api", "request", "httprequest" -> "httpRequest";
            case "script", "js", "javascript" -> "code";
            case "cron", "schedule", "scheduletrigger" -> "scheduleTrigger";
            case "delay", "sleep" -> "wait";
            case "webhooktrigger", "webhook" -> "webhookTrigger";
            case "formtrigger" -> "formTrigger";
            case "approval", "waitforapproval" -> "approval";
            case "ssh", "sshcommand", "remotecommand" -> "ssh";
            default -> nodeType;  // Return original case for registered handlers
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

            // Log activity
            FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
            UUID flowId = version != null ? version.getFlowId() : null;
            activityService.logExecutionFail(execution.getTriggeredBy(), executionId, flowId, errorMessage);
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
