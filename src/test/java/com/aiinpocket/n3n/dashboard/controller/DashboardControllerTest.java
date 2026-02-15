package com.aiinpocket.n3n.dashboard.controller;

import com.aiinpocket.n3n.dashboard.dto.DashboardStatsResponse;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @InjectMocks
    private DashboardController dashboardController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    // ===== getStats (GET /api/dashboard/stats) =====

    @Test
    void getStats_success_returnsAllStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(10L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(50L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(35L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(10L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(5L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalFlows()).isEqualTo(10L);
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(50L);
        assertThat(result.getBody().getSuccessfulExecutions()).isEqualTo(35L);
        assertThat(result.getBody().getFailedExecutions()).isEqualTo(10L);
        assertThat(result.getBody().getRunningExecutions()).isEqualTo(5L);
    }

    @Test
    void getStats_allZeros_returnsZeroStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalFlows()).isZero();
        assertThat(result.getBody().getTotalExecutions()).isZero();
        assertThat(result.getBody().getSuccessfulExecutions()).isZero();
        assertThat(result.getBody().getFailedExecutions()).isZero();
        assertThat(result.getBody().getRunningExecutions()).isZero();
    }

    @Test
    void getStats_onlyFlowsNoExecutions_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(25L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalFlows()).isEqualTo(25L);
        assertThat(result.getBody().getTotalExecutions()).isZero();
        assertThat(result.getBody().getSuccessfulExecutions()).isZero();
        assertThat(result.getBody().getFailedExecutions()).isZero();
        assertThat(result.getBody().getRunningExecutions()).isZero();
    }

    @Test
    void getStats_onlyExecutionsNoFlows_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(100L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(80L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(15L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(5L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalFlows()).isZero();
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(100L);
        assertThat(result.getBody().getSuccessfulExecutions()).isEqualTo(80L);
        assertThat(result.getBody().getFailedExecutions()).isEqualTo(15L);
        assertThat(result.getBody().getRunningExecutions()).isEqualTo(5L);
    }

    @Test
    void getStats_allExecutionsSuccessful_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(5L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(200L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(200L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(200L);
        assertThat(result.getBody().getSuccessfulExecutions()).isEqualTo(200L);
        assertThat(result.getBody().getFailedExecutions()).isZero();
        assertThat(result.getBody().getRunningExecutions()).isZero();
    }

    @Test
    void getStats_allExecutionsFailed_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(3L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(50L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(50L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(50L);
        assertThat(result.getBody().getSuccessfulExecutions()).isZero();
        assertThat(result.getBody().getFailedExecutions()).isEqualTo(50L);
        assertThat(result.getBody().getRunningExecutions()).isZero();
    }

    @Test
    void getStats_allExecutionsRunning_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(2L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(10L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(10L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(10L);
        assertThat(result.getBody().getSuccessfulExecutions()).isZero();
        assertThat(result.getBody().getFailedExecutions()).isZero();
        assertThat(result.getBody().getRunningExecutions()).isEqualTo(10L);
    }

    @Test
    void getStats_largeNumbers_returnsCorrectStats() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(999_999L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(10_000_000L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(9_500_000L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(450_000L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(50_000L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalFlows()).isEqualTo(999_999L);
        assertThat(result.getBody().getTotalExecutions()).isEqualTo(10_000_000L);
        assertThat(result.getBody().getSuccessfulExecutions()).isEqualTo(9_500_000L);
        assertThat(result.getBody().getFailedExecutions()).isEqualTo(450_000L);
        assertThat(result.getBody().getRunningExecutions()).isEqualTo(50_000L);
    }

    // ===== userId extraction verification =====

    @Test
    void getStats_extractsUserIdFromUserDetails() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(1L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(1L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(1L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        dashboardController.getStats(user);

        verify(flowRepository).countByCreatedByAndIsDeletedFalse(eq(userId));
        verify(executionRepository).countByTriggeredBy(eq(userId));
        verify(executionRepository).countByTriggeredByAndStatus(eq(userId), eq("completed"));
        verify(executionRepository).countByTriggeredByAndStatus(eq(userId), eq("failed"));
        verify(executionRepository).countByTriggeredByAndStatus(eq(userId), eq("running"));
    }

    @Test
    void getStats_differentUsers_queryWithRespectiveUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        var user1 = testUserWithId(userId1);
        var user2 = testUserWithId(userId2);

        when(flowRepository.countByCreatedByAndIsDeletedFalse(any())).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(any())).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(any(), any())).thenReturn(0L);

        dashboardController.getStats(user1);
        dashboardController.getStats(user2);

        verify(flowRepository).countByCreatedByAndIsDeletedFalse(userId1);
        verify(flowRepository).countByCreatedByAndIsDeletedFalse(userId2);
        verify(executionRepository).countByTriggeredBy(userId1);
        verify(executionRepository).countByTriggeredBy(userId2);
    }

    // ===== Repository interaction verification =====

    @Test
    void getStats_callsAllRepositoryMethods() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        dashboardController.getStats(user);

        verify(flowRepository, times(1)).countByCreatedByAndIsDeletedFalse(userId);
        verify(executionRepository, times(1)).countByTriggeredBy(userId);
        verify(executionRepository, times(1)).countByTriggeredByAndStatus(userId, "completed");
        verify(executionRepository, times(1)).countByTriggeredByAndStatus(userId, "failed");
        verify(executionRepository, times(1)).countByTriggeredByAndStatus(userId, "running");
        verifyNoMoreInteractions(flowRepository);
        verifyNoMoreInteractions(executionRepository);
    }

    // ===== Error/exception handling =====

    @Test
    void getStats_flowRepositoryThrowsException_propagatesException() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId))
                .thenThrow(new RuntimeException("Database connection failed"));

        assertThatThrownBy(() -> dashboardController.getStats(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");
    }

    @Test
    void getStats_executionRepositoryThrowsException_propagatesException() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(5L);
        when(executionRepository.countByTriggeredBy(userId))
                .thenThrow(new RuntimeException("Query timeout"));

        assertThatThrownBy(() -> dashboardController.getStats(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Query timeout");
    }

    @Test
    void getStats_statusCountThrowsException_propagatesException() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(5L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(10L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed"))
                .thenThrow(new RuntimeException("Unexpected error in status count"));

        assertThatThrownBy(() -> dashboardController.getStats(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error in status count");
    }

    // ===== Response structure verification =====

    @Test
    void getStats_responseIsBuiltCorrectly() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(7L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(42L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(30L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(8L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(4L);

        var result = dashboardController.getStats(user);

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);

        DashboardStatsResponse body = result.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTotalFlows()).isEqualTo(7L);
        assertThat(body.getTotalExecutions()).isEqualTo(42L);
        assertThat(body.getSuccessfulExecutions()).isEqualTo(30L);
        assertThat(body.getFailedExecutions()).isEqualTo(8L);
        assertThat(body.getRunningExecutions()).isEqualTo(4L);
    }

    @Test
    void getStats_responseBodyIsNotNull() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(0L);

        var result = dashboardController.getStats(user);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isInstanceOf(DashboardStatsResponse.class);
    }

    // ===== Status string verification =====

    @Test
    void getStats_usesCorrectStatusStrings() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(eq(userId), eq("completed"))).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(eq(userId), eq("failed"))).thenReturn(0L);
        when(executionRepository.countByTriggeredByAndStatus(eq(userId), eq("running"))).thenReturn(0L);

        dashboardController.getStats(user);

        verify(executionRepository).countByTriggeredByAndStatus(userId, "completed");
        verify(executionRepository).countByTriggeredByAndStatus(userId, "failed");
        verify(executionRepository).countByTriggeredByAndStatus(userId, "running");
        verify(executionRepository, never()).countByTriggeredByAndStatus(userId, "pending");
        verify(executionRepository, never()).countByTriggeredByAndStatus(userId, "cancelled");
    }

    // ===== Mixed scenario =====

    @Test
    void getStats_mixedExecutionStatuses_returnsCorrectBreakdown() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        when(flowRepository.countByCreatedByAndIsDeletedFalse(userId)).thenReturn(15L);
        when(executionRepository.countByTriggeredBy(userId)).thenReturn(100L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "completed")).thenReturn(60L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "failed")).thenReturn(25L);
        when(executionRepository.countByTriggeredByAndStatus(userId, "running")).thenReturn(15L);

        var result = dashboardController.getStats(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardStatsResponse stats = result.getBody();
        assertThat(stats).isNotNull();

        // Verify breakdown sums to total (in this case: 60 + 25 + 15 = 100)
        assertThat(stats.getSuccessfulExecutions() + stats.getFailedExecutions() + stats.getRunningExecutions())
                .isEqualTo(stats.getTotalExecutions());
    }
}
