package com.aiinpocket.n3n.housekeeping.service;

import com.aiinpocket.n3n.activity.repository.UserActivityRepository;
import com.aiinpocket.n3n.common.logging.LogContext;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.housekeeping.config.HousekeepingProperties;
import com.aiinpocket.n3n.housekeeping.entity.ExecutionHistory;
import com.aiinpocket.n3n.housekeeping.entity.HousekeepingJob;
import com.aiinpocket.n3n.housekeeping.entity.NodeExecutionHistory;
import com.aiinpocket.n3n.housekeeping.repository.ExecutionHistoryRepository;
import com.aiinpocket.n3n.housekeeping.repository.HousekeepingJobRepository;
import com.aiinpocket.n3n.housekeeping.repository.NodeExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for cleaning up old execution records.
 * Can archive to history tables or delete directly based on configuration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "housekeeping.enabled", havingValue = "true", matchIfMissing = true)
public class HousekeepingService {

    private final HousekeepingProperties properties;
    private final ExecutionRepository executionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowRepository flowRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final NodeExecutionHistoryRepository nodeExecutionHistoryRepository;
    private final HousekeepingJobRepository jobRepository;
    private final UserActivityRepository userActivityRepository;

    /**
     * Scheduled cleanup job.
     * Runs according to configured cron expression.
     */
    @Scheduled(cron = "${housekeeping.cron:0 0 2 * * ?}")
    public void scheduledCleanup() {
        String traceId = LogContext.generateTraceId();
        log.info("HOUSEKEEPING_START type=scheduled retentionDays={} archiveToHistory={}",
                properties.getRetentionDays(),
                properties.isArchiveToHistory());

        try {
            runCleanup();
        } finally {
            LogContext.clear();
        }
    }

    /**
     * Run cleanup manually.
     */
    public HousekeepingJob runCleanup() {
        // Check if already running
        if (jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")) {
            log.warn("HOUSEKEEPING_SKIP reason=already_running");
            return null;
        }

        // Create job record
        HousekeepingJob job = HousekeepingJob.builder()
                .jobType("execution_cleanup")
                .startedAt(Instant.now())
                .status("running")
                .details(Map.of(
                        "retentionDays", properties.getRetentionDays(),
                        "archiveToHistory", properties.isArchiveToHistory(),
                        "batchSize", properties.getBatchSize()
                ))
                .build();
        job = jobRepository.save(job);

        try {
            Instant cutoffDate = Instant.now().minus(properties.getRetentionDays(), ChronoUnit.DAYS);
            log.info("HOUSEKEEPING_CUTOFF date={}", cutoffDate);

            int totalProcessed = 0;
            int totalArchived = 0;
            int totalDeleted = 0;

            // Process in batches with safety limit
            int batchSize = properties.getBatchSize();
            boolean hasMore = true;
            int maxIterations = 10000; // Safety limit to prevent infinite loops
            int iteration = 0;

            while (hasMore && iteration < maxIterations) {
                iteration++;
                Page<Execution> batch = findExpiredExecutions(cutoffDate, batchSize);
                List<Execution> executions = batch.getContent();

                if (executions.isEmpty()) {
                    hasMore = false;
                    continue;
                }

                for (Execution execution : executions) {
                    try {
                        if (properties.isArchiveToHistory()) {
                            archiveExecution(execution);
                            totalArchived++;
                        } else {
                            deleteExecution(execution);
                            totalDeleted++;
                        }
                        totalProcessed++;
                    } catch (Exception e) {
                        log.error("HOUSEKEEPING_ERROR executionId={} error={}",
                                execution.getId(), e.getMessage());
                    }
                }

                log.info("HOUSEKEEPING_BATCH processed={} archived={} deleted={}",
                        totalProcessed, totalArchived, totalDeleted);

                // Since we always query page 0 and delete processed records,
                // check if there are still records to process
                hasMore = batch.getTotalElements() > executions.size();
            }

            if (iteration >= maxIterations) {
                log.warn("HOUSEKEEPING_MAX_ITERATIONS reached={}", maxIterations);
            }

            // Also cleanup old history if configured
            if (properties.getHistoryRetentionDays() > 0) {
                int historyDeleted = cleanupOldHistory();
                log.info("HOUSEKEEPING_HISTORY deleted={}", historyDeleted);
            }

            // Cleanup old activity logs
            if (properties.getActivityRetentionDays() > 0) {
                int activityDeleted = cleanupOldActivities();
                log.info("HOUSEKEEPING_ACTIVITIES deleted={}", activityDeleted);
            }

            // Update job
            job.setRecordsProcessed(totalProcessed);
            job.setRecordsArchived(totalArchived);
            job.setRecordsDeleted(totalDeleted);
            job.complete();
            jobRepository.save(job);

            log.info("HOUSEKEEPING_COMPLETE processed={} archived={} deleted={}",
                    totalProcessed, totalArchived, totalDeleted);

            return job;

        } catch (Exception e) {
            log.error("HOUSEKEEPING_FAILED error={}", e.getMessage(), e);
            job.fail(e.getMessage());
            jobRepository.save(job);
            throw e;
        }
    }

    /**
     * Find expired executions (completed/failed/cancelled and older than cutoff).
     */
    private Page<Execution> findExpiredExecutions(Instant cutoffDate, int batchSize) {
        return executionRepository.findByStatusInAndStartedAtBefore(
                List.of("completed", "failed", "cancelled"),
                cutoffDate,
                PageRequest.of(0, batchSize)
        );
    }

