package com.aiinpocket.n3n.housekeeping.service;

import com.aiinpocket.n3n.activity.repository.UserActivityRepository;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.auth.repository.RefreshTokenRepository;
import com.aiinpocket.n3n.common.logging.LogContext;
import com.aiinpocket.n3n.execution.service.FormService;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final RefreshTokenRepository refreshTokenRepository;
    private final FormService formService;
    private final ArtifactService artifactService;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

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
        // Atomic guard to prevent concurrent cleanup runs (TOCTOU safe)
        if (!cleanupRunning.compareAndSet(false, true)) {
            log.warn("HOUSEKEEPING_SKIP reason=already_running");
            return null;
        }

        try {
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
                    List<Execution> executions = findExpiredExecutions(cutoffDate, batchSize);

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

                    // 撈滿一整批表示可能還有；不足一批就代表處理完了
                    hasMore = executions.size() >= batchSize;
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

                // Cleanup expired/revoked refresh tokens
                if (properties.getTokenRetentionDays() > 0) {
                    int tokensDeleted = cleanupOldRefreshTokens();
                    log.info("HOUSEKEEPING_TOKENS deleted={}", tokensDeleted);
                }

                // Expire old form triggers
                int formsExpired = formService.expireOldFormTriggers();
                if (formsExpired > 0) {
                    log.info("HOUSEKEEPING_FORMS expired={}", formsExpired);
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
                job.fail("Housekeeping job failed");
                jobRepository.save(job);
                throw e;
            }
        } finally {
            cleanupRunning.set(false);
        }
    }

    /**
     * 保留策略選取：過期且已結束的執行，
     * 排除設為永久（pinned）與各流程的最新一次執行。
     */
    private List<Execution> findExpiredExecutions(Instant cutoffDate, int batchSize) {
        return executionRepository.findExpiredForCleanup(cutoffDate, batchSize);
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

        // 歸檔只保留 metadata，artifacts 同樣依保留策略清理（pinned 者保留）
        cleanupArtifacts(executionId);

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

        // 清理此執行的 artifacts（pinned 者保留）
        cleanupArtifacts(executionId);

        // Delete node executions first (cascade)
        nodeExecutionRepository.deleteByExecutionId(executionId);

        // Delete execution
        executionRepository.deleteById(executionId);
    }

    private void cleanupArtifacts(UUID executionId) {
        try {
            int deleted = artifactService.deleteUnpinnedByExecution(executionId);
            if (deleted > 0) {
                log.debug("HOUSEKEEPING_ARTIFACTS executionId={} deleted={}", executionId, deleted);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup artifacts for execution {}: {}", executionId, e.getMessage());
        }
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
     * Cleanup expired and revoked refresh tokens.
     */
    @Transactional
    public int cleanupOldRefreshTokens() {
        if (properties.getTokenRetentionDays() <= 0) {
            return 0;
        }

        Instant cutoffDate = Instant.now().minus(properties.getTokenRetentionDays(), ChronoUnit.DAYS);
        log.info("CLEANUP_TOKENS cutoffDate={}", cutoffDate);

        int expired = refreshTokenRepository.deleteExpiredTokensBefore(cutoffDate);
        int revoked = refreshTokenRepository.deleteRevokedTokensBefore(cutoffDate);

        return expired + revoked;
    }

    /**
     * Get cleanup statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Current counts (aligned with frontend HousekeepingStats interface)
        long totalExecutions = executionRepository.count();
        long archivedExecutions = executionHistoryRepository.count();
        stats.put("totalExecutions", totalExecutions);
        stats.put("archivedExecutions", archivedExecutions);

        // Count executions older than retention period
        Instant cutoff = Instant.now().minus(properties.getRetentionDays(), ChronoUnit.DAYS);
        long oldExecutions = executionRepository.countByStartedAtBefore(cutoff);
        stats.put("oldExecutions", oldExecutions);

        // Configuration
        stats.put("retentionDays", properties.getRetentionDays());

        // Last cleanup time
        jobRepository.findFirstByJobTypeOrderByStartedAtDesc("execution_cleanup")
                .ifPresent(job -> stats.put("lastCleanupAt", job.getStartedAt()));

        stats.put("nextScheduledCleanup", null);

        return stats;
    }
}
