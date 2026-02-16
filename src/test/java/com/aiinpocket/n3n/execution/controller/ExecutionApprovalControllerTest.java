package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionApprovalControllerTest {

    @Mock
    private ExecutionApprovalService approvalService;

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private ExecutionApprovalController controller;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private ExecutionApproval sampleApproval(UUID executionId) {
        return ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId("node-1")
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

    private ApprovalAction sampleAction(UUID approvalId) {
        return ApprovalAction.builder()
                .id(UUID.randomUUID())
                .approvalId(approvalId)
                .userId(userId)
                .action("approve")
                .comment("Looks good")
                .createdAt(Instant.now())
                .build();
    }

    // ========== getApproval ==========

    @Test
    void getApproval_found_returnsApproval() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var actions = List.of(sampleAction(approval.getId()));

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.getActionsForApproval(approval.getId())).thenReturn(actions);

        var result = controller.getApproval(executionId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().executionId()).isEqualTo(executionId.toString());
        assertThat(result.getBody().nodeId()).isEqualTo("node-1");
        assertThat(result.getBody().status()).isEqualTo("pending");
        assertThat(result.getBody().actions()).hasSize(1);
    }

    @Test
    void getApproval_noPending_returnsNotFound() {
        var executionId = UUID.randomUUID();
        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.empty());

        var result = controller.getApproval(executionId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getApproval_noAccess_throwsException() {
        var executionId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Execution not found: " + executionId))
                .when(executionService).verifyExecutionAccess(executionId, userId);

        assertThatThrownBy(() -> controller.getApproval(executionId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(executionId.toString());
    }

    // ========== getApprovals ==========

    @Test
    void getApprovals_returnsAllApprovals() {
        var executionId = UUID.randomUUID();
        var approval1 = sampleApproval(executionId);
        var approval2 = sampleApproval(executionId);
        approval2.setNodeId("node-2");
        approval2.setStatus("approved");

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getApprovalsForExecution(executionId)).thenReturn(List.of(approval1, approval2));
        when(approvalService.getActionsForApproval(any())).thenReturn(List.of());

        var result = controller.getApprovals(executionId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).nodeId()).isEqualTo("node-1");
        assertThat(result.getBody().get(1).nodeId()).isEqualTo("node-2");
    }

    @Test
    void getApprovals_empty_returnsEmptyList() {
        var executionId = UUID.randomUUID();
        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getApprovalsForExecution(executionId)).thenReturn(List.of());

        var result = controller.getApprovals(executionId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getApprovals_noAccess_throwsException() {
        var executionId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Execution not found: " + executionId))
                .when(executionService).verifyExecutionAccess(executionId, userId);

        assertThatThrownBy(() -> controller.getApprovals(executionId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== submitApproval ==========

    @Test
    void submitApproval_approve_returnsApprovedAndResumesExecution() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var approvedApproval = sampleApproval(executionId);
        approvedApproval.setId(approval.getId());
        approvedApproval.setStatus("approved");
        approvedApproval.setApprovedCount(1);
        approvedApproval.setResolvedAt(Instant.now());

        var request = new ExecutionApprovalController.ApprovalRequest("approve", "Looks good");

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.submitApproval(approval.getId(), userId, "approve", "Looks good"))
                .thenReturn(approvedApproval);
        when(approvalService.getActionsForApproval(approvedApproval.getId())).thenReturn(List.of());

        var result = controller.submitApproval(executionId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().status()).isEqualTo("approved");

        // Verify execution was auto-resumed
        verify(executionService).resumeExecution(eq(executionId), argThat(data -> {
            assertThat(data.get("approvalStatus")).isEqualTo("approved");
            assertThat(data.get("approvedBy")).isEqualTo(userId.toString());
            assertThat(data.get("comment")).isEqualTo("Looks good");
            return true;
        }), eq(userId));
    }

    @Test
    void submitApproval_reject_returnsRejectedAndResumesExecution() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var rejectedApproval = sampleApproval(executionId);
        rejectedApproval.setId(approval.getId());
        rejectedApproval.setStatus("rejected");
        rejectedApproval.setRejectedCount(1);
        rejectedApproval.setResolvedAt(Instant.now());

        var request = new ExecutionApprovalController.ApprovalRequest("reject", "Not ready");

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.submitApproval(approval.getId(), userId, "reject", "Not ready"))
                .thenReturn(rejectedApproval);
        when(approvalService.getActionsForApproval(rejectedApproval.getId())).thenReturn(List.of());

        var result = controller.submitApproval(executionId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("rejected");

        verify(executionService).resumeExecution(eq(executionId), argThat(data -> {
            assertThat(data.get("approvalStatus")).isEqualTo("rejected");
            assertThat(data.get("rejectedBy")).isEqualTo(userId.toString());
            return true;
        }), eq(userId));
    }

    @Test
    void submitApproval_stillPending_doesNotResumeExecution() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        // Still pending (not resolved)
        var updatedApproval = sampleApproval(executionId);
        updatedApproval.setId(approval.getId());
        updatedApproval.setStatus("pending");
        updatedApproval.setApprovedCount(1);

        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.submitApproval(approval.getId(), userId, "approve", null))
                .thenReturn(updatedApproval);
        when(approvalService.getActionsForApproval(updatedApproval.getId())).thenReturn(List.of());

        var result = controller.submitApproval(executionId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService, never()).resumeExecution(any(), any(), any());
    }

    @Test
    void submitApproval_cancelled_doesNotResumeExecution() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var cancelledApproval = sampleApproval(executionId);
        cancelledApproval.setId(approval.getId());
        cancelledApproval.setStatus("cancelled");
        cancelledApproval.setResolvedAt(Instant.now());

        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.submitApproval(approval.getId(), userId, "approve", null))
                .thenReturn(cancelledApproval);
        when(approvalService.getActionsForApproval(cancelledApproval.getId())).thenReturn(List.of());

        var result = controller.submitApproval(executionId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService, never()).resumeExecution(any(), any(), any());
    }

    @Test
    void submitApproval_noPendingApproval_throwsException() {
        var executionId = UUID.randomUUID();
        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.submitApproval(executionId, request, testUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pending approval found");
    }

    @Test
    void submitApproval_noAccess_throwsException() {
        var executionId = UUID.randomUUID();
        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doThrow(new ResourceNotFoundException("Execution not found: " + executionId))
                .when(executionService).verifyExecutionAccess(executionId, userId);

        assertThatThrownBy(() -> controller.submitApproval(executionId, request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitApproval_notAuthorized_throwsException() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.submitApproval(executionId, request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(approvalService, never()).submitApproval(any(), any(), any(), any());
    }

    @Test
    void submitApproval_approveWithNoComment_doesNotIncludeComment() {
        var executionId = UUID.randomUUID();
        var approval = sampleApproval(executionId);
        var approvedApproval = sampleApproval(executionId);
        approvedApproval.setId(approval.getId());
        approvedApproval.setStatus("approved");
        approvedApproval.setApprovedCount(1);
        approvedApproval.setResolvedAt(Instant.now());

        var request = new ExecutionApprovalController.ApprovalRequest("approve", null);

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(approvalService.getPendingApprovalForExecution(executionId)).thenReturn(Optional.of(approval));
        when(approvalService.isUserAuthorizedForApproval(approval, userId)).thenReturn(true);
        when(approvalService.submitApproval(approval.getId(), userId, "approve", null))
                .thenReturn(approvedApproval);
        when(approvalService.getActionsForApproval(approvedApproval.getId())).thenReturn(List.of());

        controller.submitApproval(executionId, request, testUser());

        verify(executionService).resumeExecution(eq(executionId), argThat(data ->
                !data.containsKey("comment")), eq(userId));
    }

    // ========== resumeExecution ==========

    @Test
    void resumeExecution_withData_returnsResponse() {
        var executionId = UUID.randomUUID();
        var resumeData = Map.<String, Object>of("key", "value");
        var executionResponse = ExecutionResponse.builder()
                .id(executionId)
                .status("running")
                .build();

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(executionService.resumeExecution(executionId, resumeData, userId)).thenReturn(executionResponse);

        var result = controller.resumeExecution(executionId, resumeData, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(executionId);
        assertThat(result.getBody().getStatus()).isEqualTo("running");
    }

    @Test
    void resumeExecution_nullData_passesEmptyMap() {
        var executionId = UUID.randomUUID();
        var executionResponse = ExecutionResponse.builder()
                .id(executionId)
                .status("running")
                .build();

        doNothing().when(executionService).verifyExecutionAccess(executionId, userId);
        when(executionService.resumeExecution(eq(executionId), eq(Map.of()), eq(userId)))
                .thenReturn(executionResponse);

        var result = controller.resumeExecution(executionId, null, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).resumeExecution(eq(executionId), eq(Map.of()), eq(userId));
    }

    @Test
    void resumeExecution_noAccess_throwsException() {
        var executionId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Execution not found: " + executionId))
                .when(executionService).verifyExecutionAccess(executionId, userId);

        assertThatThrownBy(() -> controller.resumeExecution(executionId, null, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== ApprovalResponse.from ==========

    @Test
    void approvalResponse_from_mapsAllFields() {
        var executionId = UUID.randomUUID();
        var approval = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId("node-1")
                .approvalType("manual")
                .message("Please approve")
                .requiredApprovers(2)
                .approvalMode("all")
                .status("pending")
                .approvedCount(1)
                .rejectedCount(0)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .metadata(Map.of("key", "value"))
                .build();

        var action = ApprovalAction.builder()
                .id(UUID.randomUUID())
                .approvalId(approval.getId())
                .userId(userId)
                .action("approve")
                .comment("OK")
                .createdAt(Instant.now())
                .build();

        var response = ExecutionApprovalController.ApprovalResponse.from(approval, List.of(action));

        assertThat(response.id()).isEqualTo(approval.getId().toString());
        assertThat(response.executionId()).isEqualTo(executionId.toString());
        assertThat(response.nodeId()).isEqualTo("node-1");
        assertThat(response.approvalType()).isEqualTo("manual");
        assertThat(response.message()).isEqualTo("Please approve");
        assertThat(response.requiredApprovers()).isEqualTo(2);
        assertThat(response.approvalMode()).isEqualTo("all");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.approvedCount()).isEqualTo(1);
        assertThat(response.rejectedCount()).isEqualTo(0);
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.metadata()).containsEntry("key", "value");
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).action()).isEqualTo("approve");
        assertThat(response.actions().get(0).comment()).isEqualTo("OK");
    }

    @Test
    void approvalResponse_from_handlesNullTimestamps() {
        var approval = ExecutionApproval.builder()
                .id(UUID.randomUUID())
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .status("pending")
                .expiresAt(null)
                .createdAt(null)
                .resolvedAt(null)
                .build();

        var response = ExecutionApprovalController.ApprovalResponse.from(approval, List.of());

        assertThat(response.expiresAt()).isNull();
        assertThat(response.createdAt()).isNull();
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.actions()).isEmpty();
    }
}
