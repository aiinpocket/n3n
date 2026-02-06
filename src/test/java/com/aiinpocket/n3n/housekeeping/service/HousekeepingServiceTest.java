package com.aiinpocket.n3n.housekeeping.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.housekeeping.config.HousekeepingProperties;
import com.aiinpocket.n3n.housekeeping.entity.HousekeepingJob;
import com.aiinpocket.n3n.housekeeping.repository.ExecutionHistoryRepository;
import com.aiinpocket.n3n.housekeeping.repository.HousekeepingJobRepository;
import com.aiinpocket.n3n.housekeeping.repository.NodeExecutionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HousekeepingServiceTest extends BaseServiceTest {

    @Mock
    private HousekeepingProperties properties;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private NodeExecutionRepository nodeExecutionRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private ExecutionHistoryRepository executionHistoryRepository;

    @Mock
    private NodeExecutionHistoryRepository nodeExecutionHistoryRepository;

    @Mock
    private HousekeepingJobRepository jobRepository;

    @InjectMocks
    private HousekeepingService housekeepingService;

    private void setupDefaultProperties() {
        when(properties.getRetentionDays()).thenReturn(30);
        when(properties.getBatchSize()).thenReturn(100);
        when(properties.isArchiveToHistory()).thenReturn(false);
        when(properties.getHistoryRetentionDays()).thenReturn(365);
    }

    // ========== Run Cleanup Tests ==========

    @Test
    void runCleanup_noExpiredExecutions_completesWithZeroProcessed() {
        // Given
        setupDefaultProperties();
        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });
        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // When
        HousekeepingJob result = housekeepingService.runCleanup();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRecordsProcessed()).isEqualTo(0);
        verify(jobRepository, times(2)).save(any(HousekeepingJob.class));
    }

    @Test
    void runCleanup_alreadyRunning_returnsNull() {
        // Given
        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(true);

        // When
        HousekeepingJob result = housekeepingService.runCleanup();

        // Then
        assertThat(result).isNull();
        verify(executionRepository, never()).findByStatusInAndStartedAtBefore(any(), any(), any());
    }

    @Test
    void runCleanup_withExpiredExecutions_deletesDirectly() {
        // Given
        setupDefaultProperties();

        Execution expiredExecution = createExpiredExecution();

        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });
        // First batch has one item, then next batch is empty
        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(expiredExecution)))
                .thenReturn(new PageImpl<>(List.of()));

        // When
        HousekeepingJob result = housekeepingService.runCleanup();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRecordsProcessed()).isEqualTo(1);
        assertThat(result.getRecordsDeleted()).isEqualTo(1);
        assertThat(result.getRecordsArchived()).isEqualTo(0);
        verify(nodeExecutionRepository).deleteByExecutionId(expiredExecution.getId());
        verify(executionRepository).deleteById(expiredExecution.getId());
    }

    @Test
    void runCleanup_withArchiveEnabled_archivesInsteadOfDeleting() {
        // Given
        when(properties.getRetentionDays()).thenReturn(30);
        when(properties.getBatchSize()).thenReturn(100);
        when(properties.isArchiveToHistory()).thenReturn(true);
        when(properties.getHistoryRetentionDays()).thenReturn(365);

        Execution expiredExecution = createExpiredExecution();

        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(expiredExecution)))
                .thenReturn(new PageImpl<>(List.of()));

        when(flowVersionRepository.findById(expiredExecution.getFlowVersionId()))
                .thenReturn(Optional.empty());
        when(nodeExecutionRepository.findByExecutionId(expiredExecution.getId()))
                .thenReturn(List.of());

        // When
        HousekeepingJob result = housekeepingService.runCleanup();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRecordsArchived()).isEqualTo(1);
        assertThat(result.getRecordsDeleted()).isEqualTo(0);
        verify(executionHistoryRepository).save(any());
    }

    @Test
    void runCleanup_withHistoryRetention_cleansOldHistory() {
        // Given
        setupDefaultProperties();

        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });
        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(nodeExecutionHistoryRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(5);
        when(executionHistoryRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(3);

        // When
        housekeepingService.runCleanup();

        // Then
        verify(nodeExecutionHistoryRepository).deleteByArchivedAtBefore(any(Instant.class));
        verify(executionHistoryRepository).deleteByArchivedAtBefore(any(Instant.class));
    }

    @Test
    void runCleanup_historyRetentionZero_skipsHistoryCleanup() {
        // Given
        when(properties.getRetentionDays()).thenReturn(30);
        when(properties.getBatchSize()).thenReturn(100);
        when(properties.isArchiveToHistory()).thenReturn(false);
        when(properties.getHistoryRetentionDays()).thenReturn(0);

        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });
        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // When
        housekeepingService.runCleanup();

        // Then
        verify(nodeExecutionHistoryRepository, never()).deleteByArchivedAtBefore(any());
        verify(executionHistoryRepository, never()).deleteByArchivedAtBefore(any());
    }

    @Test
    void runCleanup_failureDuringProcessing_marksJobFailed() {
        // Given
        when(properties.getRetentionDays()).thenReturn(30);
        when(properties.getBatchSize()).thenReturn(100);
        when(properties.isArchiveToHistory()).thenReturn(false);
        when(jobRepository.existsByJobTypeAndStatus("execution_cleanup", "running")).thenReturn(false);
        when(jobRepository.save(any(HousekeepingJob.class))).thenAnswer(invocation -> {
            HousekeepingJob j = invocation.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });
        when(executionRepository.findByStatusInAndStartedAtBefore(anyList(), any(Instant.class), any()))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThatThrownBy(() -> housekeepingService.runCleanup())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        // Verify job was saved as failed
        verify(jobRepository, atLeast(2)).save(argThat(job ->
                job.getStatus() == null || "running".equals(job.getStatus()) || "failed".equals(job.getStatus())));
    }

    // ========== Archive Execution Tests ==========

    @Test
    void archiveExecution_withFlowInfo_createsDenormalizedHistory() {
        // Given
        UUID flowId = UUID.randomUUID();
        Execution execution = createExpiredExecution();

        FlowVersion flowVersion = FlowVersion.builder()
                .id(execution.getFlowVersionId())
                .flowId(flowId)
                .version("1.0.0")
                .build();

        Flow flow = Flow.builder()
                .id(flowId)
                .name("Test Flow")
                .build();

        NodeExecution nodeExec = NodeExecution.builder()
                .id(UUID.randomUUID())
                .executionId(execution.getId())
                .nodeId("node1")
                .status("completed")
                .build();

        when(flowVersionRepository.findById(execution.getFlowVersionId())).thenReturn(Optional.of(flowVersion));
        when(flowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(nodeExecutionRepository.findByExecutionId(execution.getId())).thenReturn(List.of(nodeExec));

        // When
        housekeepingService.archiveExecution(execution);

        // Then
        verify(executionHistoryRepository).save(argThat(h ->
                "Test Flow".equals(h.getFlowName()) && "1.0.0".equals(h.getFlowVersion())));
        verify(nodeExecutionHistoryRepository).save(any());
        verify(nodeExecutionRepository).deleteByExecutionId(execution.getId());
        verify(executionRepository).deleteById(execution.getId());
    }

    @Test
    void archiveExecution_withoutFlowVersion_setsNullFlowInfo() {
        // Given
        Execution execution = createExpiredExecution();

        when(flowVersionRepository.findById(execution.getFlowVersionId())).thenReturn(Optional.empty());
        when(nodeExecutionRepository.findByExecutionId(execution.getId())).thenReturn(List.of());

        // When
        housekeepingService.archiveExecution(execution);

        // Then
        verify(executionHistoryRepository).save(argThat(h ->
                h.getFlowName() == null && h.getFlowVersion() == null));
    }

    // ========== Delete Execution Tests ==========

    @Test
    void deleteExecution_deletesNodeExecutionsFirst() {
        // Given
        Execution execution = createExpiredExecution();

        // When
        housekeepingService.deleteExecution(execution);

        // Then
        var inOrder = inOrder(nodeExecutionRepository, executionRepository);
        inOrder.verify(nodeExecutionRepository).deleteByExecutionId(execution.getId());
        inOrder.verify(executionRepository).deleteById(execution.getId());
    }

    // ========== Cleanup Old History Tests ==========

    @Test
    void cleanupOldHistory_positiveRetention_deletesOldRecords() {
        // Given
        when(properties.getHistoryRetentionDays()).thenReturn(365);
        when(nodeExecutionHistoryRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(10);
        when(executionHistoryRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(5);

        // When
        int result = housekeepingService.cleanupOldHistory();

        // Then
        assertThat(result).isEqualTo(15);
    }

    @Test
    void cleanupOldHistory_zeroRetention_returnsZero() {
        // Given
        when(properties.getHistoryRetentionDays()).thenReturn(0);

        // When
        int result = housekeepingService.cleanupOldHistory();

        // Then
        assertThat(result).isEqualTo(0);
        verify(nodeExecutionHistoryRepository, never()).deleteByArchivedAtBefore(any());
    }

    // ========== Statistics Tests ==========

    @Test
    void getStatistics_returnsCorrectStats() {
        // Given
        when(executionRepository.count()).thenReturn(100L);
        when(executionHistoryRepository.count()).thenReturn(500L);
        when(properties.getRetentionDays()).thenReturn(30);
        when(properties.isArchiveToHistory()).thenReturn(true);
        when(properties.getHistoryRetentionDays()).thenReturn(365);

        HousekeepingJob lastJob = HousekeepingJob.builder()
                .id(UUID.randomUUID())
                .status("completed")
                .startedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .recordsProcessed(50)
                .build();

        when(jobRepository.findFirstByJobTypeOrderByStartedAtDesc("execution_cleanup"))
                .thenReturn(Optional.of(lastJob));

        // When
        Map<String, Object> stats = housekeepingService.getStatistics();

        // Then
        assertThat(stats).containsEntry("executionsCount", 100L);
        assertThat(stats).containsEntry("executionsHistoryCount", 500L);
        assertThat(stats).containsEntry("retentionDays", 30);
        assertThat(stats).containsEntry("archiveToHistory", true);
        assertThat(stats).containsEntry("lastJobStatus", "completed");
    }

    // ========== Helper Methods ==========

    private Execution createExpiredExecution() {
        return Execution.builder()
                .id(UUID.randomUUID())
                .flowVersionId(UUID.randomUUID())
                .status("completed")
                .startedAt(Instant.now().minus(60, ChronoUnit.DAYS))
                .completedAt(Instant.now().minus(60, ChronoUnit.DAYS))
                .durationMs(1500)
                .triggeredBy(UUID.randomUUID())
                .triggerType("manual")
                .build();
    }
}