    /**
     * Archive an execution to history tables.
     */
    @Transactional
    public void archiveExecution(Execution execution) {
        UUID executionId = execution.getId();

        log.debug("ARCHIVE_EXECUTION id={}", executionId);

        // Get flow info for denormalization
        FlowVersion flowVersion = flowVersionRepository.findById(execution.getFlowVersionId())
                .orElse(null);
        Flow flow = flowVersion != null ?
                flowRepository.findById(flowVersion.getFlowId()).orElse(null) : null;

        // Create history record
        ExecutionHistory history = ExecutionHistory.builder()
                .id(execution.getId())
                .flowVersionId(execution.getFlowVersionId())
                .flowId(flowVersion != null ? flowVersion.getFlowId() : null)
                .flowName(flow != null ? flow.getName() : null)
                .flowVersion(flowVersion != null ? flowVersion.getVersion() : null)
                .status(execution.getStatus())
                .triggerInput(execution.getTriggerInput())
                .triggerContext(execution.getTriggerContext())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .durationMs(execution.getDurationMs())
                .triggeredBy(execution.getTriggeredBy())
                .triggerType(execution.getTriggerType())
                .cancelReason(execution.getCancelReason())
                .cancelledBy(execution.getCancelledBy())
                .cancelledAt(execution.getCancelledAt())
                .pausedAt(execution.getPausedAt())
                .waitingNodeId(execution.getWaitingNodeId())
                .pauseReason(execution.getPauseReason())
                .resumeCondition(execution.getResumeCondition())
                .retryOf(execution.getRetryOf())
                .retryCount(execution.getRetryCount())
                .maxRetries(execution.getMaxRetries())
                .archivedAt(Instant.now())
                .originalCreatedAt(execution.getStartedAt())
                .build();

        executionHistoryRepository.save(history);

        // Archive node executions
        List<NodeExecution> nodeExecutions = nodeExecutionRepository.findByExecutionId(executionId);
        for (NodeExecution nodeExec : nodeExecutions) {
            NodeExecutionHistory nodeHistory = NodeExecutionHistory.builder()
                    .id(nodeExec.getId())
                    .executionId(nodeExec.getExecutionId())
                    .nodeId(nodeExec.getNodeId())
                    .componentName(nodeExec.getComponentName())
                    .componentVersion(nodeExec.getComponentVersion())
                    .status(nodeExec.getStatus())
                    .startedAt(nodeExec.getStartedAt())
                    .completedAt(nodeExec.getCompletedAt())
                    .durationMs(nodeExec.getDurationMs())
                    .errorMessage(nodeExec.getErrorMessage())
                    .errorStack(nodeExec.getErrorStack())
                    .workerId(nodeExec.getWorkerId())
                    .retryCount(nodeExec.getRetryCount())
                    .archivedAt(Instant.now())
                    .build();

            nodeExecutionHistoryRepository.save(nodeHistory);
        }

        // Delete from main tables
        nodeExecutionRepository.deleteByExecutionId(executionId);
        executionRepository.deleteById(executionId);
    }

    /**
     * Delete an execution without archiving.
     */
    @Transactional
    public void deleteExecution(Execution execution) {
        UUID executionId = execution.getId();

        log.debug("DELETE_EXECUTION id={}", executionId);

        // Delete node executions first (cascade)
        nodeExecutionRepository.deleteByExecutionId(executionId);

        // Delete execution
        executionRepository.deleteById(executionId);
    }

    /**
     * Cleanup old history records.
     */
    @Transactional
    public int cleanupOldHistory() {
        if (properties.getHistoryRetentionDays() <= 0) {
            return 0;
        }

        Instant cutoffDate = Instant.now().minus(properties.getHistoryRetentionDays(), ChronoUnit.DAYS);

        log.info("CLEANUP_HISTORY cutoffDate={}", cutoffDate);

        int nodeDeleted = nodeExecutionHistoryRepository.deleteByArchivedAtBefore(cutoffDate);
        int execDeleted = executionHistoryRepository.deleteByArchivedAtBefore(cutoffDate);

        return nodeDeleted + execDeleted;
    }

    /**
     * Cleanup old activity log records.
     */
    @Transactional
    public int cleanupOldActivities() {
        if (properties.getActivityRetentionDays() <= 0) {
            return 0;
        }

        Instant cutoffDate = Instant.now().minus(properties.getActivityRetentionDays(), ChronoUnit.DAYS);
        log.info("CLEANUP_ACTIVITIES cutoffDate={}", cutoffDate);

        return userActivityRepository.deleteByCreatedAtBefore(cutoffDate);
    }

    /**
     * Get cleanup statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Current counts
        stats.put("executionsCount", executionRepository.count());
        stats.put("executionsHistoryCount", executionHistoryRepository.count());

        // Configuration
        stats.put("retentionDays", properties.getRetentionDays());
        stats.put("archiveToHistory", properties.isArchiveToHistory());
        stats.put("historyRetentionDays", properties.getHistoryRetentionDays());
        stats.put("activityRetentionDays", properties.getActivityRetentionDays());

        // Last job
        jobRepository.findFirstByJobTypeOrderByStartedAtDesc("execution_cleanup")
                .ifPresent(job -> {
                    stats.put("lastJobStatus", job.getStatus());
                    stats.put("lastJobStartedAt", job.getStartedAt());
                    stats.put("lastJobRecordsProcessed", job.getRecordsProcessed());
                });

        return stats;
    }
}
