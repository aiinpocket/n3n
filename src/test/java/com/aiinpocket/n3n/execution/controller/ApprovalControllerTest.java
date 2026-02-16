package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.entity.ApprovalAction;
import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.service.ExecutionApprovalService;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ExecutionApprovalService approvalService;

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private ApprovalController approvalController;

    // ===== Helpers =====

    private UserDetails testUser(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private ExecutionApproval sampleApproval(UUID approvalId, UUID executionId) {
        return ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("approval-node-1")
                .approvalType("manual")
                .message("Please approve this execution")
                .requiredApprovers(1)
                .approvalMode("any")
                .status("pending")
                .approvedCount(0)
                .rejectedCount(0)
                .createdAt(Instant.now())
                .build();
    }

    private ApprovalAction sampleAction(UUID approvalId, UUID userId) {
        return ApprovalAction.builder()
                .id(UUID.randomUUID())
                .approvalId(approvalId)
                .userId(userId)
                .action("approve")
                .comment("Looks good")
                .createdAt(Instant.now())
                .build();
    }

    // ===== GET /pending =====

    @Test
    void getPendingApprovals_shouldReturnOnlyUserApprovals() {
        UUID userId = UUID.randomUUID();
        UUID approvalId1 = UUID.randomUUID();

        ExecutionApproval approval1 = sampleApproval(approvalId1, UUID.randomUUID());

        // DB-level filtering returns only user's approvals
        when(approvalService.getPendingApprovalsForUser(userId)).thenReturn(List.of(approval1));

        var response = approvalController.getPendingApprovals(testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).id()).isEqualTo(approvalId1.toString());
    }

    @Test
    void getPendingApprovals_shouldReturnEmptyWhenNoApprovals() {
        UUID userId = UUID.randomUUID();
        when(approvalService.getPendingApprovalsForUser(userId)).thenReturn(List.of());

        var response = approvalController.getPendingApprovals(testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ===== GET /{approvalId} =====

    @Test
    void getApproval_shouldReturnDetailWhenAuthorized() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        ExecutionApproval approval = sampleApproval(approvalId, UUID.randomUUID());
        ApprovalAction action = sampleAction(approvalId, userId);

        when(approvalService.getApproval(approvalId)).thenReturn(approval);
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.getActionsForApproval(approvalId)).thenReturn(List.of(action));

        var response = approvalController.getApproval(approvalId, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(approvalId.toString());
        assertThat(response.getBody().actions()).hasSize(1);
    }

    @Test
    void getApproval_shouldThrowWhenNotAuthorized() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        ExecutionApproval approval = sampleApproval(approvalId, UUID.randomUUID());

        when(approvalService.getApproval(approvalId)).thenReturn(approval);
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(false);

        assertThatThrownBy(() -> approvalController.getApproval(approvalId, testUser(userId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== POST /{approvalId} - Approve =====

    @Test
    void submitApproval_shouldApproveAndResumeExecution() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval existing = sampleApproval(approvalId, executionId);
        ExecutionApproval approved = sampleApproval(approvalId, executionId);
        approved.setStatus("approved");
        approved.setApprovedCount(1);
        approved.setResolvedAt(Instant.now());

        when(approvalService.getApproval(approvalId)).thenReturn(existing);
        when(approvalService.isUserAuthorizedForApproval(existing, userId)).thenReturn(true);
        when(approvalService.submitApproval(eq(approvalId), eq(userId), eq("approve"), eq("LGTM")))
                .thenReturn(approved);
        when(approvalService.getActionsForApproval(approvalId)).thenReturn(List.of());

        var request = new ApprovalController.ApprovalActionRequest("approve", "LGTM");
        var response = approvalController.submitApproval(approvalId, request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("approved");

        // Verify execution was resumed
        verify(executionService).resumeExecution(eq(executionId), anyMap(), eq(userId));
    }

    @Test
    void submitApproval_shouldRejectWithoutResumingWhenCancelled() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval existing = sampleApproval(approvalId, executionId);
        ExecutionApproval cancelled = sampleApproval(approvalId, executionId);
        cancelled.setStatus("cancelled");

        when(approvalService.getApproval(approvalId)).thenReturn(existing);
        when(approvalService.isUserAuthorizedForApproval(existing, userId)).thenReturn(true);
        when(approvalService.submitApproval(eq(approvalId), eq(userId), eq("reject"), isNull()))
                .thenReturn(cancelled);
        when(approvalService.getActionsForApproval(approvalId)).thenReturn(List.of());

        var request = new ApprovalController.ApprovalActionRequest("reject", null);
        var response = approvalController.submitApproval(approvalId, request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Cancelled status should not resume execution
        verify(executionService, never()).resumeExecution(any(), anyMap(), any());
    }

    @Test
    void submitApproval_shouldRejectAndResumeExecution() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval existing = sampleApproval(approvalId, executionId);
        ExecutionApproval rejected = sampleApproval(approvalId, executionId);
        rejected.setStatus("rejected");
        rejected.setRejectedCount(1);
        rejected.setResolvedAt(Instant.now());

        when(approvalService.getApproval(approvalId)).thenReturn(existing);
        when(approvalService.isUserAuthorizedForApproval(existing, userId)).thenReturn(true);
        when(approvalService.submitApproval(eq(approvalId), eq(userId), eq("reject"), eq("Not ready")))
                .thenReturn(rejected);
        when(approvalService.getActionsForApproval(approvalId)).thenReturn(List.of());

        var request = new ApprovalController.ApprovalActionRequest("reject", "Not ready");
        var response = approvalController.submitApproval(approvalId, request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("rejected");

        // Rejected status should still resume to notify
        verify(executionService).resumeExecution(eq(executionId), anyMap(), eq(userId));
    }

    @Test
    void submitApproval_shouldNotResumeWhenPending() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionApproval existing = sampleApproval(approvalId, executionId);
        ExecutionApproval stillPending = sampleApproval(approvalId, executionId);
        // Still pending (not resolved)

        when(approvalService.getApproval(approvalId)).thenReturn(existing);
        when(approvalService.isUserAuthorizedForApproval(existing, userId)).thenReturn(true);
        when(approvalService.submitApproval(eq(approvalId), eq(userId), eq("approve"), isNull()))
                .thenReturn(stillPending);
        when(approvalService.getActionsForApproval(approvalId)).thenReturn(List.of());

        var request = new ApprovalController.ApprovalActionRequest("approve", null);
        var response = approvalController.submitApproval(approvalId, request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Not resolved, should not resume
        verify(executionService, never()).resumeExecution(any(), anyMap(), any());
    }

    @Test
    void submitApproval_shouldThrowWhenNotAuthorized() {
        UUID userId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        ExecutionApproval existing = sampleApproval(approvalId, UUID.randomUUID());

        when(approvalService.getApproval(approvalId)).thenReturn(existing);
        when(approvalService.isUserAuthorizedForApproval(existing, userId)).thenReturn(false);

        var request = new ApprovalController.ApprovalActionRequest("approve", null);

        assertThatThrownBy(() -> approvalController.submitApproval(approvalId, request, testUser(userId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== DTO Mapping =====

    @Test
    void approvalSummary_shouldMapAllFields() {
        UUID approvalId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(3600);

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(executionId)
                .nodeId("node-1")
                .message("Approve?")
                .approvalMode("any")
                .requiredApprovers(1)
                .approvedCount(0)
                .rejectedCount(0)
                .expiresAt(expires)
                .createdAt(now)
                .build();

        var summary = ApprovalController.ApprovalSummary.from(approval);

        assertThat(summary.id()).isEqualTo(approvalId.toString());
        assertThat(summary.executionId()).isEqualTo(executionId.toString());
        assertThat(summary.nodeId()).isEqualTo("node-1");
        assertThat(summary.message()).isEqualTo("Approve?");
        assertThat(summary.approvalMode()).isEqualTo("any");
        assertThat(summary.requiredApprovers()).isEqualTo(1);
        assertThat(summary.expiresAt()).isNotNull();
        assertThat(summary.createdAt()).isNotNull();
    }

    @Test
    void approvalDetail_shouldMapAllFieldsIncludingActions() {
        UUID approvalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        ExecutionApproval approval = ExecutionApproval.builder()
                .id(approvalId)
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .approvalType("manual")
                .message("Approve?")
                .requiredApprovers(1)
                .approvalMode("any")
                .status("approved")
                .approvedCount(1)
                .rejectedCount(0)
                .createdAt(now)
                .resolvedAt(now.plusSeconds(60))
                .metadata(Map.of("key", "value"))
                .build();

        ApprovalAction action = ApprovalAction.builder()
                .id(UUID.randomUUID())
                .approvalId(approvalId)
                .userId(userId)
                .action("approve")
                .comment("OK")
                .createdAt(now.plusSeconds(30))
                .build();

        var detail = ApprovalController.ApprovalDetail.from(approval, List.of(action));

        assertThat(detail.id()).isEqualTo(approvalId.toString());
        assertThat(detail.status()).isEqualTo("approved");
        assertThat(detail.approvedCount()).isEqualTo(1);
        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().get(0).action()).isEqualTo("approve");
        assertThat(detail.actions().get(0).comment()).isEqualTo("OK");
        assertThat(detail.metadata()).containsEntry("key", "value");
    }

    @Test
    void approvalSummary_shouldHandleNullDates() {
        ExecutionApproval approval = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .approvalMode("any")
                .requiredApprovers(1)
                .approvedCount(0)
                .rejectedCount(0)
                .expiresAt(null)
                .createdAt(null)
                .build();

        var summary = ApprovalController.ApprovalSummary.from(approval);

        assertThat(summary.expiresAt()).isNull();
        assertThat(summary.createdAt()).isNull();
    }
}
