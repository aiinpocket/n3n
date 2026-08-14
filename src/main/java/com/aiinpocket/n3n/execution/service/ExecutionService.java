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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final com.aiinpocket.n3n.artifact.service.ArtifactService artifactService;

    @Value("${execution.max-concurrent:100}")
    private int maxConcurrentExecutions;

    @Value("${execution.max-concurrent-per-user:10}")
    private int maxConcurrentPerUser;

    @Value("${execution.timeout-ms:300000}")
    private long executionTimeoutMs;

    @Transactional(readOnly = true)
    public Page<ExecutionResponse> listExecutions(UUID userId, Pageable pageable) {
        Page<Execution> page = executionRepository.findByTriggeredByOrderByStartedAtDesc(userId, pageable);
        return enrichExecutionPage(page);
    }

    @Transactional(readOnly = true)
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
        return enrichExecutionPage(page);
    }

    @Transactional(readOnly = true)
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

        // Flow owner sees all executions; shared users only see their own
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId).orElse(null);
        Page<Execution> page;
        if (flow != null && flow.getCreatedBy().equals(userId)) {
            page = executionRepository.findByFlowVersionIdOrderByStartedAtDesc(published.getId(), pageable);
        } else {
            page = executionRepository.findByFlowVersionIdAndTriggeredByOrderByStartedAtDesc(published.getId(), userId, pageable);
        }
        return enrichExecutionPage(page);
    }

    private Execution findExecutionWithOwnerCheck(UUID id, UUID userId) {
        Execution execution = executionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id));
        if (!execution.getTriggeredBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return execution;
    }

    @Transactional(readOnly = true)
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

    /**
     * 設為永久 / 取消永久：pinned 的執行（連同其 artifacts）不受保留天數清理。
     */
    @Transactional
    public ExecutionResponse setPinned(UUID id, UUID userId, boolean pinned) {
        Execution execution = findExecutionWithOwnerCheck(id, userId);
        execution.setPinned(pinned);
        executionRepository.save(execution);
        artifactService.setPinnedByExecution(id, pinned);
        return enrichExecution(execution);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNodeData(UUID executionId, String nodeId, UUID userId) {
        findExecutionWithOwnerCheck(executionId, userId);
        Map<String, Object> output = stateManager.getNodeOutput(executionId, nodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("input", Map.of());  // Input is derived from upstream outputs
        result.put("output", output != null ? output : Map.of());
        return result;
    }

    @Transactional(readOnly = true)
    public List<NodeExecutionResponse> getNodeExecutions(UUID executionId, UUID userId) {
        findExecutionWithOwnerCheck(executionId, userId);
        return nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId)
            .stream()
            .map(NodeExecutionResponse::from)
            .toList();
    }

    private static final String DEDUP_MESSAGE = "An execution for this flow is already being created. Please wait a moment.";

    @Transactional
    public ExecutionResponse createExecution(CreateExecutionRequest request, UUID userId) {
        // Dedup: prevent rapid double-execution (5 second window per flow+user)
        String dedupKey = "execution:dedup:" + request.getFlowId() + ":" + userId;
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", 5, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                throw new IllegalStateException(DEDUP_MESSAGE);
            }
        } catch (IllegalStateException e) {
            throw e; // Re-throw our own dedup exception
        } catch (Exception e) {
            log.warn("Redis dedup check failed, proceeding without dedup: {}", e.getMessage());
            // If Redis is down, skip dedup rather than blocking all executions
        }

        try {
            return doCreateExecution(request, userId);
        } catch (Exception e) {
            // Clean up dedup key so user can retry immediately (unless it's a dedup collision)
            if (!(e instanceof IllegalStateException && DEDUP_MESSAGE.equals(e.getMessage()))) {
                try {
                    stringRedisTemplate.delete(dedupKey);
                } catch (Exception ignored) {
                    // Redis cleanup is best-effort
                }
            }
            throw e;
        }
    }

    private ExecutionResponse doCreateExecution(CreateExecutionRequest request, UUID userId) {
        // Find flow and version
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(request.getFlowId())
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + request.getFlowId()));

        // Verify user has edit access to execute (VIEW-only users cannot trigger executions)
        if (!flowShareService.hasEditAccess(flow.getId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to flow: " + flow.getId());
        }

        FlowVersion version;
        if (request.getVersion() != null) {
            version = flowVersionRepository.findByFlowIdAndVersion(flow.getId(), request.getVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + request.getVersion()));
            // Allow both published and draft versions when explicitly specified (for testing)
            if ("deprecated".equals(version.getStatus())) {
                throw new IllegalArgumentException(
                    "Version " + request.getVersion() + " is deprecated and cannot be executed.");
            }
        } else {
            // Default to published version when no specific version requested
            version = flowVersionRepository.findByFlowIdAndStatus(flow.getId(), "published")
                .orElseThrow(() -> new IllegalArgumentException(
                    "No published version available for flow: " + flow.getName()));
        }

        // Validate DAG
        DagParser.ParseResult parseResult = dagParser.parse(version.getDefinition());
        if (!parseResult.isValid()) {
            throw new IllegalArgumentException("Invalid flow definition: " + String.join(", ", parseResult.getErrors()));
        }

        // Check global concurrent execution limit
        long runningCount = executionRepository.countByStatus("running");
        if (runningCount >= maxConcurrentExecutions) {
            throw new IllegalStateException(
                "Maximum concurrent executions (" + maxConcurrentExecutions + ") reached. Please wait for existing executions to complete.");
        }

        // Check per-user concurrent execution limit
        long userRunningCount = executionRepository.countByStatusAndTriggeredBy("running", userId);
        if (userRunningCount >= maxConcurrentPerUser) {
            throw new IllegalStateException(
                "Maximum concurrent executions per user (" + maxConcurrentPerUser + ") reached. Please wait for your existing executions to complete.");
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
        String cancelledFlowName = flowVersionRepository.findById(execution.getFlowVersionId())
            .flatMap(v -> flowRepository.findById(v.getFlowId()))
            .map(Flow::getName)
            .orElse(null);
        activityService.logExecutionCancel(userId, id, cancelledFlowName, reason);

        return enrichExecution(execution);
    }

    @Transactional(readOnly = true)
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
            updateExecutionFailed(executionId, failureReason(e));
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
            // Propagate sub-workflow depth to top-level context for recursion tracking
            if (execution.getTriggerInput().containsKey("_subWorkflowDepth")) {
                context.put("_subWorkflowDepth", execution.getTriggerInput().get("_subWorkflowDepth"));
            }
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

        // Precompute incoming edges per node for branch-aware routing (skip nodes whose
        // branch was not selected by an upstream condition/switch/approval node).
        List<DagParser.FlowEdge> parsedEdges = dagParser.getAllEdges(version.getDefinition());
        final List<DagParser.FlowEdge> allEdges = parsedEdges != null ? parsedEdges : List.of();
        Map<String, List<DagParser.FlowEdge>> incomingByTarget = new HashMap<>();
        for (DagParser.FlowEdge edge : allEdges) {
            incomingByTarget.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
        }

        // Node-outcome bookkeeping. Nodes already completed before a resume count as succeeded.
        Set<String> succeededNodes = new HashSet<>(completedNodes);
        Set<String> failedNodes = new HashSet<>();
        Map<String, List<String>> branchSelections = new HashMap<>();

        for (String nodeId : nodesToExecute) {
            // Check if execution was cancelled or should stop
            String currentStatus = stateManager.getExecutionStatus(executionId);
            if ("cancelled".equals(currentStatus) || "waiting".equals(currentStatus)) {
                log.info("Execution stopped (status={}): {}", currentStatus, executionId);
                return;
            }

            // Branch pruning: skip nodes that no active edge reaches. This is what makes
            // condition/switch/approval branches actually exclusive — without it every branch runs.
            if (!isNodeActive(nodeId, incomingByTarget, succeededNodes, failedNodes, branchSelections)) {
                log.debug("Skipping inactive node (branch not taken): {}", nodeId);
                continue;
            }

            try {
                log.info("Executing node: {} (current nodeOutputs keys: {})", nodeId, nodeOutputs.keySet());

                // Check if this is the resume node and has resume data
                Map<String, Object> nodeResumeData = null;
                if (isResume && nodeId.equals(waitingNodeId)) {
                    nodeResumeData = stateManager.getResumeData(executionId, nodeId);
                    if (nodeResumeData != null && !nodeResumeData.isEmpty()) {
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
                succeededNodes.add(nodeId);
                if (result.getBranchesToFollow() != null) {
                    branchSelections.put(nodeId, result.getBranchesToFollow());
                }

                // Clear resume data after successful execution
                if (isResume && nodeId.equals(waitingNodeId)) {
                    stateManager.clearResumeData(executionId, nodeId);
                    context.remove("_resumeData");
                }
            } catch (Exception e) {
                log.error("Node execution failed: executionId={}, nodeId={}", executionId, nodeId, e);
                failedNodes.add(nodeId);

                // Expose error info so downstream error handlers (error/always edges) can read it.
                Map<String, Object> errorInfo = new HashMap<>();
                errorInfo.put("error", true);
                errorInfo.put("errorMessage", "Node execution failed");
                errorInfo.put("errorType", e.getClass().getName());
                errorInfo.put("failedNodeId", nodeId);
                nodeOutputs.put(nodeId, errorInfo);
                context.put(nodeId, errorInfo);
                context.put("_lastError", errorInfo);

                // If this node has an error/always path, let the loop continue: the error/always
                // targets become active (see isNodeActive) and run in topological order. The failed
                // node's success successors stay inactive and are skipped. This replaces the old
                // inline error execution, which double-ran error handlers and still ran the success path.
                boolean hasErrorPath = allEdges.stream()
                    .anyMatch(edge -> nodeId.equals(edge.getSource())
                        && (edge.isErrorEdge() || edge.isAlwaysEdge()));

                if (hasErrorPath) {
                    log.info("Node {} failed but has error/always path, routing to error handlers", nodeId);
                    continue;
                } else {
                    // No error handling path, fail the execution
                    updateExecutionFailed(executionId, "Node execution failed");
                    return;
                }
            }
        }

        // Mark execution as completed
        execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        execution.setStatus("completed");
        execution.setCompletedAt(Instant.now());
        if (execution.getStartedAt() != null) {
            execution.setDurationMs((int) (execution.getCompletedAt().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
        }
        executionRepository.save(execution);

        stateManager.updateExecutionStatus(executionId, "completed");
        stateManager.setExecutionOutput(executionId, nodeOutputs);
        notificationService.notifyExecutionCompleted(executionId, nodeOutputs);

        // Log activity
        Flow flow = flowRepository.findById(version.getFlowId()).orElse(null);
        UUID flowId = flow != null ? flow.getId() : version.getFlowId();
        activityService.logExecutionComplete(execution.getTriggeredBy(), executionId, flowId,
            flow != null ? flow.getName() : null, execution.getDurationMs());

        log.info("Execution completed: id={}, duration={}ms", executionId, execution.getDurationMs());
    }

    /**
     * Decide whether a node should execute, given how its upstream nodes resolved.
     *
     * <p>A node runs if it is an entry point (no incoming edges) or at least one of its
     * incoming edges is "active":</p>
     * <ul>
     *   <li><b>always</b> edge — active once its source has executed (succeeded or failed);</li>
     *   <li><b>error</b> edge — active only when its source failed;</li>
     *   <li><b>success</b> edge (default) — active only when its source succeeded, and, if the
     *       source is a branching node (condition/switch/approval), only when the edge's branch
     *       (matched by {@code sourceHandle} or {@code label}) is among the selected branches.</li>
     * </ul>
     *
     * <p>Sources with no recorded branch selection (plain nodes, or nodes already completed
     * before a resume) let all their success edges through, preserving linear-flow behavior.</p>
     */
    private boolean isNodeActive(String nodeId,
                                 Map<String, List<DagParser.FlowEdge>> incomingByTarget,
                                 Set<String> succeededNodes,
                                 Set<String> failedNodes,
                                 Map<String, List<String>> branchSelections) {
        List<DagParser.FlowEdge> incoming = incomingByTarget.get(nodeId);
        if (incoming == null || incoming.isEmpty()) {
            return true; // entry point / trigger
        }
        for (DagParser.FlowEdge edge : incoming) {
            String source = edge.getSource();
            boolean srcSucceeded = succeededNodes.contains(source);
            boolean srcFailed = failedNodes.contains(source);
            if (!srcSucceeded && !srcFailed) {
                continue; // upstream node did not execute (e.g. itself skipped)
            }
            if (edge.isAlwaysEdge()) {
                return true;
            } else if (edge.isErrorEdge()) {
                if (srcFailed) {
                    return true;
                }
            } else { // success edge
                if (srcSucceeded) {
                    List<String> selected = branchSelections.get(source);
                    if (selected == null || matchesBranch(edge, selected)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Match an outgoing edge to a branching node's selected branch names,
     * comparing against the edge's {@code sourceHandle} first, then its {@code label}.
     */
    private boolean matchesBranch(DagParser.FlowEdge edge, List<String> selectedBranches) {
        String handle = edge.getSourceHandle();
        if (handle != null && selectedBranches.contains(handle)) {
            return true;
        }
        String label = edge.getLabel();
        return label != null && selectedBranches.contains(label);
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
        stateManager.extendTTLForWaiting(executionId);

        // Send notification
        notificationService.notifyExecutionWaiting(executionId, nodeId, pauseReason, resumeCondition);

        // Log activity
        activityService.logExecutionPause(execution.getTriggeredBy(), executionId, nodeId, pauseReason);

        log.info("Execution paused: id={}, node={}, reason={}", executionId, nodeId, pauseReason);
    }

    /**
     * Verify the caller has access to the execution (is the owner).
     */
    @Transactional(readOnly = true)
    public void verifyExecutionAccess(UUID executionId, UUID userId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        if (!userId.equals(execution.getTriggeredBy())) {
            throw new ResourceNotFoundException("Execution not found: " + executionId);
        }
    }

    /**
     * Resume a waiting execution with provided data.
     */
    @Transactional
    public ExecutionResponse resumeExecution(UUID executionId, Map<String, Object> resumeData, UUID userId) {
        Execution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));

        // Verify ownership - only the execution owner can resume
        if (userId == null || !userId.equals(execution.getTriggeredBy())) {
            throw new ResourceNotFoundException("Execution not found: " + executionId);
        }

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
            updateExecutionFailed(executionId, failureReason(e));
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
        /** For branching nodes (condition/switch/approval): the output branch names to follow. */
        private final List<String> branchesToFollow;

        private ExecuteNodeResult(Map<String, Object> output, boolean pauseRequested,
                                  String pauseReason, Map<String, Object> resumeCondition,
                                  List<String> branchesToFollow) {
            this.output = output;
            this.pauseRequested = pauseRequested;
            this.pauseReason = pauseReason;
            this.resumeCondition = resumeCondition;
            this.branchesToFollow = branchesToFollow;
        }

        static ExecuteNodeResult success(Map<String, Object> output, List<String> branchesToFollow) {
            return new ExecuteNodeResult(output, false, null, null, branchesToFollow);
        }

        static ExecuteNodeResult pause(String reason, Map<String, Object> condition, Map<String, Object> partialOutput) {
            return new ExecuteNodeResult(partialOutput, true, reason, condition, null);
        }

        Map<String, Object> getOutput() { return output; }
        boolean isPauseRequested() { return pauseRequested; }
        String getPauseReason() { return pauseReason; }
        Map<String, Object> getResumeCondition() { return resumeCondition; }
        List<String> getBranchesToFollow() { return branchesToFollow; }
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
                    return ExecuteNodeResult.success(pinnedDataOutput, null);
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
                executionId, nodeId, nodeType, data, context, nodeOutputs,
                withLabelAliases(nodes, nodeOutputs));

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

            return ExecuteNodeResult.success(output, result.getBranchesToFollow());
        } catch (Exception e) {
            // 保留真實錯誤訊息給使用者（欄位上限 255，截斷避免存檔失敗）
            String reason = truncateError(e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage() : "Execution failed");
            nodeExecution.setStatus("failed");
            nodeExecution.setCompletedAt(Instant.now());
            nodeExecution.setDurationMs((int) (nodeExecution.getCompletedAt().toEpochMilli() - nodeExecution.getStartedAt().toEpochMilli()));
            nodeExecution.setErrorMessage(reason);
            nodeExecution.setErrorStack(e.getClass().getName());
            nodeExecutionRepository.save(nodeExecution);

            stateManager.markNodeFailed(executionId, nodeId, reason);
            notificationService.notifyNodeFailed(executionId, nodeId, reason);
            throw e;
        }
    }

    /**
     * previousOutputs 以節點 id 為 key；補上「節點標籤 → 同一輸出」的別名，
     * 讓 AI 生成流程慣用的 {{$node['取得財報資料'].json}} 也能解析。
     * 只做為表達式解析用，不寫回實際儲存的輸出。
     */
    private Map<String, Object> withLabelAliases(List<Map<String, Object>> nodes,
                                                 Map<String, Object> nodeOutputs) {
        Map<String, Object> augmented = new LinkedHashMap<>(nodeOutputs);
        for (Map<String, Object> node : nodes) {
            Object id = node.get("id");
            Object dataObj = node.get("data");
            if (id == null || !(dataObj instanceof Map)) {
                continue;
            }
            Object label = ((Map<?, ?>) dataObj).get("label");
            if (label instanceof String l && !l.isBlank()
                && !augmented.containsKey(l)
                && nodeOutputs.containsKey(id.toString())) {
                augmented.put(l, nodeOutputs.get(id.toString()));
            }
        }
        return augmented;
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult executeNodeHandler(UUID executionId, String nodeId, String nodeType,
                                                    Map<String, Object> data, Map<String, Object> context,
                                                    Map<String, Object> nodeOutputs,
                                                    Map<String, Object> previousOutputsAliased) {
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
            .previousOutputs(previousOutputsAliased)
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
            .previousOutputs(previousOutputsAliased)
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

    /** 從例外萃取可讀的失敗原因；訊息為空時退回泛用文案。 */
    private String failureReason(Exception e) {
        String message = e.getMessage();
        return truncateError(message != null && !message.isBlank() ? message : "Execution failed");
    }

    /** error_message 欄位上限 255，超長時截斷避免存檔失敗。 */
    private String truncateError(String message) {
        return message.length() > 255 ? message.substring(0, 252) + "..." : message;
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
            String flowName = flowId != null
                ? flowRepository.findById(flowId).map(Flow::getName).orElse(null)
                : null;
            activityService.logExecutionFail(execution.getTriggeredBy(), executionId, flowId, flowName, errorMessage);
        });
        stateManager.updateExecutionStatus(executionId, "failed");
        notificationService.notifyExecutionFailed(executionId, errorMessage);
    }

    /**
     * Batch-enrich a page of executions to avoid N+1 queries.
     * Loads all FlowVersions and Flows in two bulk queries instead of 2N individual queries.
     */
    private Page<ExecutionResponse> enrichExecutionPage(Page<Execution> page) {
        if (page.isEmpty()) {
            return page.map(ExecutionResponse::from);
        }

        // Batch load all flow versions
        Set<UUID> versionIds = page.getContent().stream()
            .map(Execution::getFlowVersionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, FlowVersion> versionsMap = flowVersionRepository.findAllById(versionIds).stream()
            .collect(Collectors.toMap(FlowVersion::getId, v -> v));

        // Batch load all flows
        Set<UUID> flowIds = versionsMap.values().stream()
            .map(FlowVersion::getFlowId)
            .collect(Collectors.toSet());
        Map<UUID, Flow> flowsMap = flowRepository.findByIdInAndIsDeletedFalse(flowIds).stream()
            .collect(Collectors.toMap(Flow::getId, f -> f));

        return page.map(execution -> {
            FlowVersion version = versionsMap.get(execution.getFlowVersionId());
            if (version != null) {
                Flow flow = flowsMap.get(version.getFlowId());
                if (flow != null) {
                    return ExecutionResponse.from(execution, flow.getId(), flow.getName(), version.getVersion());
                }
            }
            return ExecutionResponse.from(execution);
        });
    }

    private ExecutionResponse enrichExecution(Execution execution) {
        FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
        if (version != null) {
            Flow flow = flowRepository.findById(version.getFlowId()).orElse(null);
            if (flow != null) {
                return ExecutionResponse.from(execution, flow.getId(), flow.getName(), version.getVersion());
            }
        }
        return ExecutionResponse.from(execution);
    }

    /**
     * Monitor and cancel stuck executions that exceed the configured timeout.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void monitorStuckExecutions() {
        if (executionTimeoutMs <= 0) {
            return;
        }

        try {
            Instant cutoff = Instant.now().minusMillis(executionTimeoutMs);
            List<Execution> stuckExecutions = executionRepository.findTop1000ByStatusAndStartedAtBefore("running", cutoff);

            for (Execution execution : stuckExecutions) {
                log.warn("EXECUTION_TIMEOUT id={} startedAt={} timeoutMs={}",
                        execution.getId(), execution.getStartedAt(), executionTimeoutMs);

                execution.setStatus("failed");
                execution.setCompletedAt(Instant.now());
                if (execution.getStartedAt() != null) {
                    execution.setDurationMs((int) (Instant.now().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
                }
                executionRepository.save(execution);

                try {
                    stateManager.updateExecutionStatus(execution.getId(), "failed");
                } catch (Exception e) {
                    log.debug("Failed to update state manager for timed-out execution: {}", e.getMessage());
                }

                try {
                    notificationService.notifyExecutionFailed(execution.getId(),
                            "Execution timed out after " + executionTimeoutMs + "ms");
                } catch (Exception e) {
                    log.debug("Failed to send timeout notification: {}", e.getMessage());
                }
            }

            if (!stuckExecutions.isEmpty()) {
                log.info("EXECUTION_TIMEOUT_MONITOR cancelled={}", stuckExecutions.size());
            }
        } catch (Exception e) {
            log.error("Stuck execution monitor failed", e);
        }
    }
}
