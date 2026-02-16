package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExecutionRecoveryServiceTest extends BaseServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private StateManager stateManager;

    @Mock
    private ExecutionNotificationService notificationService;

    @InjectMocks
    private ExecutionRecoveryService recoveryService;

    @Test
    void shouldMarkRunningExecutionsAsFailedOnStartup() {
        Execution running = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(Instant.now().minusSeconds(60))
                .build();

        when(executionRepository.findTop1000ByStatus("running")).thenReturn(List.of(running));
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(Collections.emptyList());
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recoveryService.recoverStaleExecutions();

        verify(executionRepository).save(argThat(exec ->
                "failed".equals(exec.getStatus()) && exec.getCompletedAt() != null));
        verify(stateManager).updateExecutionStatus(eq(running.getId()), eq("failed"));
        verify(notificationService).notifyExecutionFailed(eq(running.getId()), anyString());
    }

    @Test
    void shouldMarkPendingExecutionsAsFailedOnStartup() {
        Execution pending = Execution.builder()
                .id(UUID.randomUUID())
                .status("pending")
                .build();

        when(executionRepository.findTop1000ByStatus("running")).thenReturn(Collections.emptyList());
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(List.of(pending));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recoveryService.recoverStaleExecutions();

        verify(executionRepository).save(argThat(exec ->
                "failed".equals(exec.getStatus()) && exec.getCompletedAt() != null));
    }

    @Test
    void shouldDoNothingWhenNoStaleExecutions() {
        when(executionRepository.findTop1000ByStatus("running")).thenReturn(Collections.emptyList());
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(Collections.emptyList());

        recoveryService.recoverStaleExecutions();

        verify(executionRepository, never()).save(any());
        verify(stateManager, never()).updateExecutionStatus(any(), any());
    }

    @Test
    void shouldHandleRecoveryOfMultipleExecutions() {
        Execution running1 = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(Instant.now().minusSeconds(120))
                .build();
        Execution running2 = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(Instant.now().minusSeconds(60))
                .build();
        Execution pending1 = Execution.builder()
                .id(UUID.randomUUID())
                .status("pending")
                .build();

        when(executionRepository.findTop1000ByStatus("running")).thenReturn(List.of(running1, running2));
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(List.of(pending1));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recoveryService.recoverStaleExecutions();

        verify(executionRepository, times(3)).save(any());
        verify(stateManager, times(3)).updateExecutionStatus(any(), eq("failed"));
    }

    @Test
    void shouldContinueEvenIfOneRecoveryFails() {
        Execution running1 = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(Instant.now().minusSeconds(60))
                .build();
        Execution running2 = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(Instant.now().minusSeconds(30))
                .build();

        when(executionRepository.findTop1000ByStatus("running")).thenReturn(List.of(running1, running2));
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(Collections.emptyList());
        when(executionRepository.save(running1)).thenThrow(new RuntimeException("DB error"));
        when(executionRepository.save(running2)).thenReturn(running2);

        // Should not throw
        recoveryService.recoverStaleExecutions();

        verify(executionRepository, times(2)).save(any());
    }

    @Test
    void shouldCalculateDurationMsForRunningExecutions() {
        Instant startedAt = Instant.now().minusSeconds(120);
        Execution running = Execution.builder()
                .id(UUID.randomUUID())
                .status("running")
                .startedAt(startedAt)
                .build();

        when(executionRepository.findTop1000ByStatus("running")).thenReturn(List.of(running));
        when(executionRepository.findTop1000ByStatus("pending")).thenReturn(Collections.emptyList());
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recoveryService.recoverStaleExecutions();

        verify(executionRepository).save(argThat(exec ->
                exec.getDurationMs() != null && exec.getDurationMs() > 100000));
    }
}
