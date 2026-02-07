package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.entity.ApprovalAction;
import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.repository.ApprovalActionRepository;
import com.aiinpocket.n3n.execution.repository.ExecutionApprovalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExecutionApprovalServiceTest extends BaseServiceTest {

    @Mock
    private ExecutionApprovalRepository approvalRepository;

    @Mock
    private ApprovalActionRepository actionRepository;

    @Mock
    private ExecutionNotificationService notificationService;

    @InjectMocks
    private ExecutionApprovalService approvalService;

    // ========== createApproval Tests ==========

    @Test
    void createApproval_withDefaults_createsApprovalSuccessfully() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "node1";
        String message = "Please approve this deployment";

        when(approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId))
                .thenReturn(Optional.empty());
        when(approvalRepository.save(any(ExecutionApproval.class)))
                .thenAnswer(inv -> {
                    ExecutionApproval a = inv.getArgument(0);
                    if (a.getId() == null) a.setId(UUID.randomUUID());
                    return a;
                });

        // When
        ExecutionApproval result = approvalService.createApproval(
                executionId, nodeId, message, null, null, null, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(executionId);
        assertThat(result.getNodeId()).isEqualTo(nodeId);
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getRequiredApprovers()).isEqualTo(1);
        assertThat(result.getApprovalMode()).isEqualTo("any");
        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getApprovedCount()).isZero();
        assertThat(result.getRejectedCount()).isZero();
        assertThat(result.getExpiresAt()).isNull();
        assertThat(result.getApprovalType()).isEqualTo("manual");

        verify(approvalRepository).save(any(ExecutionApproval.class));
        verify(notificationService).notifyApprovalCreated(eq(executionId), eq(nodeId), any(UUID.class), eq(message));
    }

    @Test
    void createApproval_withCustomParameters_usesProvidedValues() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "approval-node";
        String message = "Need 3 approvers";
        Integer requiredApprovers = 3;
        String approvalMode = "all";
        Integer expiresInMinutes = 60;
        Map<String, Object> metadata = Map.of("priority", "high", "category", "deploy");

        when(approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId))
                .thenReturn(Optional.empty());
        when(approvalRepository.save(any(ExecutionApproval.class)))
                .thenAnswer(inv -> {
                    ExecutionApproval a = inv.getArgument(0);
                    if (a.getId() == null) a.setId(UUID.randomUUID());
                    return a;
                });

        // When
        ExecutionApproval result = approvalService.createApproval(
                executionId, nodeId, message, requiredApprovers, approvalMode, expiresInMinutes, metadata);

        // Then
        assertThat(result.getRequiredApprovers()).isEqualTo(3);
        assertThat(result.getApprovalMode()).isEqualTo("all");
        assertThat(result.getMetadata()).containsEntry("priority", "high");
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(59, ChronoUnit.MINUTES));
    }

    @Test
    void createApproval_alreadyExists_returnsExistingApproval() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "node1";
        ExecutionApproval existing = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId(nodeId)
                .status("pending")
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId))
                .thenReturn(Optional.of(existing));

        // When
        ExecutionApproval result = approvalService.createApproval(
                executionId, nodeId, "msg", 1, "any", null, null);

        // Then
        assertThat(result).isSameAs(existing);
        verify(approvalRepository, never()).save(any());
        verify(notificationService, never()).notifyApprovalCreated(any(), any(), any(), any());
    }

    // ========== submitApproval Tests ==========

    @Test
    void submitApproval_approve_incrementsApprovedCount() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .approvalMode("all")
                .requiredApprovers(3)
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(actionRepository.existsByApprovalIdAndUserId(approvalId, userId)).thenReturn(false);
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ExecutionApproval result = approvalService.submitApproval(approvalId, userId, "approve", "Looks good");

        // Then
        assertThat(result.getApprovedCount()).isEqualTo(1);
        assertThat(result.getRejectedCount()).isZero();
        assertThat(result.getStatus()).isEqualTo("pending"); // still pending, need 3 approvers in "all" mode

        ArgumentCaptor<ApprovalAction> actionCaptor = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(actionRepository).save(actionCaptor.capture());
        ApprovalAction savedAction = actionCaptor.getValue();
        assertThat(savedAction.getApprovalId()).isEqualTo(approvalId);
        assertThat(savedAction.getUserId()).isEqualTo(userId);
        assertThat(savedAction.getAction()).isEqualTo("approve");
        assertThat(savedAction.getComment()).isEqualTo("Looks good");

        verify(notificationService).notifyApprovalAction(executionId, approvalId, "approve", userId);
    }

    @Test
    void submitApproval_reject_incrementsRejectedCount() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .approvalMode("all")
                .requiredApprovers(3)
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(actionRepository.existsByApprovalIdAndUserId(approvalId, userId)).thenReturn(false);
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ExecutionApproval result = approvalService.submitApproval(approvalId, userId, "reject", "Not ready");

        // Then
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getApprovedCount()).isZero();
        // In "all" mode, any rejection triggers rejection
        assertThat(result.getStatus()).isEqualTo("rejected");
        assertThat(result.getResolvedAt()).isNotNull();

        verify(notificationService).notifyApprovalResolved(executionId, approvalId, "rejected");
    }

    @Test
    void submitApproval_approveInAnyMode_resolvesImmediately() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .approvalMode("any")
                .requiredApprovers(1)
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(actionRepository.existsByApprovalIdAndUserId(approvalId, userId)).thenReturn(false);
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ExecutionApproval result = approvalService.submitApproval(approvalId, userId, "approve", null);

        // Then
        assertThat(result.getApprovedCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("approved");
        assertThat(result.getResolvedAt()).isNotNull();

        verify(notificationService).notifyApprovalResolved(executionId, approvalId, "approved");
    }

    @Test
    void submitApproval_majorityMode_resolvesWhenMajorityReached() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        // requiredApprovers=4, majority means approvedCount > 4/2 = 2, so need at least 3
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .approvalMode("majority")
                .requiredApprovers(4)
                .approvedCount(2) // already 2 approvals
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(actionRepository.existsByApprovalIdAndUserId(approvalId, userId)).thenReturn(false);
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        ExecutionApproval result = approvalService.submitApproval(approvalId, userId, "approve", "Go ahead");

        // Then
        assertThat(result.getApprovedCount()).isEqualTo(3);
        assertThat(result.getStatus()).isEqualTo("approved"); // 3 > 4/2 = 2
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void submitApproval_onResolvedApproval_throwsIllegalStateException() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node1")
                .status("approved")
                .approvedCount(1)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));

        // When & Then
        assertThatThrownBy(() -> approvalService.submitApproval(approvalId, userId, "approve", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been resolved");
    }

    @Test
    void submitApproval_userAlreadyActed_throwsIllegalStateException() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node1")
                .status("pending")
                .approvalMode("any")
                .requiredApprovers(1)
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(actionRepository.existsByApprovalIdAndUserId(approvalId, userId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> approvalService.submitApproval(approvalId, userId, "approve", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    void submitApproval_expiredApproval_setsStatusExpiredAndThrows() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node1")
                .status("pending")
                .approvalMode("any")
                .requiredApprovers(1)
                .approvedCount(0)
                .rejectedCount(0)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // expired 1 hour ago
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When & Then
        assertThatThrownBy(() -> approvalService.submitApproval(approvalId, userId, "approve", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        assertThat(approval.getStatus()).isEqualTo("expired");
        assertThat(approval.getResolvedAt()).isNotNull();
        verify(approvalRepository).save(approval);
    }

    @Test
    void submitApproval_notFound_throwsResourceNotFoundException() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> approvalService.submitApproval(approvalId, userId, "approve", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(approvalId.toString());
    }

    // ========== getApproval Tests ==========

    @Test
    void getApproval_existingId_returnsApproval() {
        // Given
        UUID approvalId = UUID.randomUUID();
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node1")
                .status("pending")
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));

        // When
        ExecutionApproval result = approvalService.getApproval(approvalId);

        // Then
        assertThat(result).isSameAs(approval);
        assertThat(result.getId()).isEqualTo(approvalId);
    }

    @Test
    void getApproval_notFound_throwsResourceNotFoundException() {
        // Given
        UUID approvalId = UUID.randomUUID();
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> approvalService.getApproval(approvalId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(approvalId.toString());
    }

    // ========== getApprovalForExecution Tests ==========

    @Test
    void getApprovalForExecution_existing_returnsOptional() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "node1";
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId(nodeId)
                .status("pending")
                .build();

        when(approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId))
                .thenReturn(Optional.of(approval));

        // When
        Optional<ExecutionApproval> result = approvalService.getApprovalForExecution(executionId, nodeId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getExecutionId()).isEqualTo(executionId);
        assertThat(result.get().getNodeId()).isEqualTo(nodeId);
    }

    @Test
    void getApprovalForExecution_notFound_returnsEmpty() {
        // Given
        UUID executionId = UUID.randomUUID();
        when(approvalRepository.findByExecutionIdAndNodeId(executionId, "missing-node"))
                .thenReturn(Optional.empty());

        // When
        Optional<ExecutionApproval> result = approvalService.getApprovalForExecution(executionId, "missing-node");

        // Then
        assertThat(result).isEmpty();
    }

    // ========== getPendingApprovalForExecution Tests ==========

    @Test
    void getPendingApprovalForExecution_hasPending_returnsApproval() {
        // Given
        UUID executionId = UUID.randomUUID();
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .build();

        when(approvalRepository.findPendingByExecutionId(executionId))
                .thenReturn(Optional.of(approval));

        // When
        Optional<ExecutionApproval> result = approvalService.getPendingApprovalForExecution(executionId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("pending");
    }

    // ========== getApprovalsForExecution Tests ==========

    @Test
    void getApprovalsForExecution_multipleApprovals_returnsList() {
        // Given
        UUID executionId = UUID.randomUUID();
        List<ExecutionApproval> approvals = List.of(
                ExecutionApproval.builder().id(UUID.randomUUID()).executionId(executionId).nodeId("n1").status("approved").build(),
                ExecutionApproval.builder().id(UUID.randomUUID()).executionId(executionId).nodeId("n2").status("pending").build()
        );

        when(approvalRepository.findByExecutionId(executionId)).thenReturn(approvals);

        // When
        List<ExecutionApproval> result = approvalService.getApprovalsForExecution(executionId);

        // Then
        assertThat(result).hasSize(2);
    }

    // ========== getAllPendingApprovals Tests ==========

    @Test
    void getAllPendingApprovals_hasPending_returnsList() {
        // Given
        List<ExecutionApproval> pending = List.of(
                ExecutionApproval.builder().id(UUID.randomUUID()).executionId(UUID.randomUUID()).nodeId("n1").status("pending").build(),
                ExecutionApproval.builder().id(UUID.randomUUID()).executionId(UUID.randomUUID()).nodeId("n2").status("pending").build(),
                ExecutionApproval.builder().id(UUID.randomUUID()).executionId(UUID.randomUUID()).nodeId("n3").status("pending").build()
        );

        when(approvalRepository.findAllPending()).thenReturn(pending);

        // When
        List<ExecutionApproval> result = approvalService.getAllPendingApprovals();

        // Then
        assertThat(result).hasSize(3);
        verify(approvalRepository).findAllPending();
    }

    @Test
    void getAllPendingApprovals_noPending_returnsEmptyList() {
        // Given
        when(approvalRepository.findAllPending()).thenReturn(List.of());

        // When
        List<ExecutionApproval> result = approvalService.getAllPendingApprovals();

        // Then
        assertThat(result).isEmpty();
    }

    // ========== getActionsForApproval Tests ==========

    @Test
    void getActionsForApproval_hasActions_returnsList() {
        // Given
        UUID approvalId = UUID.randomUUID();
        List<ApprovalAction> actions = List.of(
                ApprovalAction.builder().id(UUID.randomUUID()).approvalId(approvalId).userId(UUID.randomUUID()).action("approve").build(),
                ApprovalAction.builder().id(UUID.randomUUID()).approvalId(approvalId).userId(UUID.randomUUID()).action("reject").build()
        );

        when(actionRepository.findByApprovalIdOrderByCreatedAtDesc(approvalId)).thenReturn(actions);

        // When
        List<ApprovalAction> result = approvalService.getActionsForApproval(approvalId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAction()).isEqualTo("approve");
        assertThat(result.get(1).getAction()).isEqualTo("reject");
    }

    // ========== expireOldApprovals Tests ==========

    @Test
    void expireOldApprovals_hasExpired_expiresAndReturnsCount() {
        // Given
        UUID execId1 = UUID.randomUUID();
        UUID execId2 = UUID.randomUUID();
        ExecutionApproval exp1 = ExecutionApproval.builder()
                .id(UUID.randomUUID()).executionId(execId1).nodeId("n1")
                .status("pending").approvedCount(0).rejectedCount(0)
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
        ExecutionApproval exp2 = ExecutionApproval.builder()
                .id(UUID.randomUUID()).executionId(execId2).nodeId("n2")
                .status("pending").approvedCount(0).rejectedCount(0)
                .expiresAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build();

        when(approvalRepository.findExpiredApprovals(any(Instant.class)))
                .thenReturn(List.of(exp1, exp2));
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = approvalService.expireOldApprovals();

        // Then
        assertThat(count).isEqualTo(2);
        assertThat(exp1.getStatus()).isEqualTo("expired");
        assertThat(exp1.getResolvedAt()).isNotNull();
        assertThat(exp2.getStatus()).isEqualTo("expired");
        assertThat(exp2.getResolvedAt()).isNotNull();

        verify(approvalRepository, times(2)).save(any(ExecutionApproval.class));
        verify(notificationService).notifyApprovalResolved(eq(execId1), eq(exp1.getId()), eq("expired"));
        verify(notificationService).notifyApprovalResolved(eq(execId2), eq(exp2.getId()), eq("expired"));
    }

    @Test
    void expireOldApprovals_noExpired_returnsZero() {
        // Given
        when(approvalRepository.findExpiredApprovals(any(Instant.class)))
                .thenReturn(List.of());

        // When
        int count = approvalService.expireOldApprovals();

        // Then
        assertThat(count).isZero();
        verify(approvalRepository, never()).save(any());
    }

    // ========== cancelApproval Tests ==========

    @Test
    void cancelApproval_pendingApproval_setsCancelledStatus() {
        // Given
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node1")
                .status("pending")
                .approvedCount(0)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(approvalRepository.save(any(ExecutionApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        approvalService.cancelApproval(approvalId);

        // Then
        assertThat(approval.getStatus()).isEqualTo("cancelled");
        assertThat(approval.getResolvedAt()).isNotNull();

        verify(approvalRepository).save(approval);
        verify(notificationService).notifyApprovalResolved(executionId, approvalId, "cancelled");
    }

    @Test
    void cancelApproval_alreadyResolved_doesNothing() {
        // Given
        UUID approvalId = UUID.randomUUID();
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node1")
                .status("approved")
                .approvedCount(1)
                .rejectedCount(0)
                .build();

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));

        // When
        approvalService.cancelApproval(approvalId);

        // Then
        assertThat(approval.getStatus()).isEqualTo("approved"); // unchanged
        verify(approvalRepository, never()).save(any());
        verify(notificationService, never()).notifyApprovalResolved(any(), any(), any());
    }

    @Test
    void cancelApproval_notFound_doesNothing() {
        // Given
        UUID approvalId = UUID.randomUUID();
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.empty());

        // When
        approvalService.cancelApproval(approvalId);

        // Then
        verify(approvalRepository, never()).save(any());
        verify(notificationService, never()).notifyApprovalResolved(any(), any(), any());
    }
}
