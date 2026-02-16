package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.ExecutionArchive;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.repository.ExecutionArchiveRepository;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionArchivalService {

    private final ExecutionRepository executionRepository;
    private final ExecutionArchiveRepository archiveRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowRepository flowRepository;
    private final StateManager stateManager;

    @Value("${execution.history.retention-days:30}")
    private int retentionDays;

    private static final Set<String> COMPLETED_STATUSES = Set.of("completed", "failed", "cancelled");
    private final AtomicBoolean archivalRunning = new AtomicBoolean(false);

    /**
     * Archive completed executions older than retention period.
     * Runs daily at 3:00 AM (staggered from housekeeping at 2:00 AM).
     */
    @Scheduled(cron = "${execution.archival.cron:0 0 3 * * ?}")
    @Transactional
    public void archiveOldExecutions() {
        if (!archivalRunning.compareAndSet(false, true)) {
            log.warn("Execution archival already running, skipping this run");
            return;
        }
        try {
            doArchiveOldExecutions();
        } finally {
            archivalRunning.set(false);
        }
    }

    private void doArchiveOldExecutions() {
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Starting execution archival for records older than {}", cutoffDate);

        int archivedCount = 0;
        int failedCount = 0;
        int batchSize = 100;
        int maxIterations = 10000;
        int iteration = 0;

        List<Execution> toArchive = executionRepository.findByCompletedAtBeforeAndStatusIn(
            cutoffDate, COMPLETED_STATUSES, batchSize);

        while (!toArchive.isEmpty() && iteration < maxIterations) {
            iteration++;

            // Batch-load flow versions and flows to avoid N+1 queries
            Set<UUID> versionIds = new HashSet<>();
            for (Execution exec : toArchive) {
                if (exec.getFlowVersionId() != null) versionIds.add(exec.getFlowVersionId());
            }
            Map<UUID, FlowVersion> versionMap = new HashMap<>();
            if (!versionIds.isEmpty()) {
                flowVersionRepository.findAllById(versionIds).forEach(v -> versionMap.put(v.getId(), v));
            }
            Set<UUID> flowIds = new HashSet<>();
            for (FlowVersion v : versionMap.values()) {
                if (v.getFlowId() != null) flowIds.add(v.getFlowId());
            }
            Map<UUID, Flow> flowMap = new HashMap<>();
            if (!flowIds.isEmpty()) {
                flowRepository.findAllById(flowIds).forEach(f -> flowMap.put(f.getId(), f));
            }

            int batchFailed = 0;
            for (Execution execution : toArchive) {
                try {
                    archiveExecution(execution, versionMap, flowMap);
                    archivedCount++;
                } catch (Exception e) {
                    failedCount++;
                    batchFailed++;
                    log.error("Failed to archive execution: {}", execution.getId(), e);
                }
            }

            // If entire batch failed, stop to avoid infinite loop
            if (batchFailed == toArchive.size()) {
                log.error("Entire batch failed to archive, stopping to prevent infinite loop. Failed: {}", failedCount);
                break;
            }

            toArchive = executionRepository.findByCompletedAtBeforeAndStatusIn(
                cutoffDate, COMPLETED_STATUSES, batchSize);
        }

        if (iteration >= maxIterations) {
            log.warn("Execution archival hit max iteration limit ({}). Archived: {}, Failed: {}", maxIterations, archivedCount, failedCount);
        } else {
            log.info("Execution archival completed. Archived: {}, Failed: {}", archivedCount, failedCount);
        }
    }

    /**
     * Archive a single execution (backward-compatible, loads flow info individually).
     */
    @Transactional
    public void archiveExecution(Execution execution) {
        // Fallback for single execution archival (used in tests and external callers)
        Map<UUID, FlowVersion> versionMap = new HashMap<>();
        Map<UUID, Flow> flowMap = new HashMap<>();
        if (execution.getFlowVersionId() != null) {
            flowVersionRepository.findById(execution.getFlowVersionId()).ifPresent(v -> {
                versionMap.put(v.getId(), v);
                if (v.getFlowId() != null) {
                    flowRepository.findById(v.getFlowId()).ifPresent(f -> flowMap.put(f.getId(), f));
                }
            });
        }
        archiveExecution(execution, versionMap, flowMap);
    }

    @Transactional
    public void archiveExecution(Execution execution, Map<UUID, FlowVersion> versionMap, Map<UUID, Flow> flowMap) {
        // Get flow info from pre-loaded maps (batch-loaded to avoid N+1)
        String flowName = null;
        String flowVersion = null;

        FlowVersion version = execution.getFlowVersionId() != null ? versionMap.get(execution.getFlowVersionId()) : null;
        if (version != null) {
            flowVersion = version.getVersion();
            Flow flow = version.getFlowId() != null ? flowMap.get(version.getFlowId()) : null;
            if (flow != null) {
                flowName = flow.getName();
            }
        }

        // Get node executions
        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(execution.getId());
        Map<String, Object> nodeExecutionsMap = new HashMap<>();
        for (NodeExecution ne : nodeExecutions) {
            Map<String, Object> nodeData = new HashMap<>();
            nodeData.put("nodeId", ne.getNodeId());
            nodeData.put("status", ne.getStatus());
            nodeData.put("componentName", ne.getComponentName());
            nodeData.put("startedAt", ne.getStartedAt());
            nodeData.put("completedAt", ne.getCompletedAt());
            nodeData.put("durationMs", ne.getDurationMs());
            nodeData.put("errorMessage", ne.getErrorMessage());
            nodeExecutionsMap.put(ne.getNodeId(), nodeData);
        }

        // Get output from Redis if available
        Map<String, Object> output = stateManager.getExecutionOutput(execution.getId());

        // Create archive record
        ExecutionArchive archive = ExecutionArchive.builder()
            .id(execution.getId())
            .flowVersionId(execution.getFlowVersionId())
            .flowName(flowName)
            .flowVersion(flowVersion)
            .status(execution.getStatus())
            .triggerInput(execution.getTriggerInput())
            .triggerContext(execution.getTriggerContext())
            .output(output)
            .startedAt(execution.getStartedAt())
            .completedAt(execution.getCompletedAt())
            .durationMs(execution.getDurationMs())
            .triggeredBy(execution.getTriggeredBy())
            .triggerType(execution.getTriggerType())
            .nodeExecutions(nodeExecutionsMap)
            .build();

        archiveRepository.save(archive);

        // Delete from main tables
        nodeExecutionRepository.deleteByExecutionId(execution.getId());
        executionRepository.delete(execution);

        // Clean up Redis state
        stateManager.cleanupExecution(execution.getId());

        log.debug("Archived execution: {}", execution.getId());
    }

    /**
     * Clean up archives older than 1 year.
     * Runs monthly on the 1st at 4:00 AM.
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    @Transactional
    public void cleanupOldArchives() {
        try {
            Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
            int deleted = archiveRepository.deleteByArchivedAtBefore(oneYearAgo);
            log.info("Cleaned up {} archive records older than 1 year", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup old archives", e);
        }
    }
}
