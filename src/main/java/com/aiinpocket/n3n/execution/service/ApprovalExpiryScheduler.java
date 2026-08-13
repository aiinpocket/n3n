package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodically expires overdue approvals and unsticks the executions that were
 * waiting on them.
 *
 * <p>Without this, an execution paused at an approval node with a set expiry would
 * sit in {@code waiting} forever if nobody voted: {@code expireOldApprovals} had no
 * scheduled caller, and the stuck-execution monitor only inspects {@code running}
 * executions. Expiring an approval routes the execution down its "rejected" branch
 * (see {@link com.aiinpocket.n3n.execution.handler.handlers.flowcontrol.ApprovalNodeHandler}),
 * mirroring what {@code ApprovalController} does when a vote resolves an approval.</p>
 *
 * <p>Kept as a separate component (rather than a {@code @Scheduled} method on
 * {@code ExecutionApprovalService}) to avoid a constructor dependency cycle:
 * {@code ExecutionService → NodeHandlerRegistry → ApprovalNodeHandler →
 * ExecutionApprovalService}. This scheduler is depended on by nobody, so wiring it to
 * both services introduces no cycle.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalExpiryScheduler {

    private final ExecutionApprovalService approvalService;
    private final ExecutionService executionService;
    private final ExecutionRepository executionRepository;

    /**
     * Runs every minute. Expires overdue approvals and resumes each associated
     * execution that is still waiting, letting the flow continue down its rejected path.
     */
    @Scheduled(fixedDelayString = "${execution.approval.expiry-check-ms:60000}")
    public void expireApprovals() {
        try {
            List<ExecutionApproval> expired = approvalService.expireOldApprovalsAndCollect();
            for (ExecutionApproval approval : expired) {
                resumeExpiredExecution(approval);
            }
        } catch (Exception e) {
            log.error("Approval expiry scheduler failed", e);
        }
    }

    private void resumeExpiredExecution(ExecutionApproval approval) {
        try {
            Execution execution = executionRepository.findById(approval.getExecutionId()).orElse(null);
            if (execution == null || !"waiting".equals(execution.getStatus())) {
                return;
            }
            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("approvalId", approval.getId().toString());
            resumeData.put("approvalStatus", "expired");
            log.info("Resuming expired-approval execution: executionId={}, approvalId={}",
                approval.getExecutionId(), approval.getId());
            // Resume as the execution's owner so the ownership check passes.
            executionService.resumeExecution(approval.getExecutionId(), resumeData, execution.getTriggeredBy());
        } catch (Exception e) {
            log.warn("Failed to resume execution {} after approval expiry: {}",
                approval.getExecutionId(), e.getMessage());
        }
    }
}
