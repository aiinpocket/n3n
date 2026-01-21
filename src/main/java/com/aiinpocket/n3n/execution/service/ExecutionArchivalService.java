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
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Archive completed executions older than retention period.
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "${execution.archival.cron:0 0 2 * * ?}")
    @Transactional
    public void archiveOldExecutions() {
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Starting execution archival for records older than {}", cutoffDate);

        int archivedCount = 0;
        int batchSize = 100;

        List<Execution> toArchive = executionRepository.findByCompletedAtBeforeAndStatusIn(
            cutoffDate, COMPLETED_STATUSES, batchSize);

        while (!toArchive.isEmpty()) {
            for (Execution execution : toArchive) {
                try {
                    archiveExecution(execution);
                    archivedCount++;
                } catch (Exception e) {
                    log.error("Failed to archive execution: {}", execution.getId(), e);
                }
            }

            toArchive = executionRepository.findByCompletedAtBeforeAndStatusIn(
                cutoffDate, COMPLETED_STATUSES, batchSize);
        }

        log.info("Execution archival completed. Archived {} records.", archivedCount);
    }

    @Transactional
    public void archiveExecution(Execution execution) {
        // Get flow info
        String flowName = null;
        String flowVersion = null;

        FlowVersion version = flowVersionRepository.findById(execution.getFlowVersionId()).orElse(null);
        if (version != null) {
            flowVersion = version.getVersion();
            Flow flow = flowRepository.findById(version.getFlowId()).orElse(null);
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
     * Runs monthly on the 1st at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    @Transactional
    public void cleanupOldArchives() {
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        int deleted = archiveRepository.deleteByArchivedAtBefore(oneYearAgo);
        log.info("Cleaned up {} archive records older than 1 year", deleted);
    }
}
