package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Recovers stale executions on application startup.
 * When the application restarts, any executions still in "running" or "pending"
 * status have no associated thread and will never complete. This service marks
 * them as "failed" to free up concurrent execution slots and provide accurate status.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionRecoveryService {

    private final ExecutionRepository executionRepository;
    private final StateManager stateManager;
    private final ExecutionNotificationService notificationService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleExecutions() {
        int recovered = 0;

        // Mark all "running" executions as failed (their threads are gone after restart)
        List<Execution> runningExecutions = executionRepository.findTop1000ByStatus("running");
        for (Execution execution : runningExecutions) {
            markAsFailed(execution, "Application restarted while execution was running");
            recovered++;
        }

        // Mark all "pending" executions as failed (they were never picked up)
        List<Execution> pendingExecutions = executionRepository.findTop1000ByStatus("pending");
        for (Execution execution : pendingExecutions) {
            markAsFailed(execution, "Application restarted while execution was pending");
            recovered++;
        }

        if (recovered > 0) {
            log.info("EXECUTION_RECOVERY recovered={} stale executions marked as failed on startup", recovered);
        } else {
            log.info("EXECUTION_RECOVERY no stale executions found");
        }
    }

    private void markAsFailed(Execution execution, String reason) {
        try {
            execution.setStatus("failed");
            execution.setCompletedAt(Instant.now());
            if (execution.getStartedAt() != null) {
                execution.setDurationMs((int) (Instant.now().toEpochMilli() - execution.getStartedAt().toEpochMilli()));
            }
            executionRepository.save(execution);

            try {
                stateManager.updateExecutionStatus(execution.getId(), "failed");
            } catch (Exception e) {
                log.debug("Failed to update Redis state for recovered execution {}: {}", execution.getId(), e.getMessage());
            }

            try {
                notificationService.notifyExecutionFailed(execution.getId(), reason);
            } catch (Exception e) {
                log.debug("Failed to send recovery notification for execution {}: {}", execution.getId(), e.getMessage());
            }

            log.info("EXECUTION_RECOVERY id={} previousStatus={} reason={}",
                    execution.getId(), execution.getStatus(), reason);
        } catch (Exception e) {
            log.error("EXECUTION_RECOVERY_FAILED id={}: {}", execution.getId(), e.getMessage());
        }
    }
}
