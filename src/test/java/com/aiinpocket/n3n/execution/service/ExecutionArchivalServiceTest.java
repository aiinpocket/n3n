package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.ExecutionArchive;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.repository.ExecutionArchiveRepository;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutionArchivalServiceTest extends BaseServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private ExecutionArchiveRepository archiveRepository;

    @Mock
    private NodeExecutionRepository nodeExecutionRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private StateManager stateManager;

    @InjectMocks
    private ExecutionArchivalService archivalService;

    private UUID flowId;
    private UUID versionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        flowId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        ReflectionTestUtils.setField(archivalService, "retentionDays", 30);
    }

    @Nested
    @DisplayName("Archive Execution")
    class ArchiveExecution {

        @Test
        void archiveExecution_withFlowInfo_createsArchiveWithFlowDetails() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggerInput(Map.of("key", "value"))
                    .startedAt(Instant.now().minusSeconds(60))
                    .completedAt(Instant.now())
                    .durationMs(60000)
                    .triggeredBy(UUID.randomUUID())
                    .triggerType("manual")
                    .build();

            FlowVersion version = FlowVersion.builder()
                    .id(versionId).flowId(flowId).version("1.0.0").build();
            Flow flow = Flow.builder().id(flowId).name("My Flow").build();

            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(flow));
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId))
                    .thenReturn(List.of());
            when(stateManager.getExecutionOutput(executionId)).thenReturn(Map.of("output", "data"));
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            archivalService.archiveExecution(execution);

            // Then
            ArgumentCaptor<ExecutionArchive> captor = ArgumentCaptor.forClass(ExecutionArchive.class);
            verify(archiveRepository).save(captor.capture());
            ExecutionArchive archive = captor.getValue();

            assertThat(archive.getId()).isEqualTo(executionId);
            assertThat(archive.getFlowName()).isEqualTo("My Flow");
            assertThat(archive.getFlowVersion()).isEqualTo("1.0.0");
            assertThat(archive.getStatus()).isEqualTo("completed");
            assertThat(archive.getOutput()).containsEntry("output", "data");

            verify(nodeExecutionRepository).deleteByExecutionId(executionId);
            verify(executionRepository).delete(execution);
            verify(stateManager).cleanupExecution(executionId);
        }

        @Test
        void archiveExecution_withNodeExecutions_includesNodeData() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("completed").build();

            NodeExecution ne = NodeExecution.builder()
                    .nodeId("node1").status("completed")
                    .componentName("httpRequest").componentVersion("1.0.0")
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .durationMs(5000)
                    .build();

            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.empty());
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId))
                    .thenReturn(List.of(ne));
            when(stateManager.getExecutionOutput(executionId)).thenReturn(Map.of());
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            archivalService.archiveExecution(execution);

            // Then
            ArgumentCaptor<ExecutionArchive> captor = ArgumentCaptor.forClass(ExecutionArchive.class);
            verify(archiveRepository).save(captor.capture());
            ExecutionArchive archive = captor.getValue();

            assertThat(archive.getNodeExecutions()).containsKey("node1");
            assertThat(archive.getFlowName()).isNull();
            assertThat(archive.getFlowVersion()).isNull();
        }

        @Test
        void archiveExecution_noFlowVersion_archivesWithoutFlowInfo() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("failed").build();

            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.empty());
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId))
                    .thenReturn(List.of());
            when(stateManager.getExecutionOutput(executionId)).thenReturn(Map.of());
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            archivalService.archiveExecution(execution);

            // Then
            verify(archiveRepository).save(argThat(a ->
                    a.getFlowName() == null && a.getFlowVersion() == null
            ));
        }

        @Test
        void archiveExecution_cleanupIsCalled() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("completed").build();

            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.empty());
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId))
                    .thenReturn(List.of());
            when(stateManager.getExecutionOutput(executionId)).thenReturn(Map.of());
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            archivalService.archiveExecution(execution);

            // Then
            verify(nodeExecutionRepository).deleteByExecutionId(executionId);
            verify(executionRepository).delete(execution);
            verify(stateManager).cleanupExecution(executionId);
        }
    }

    @Nested
    @DisplayName("Archive Old Executions (Batch)")
    class ArchiveOldExecutions {

        @Test
        void archiveOldExecutions_noOldExecutions_doesNothing() {
            // Given
            when(executionRepository.findByCompletedAtBeforeAndStatusIn(any(), any(), eq(100)))
                    .thenReturn(List.of());

            // When
            archivalService.archiveOldExecutions();

            // Then
            verify(archiveRepository, never()).save(any());
        }

        @Test
        void archiveOldExecutions_withOldExecutions_archivesThem() {
            // Given
            Execution old1 = Execution.builder()
                    .id(UUID.randomUUID()).flowVersionId(versionId)
                    .status("completed")
                    .completedAt(Instant.now().minus(60, ChronoUnit.DAYS))
                    .build();
            Execution old2 = Execution.builder()
                    .id(UUID.randomUUID()).flowVersionId(versionId)
                    .status("failed")
                    .completedAt(Instant.now().minus(45, ChronoUnit.DAYS))
                    .build();

            when(executionRepository.findByCompletedAtBeforeAndStatusIn(any(), any(), eq(100)))
                    .thenReturn(List.of(old1, old2))
                    .thenReturn(List.of()); // second call returns empty to stop loop

            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.empty());
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(any()))
                    .thenReturn(List.of());
            when(stateManager.getExecutionOutput(any())).thenReturn(Map.of());
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            archivalService.archiveOldExecutions();

            // Then
            verify(archiveRepository, times(2)).save(any(ExecutionArchive.class));
        }

        @Test
        void archiveOldExecutions_archivalFailure_continuesWithNext() {
            // Given
            Execution good = Execution.builder()
                    .id(UUID.randomUUID()).flowVersionId(versionId)
                    .status("completed").build();
            Execution bad = Execution.builder()
                    .id(UUID.randomUUID()).flowVersionId(versionId)
                    .status("completed").build();

            when(executionRepository.findByCompletedAtBeforeAndStatusIn(any(), any(), eq(100)))
                    .thenReturn(List.of(bad, good))
                    .thenReturn(List.of());

            // First call throws, second succeeds
            when(flowVersionRepository.findById(versionId))
                    .thenThrow(new RuntimeException("DB error"))
                    .thenReturn(Optional.empty());
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(any()))
                    .thenReturn(List.of());
            when(stateManager.getExecutionOutput(any())).thenReturn(Map.of());
            when(archiveRepository.save(any(ExecutionArchive.class))).thenAnswer(inv -> inv.getArgument(0));

            // When - should not throw
            archivalService.archiveOldExecutions();

            // Then - at least one archive attempt was made
            verify(executionRepository, atLeastOnce()).findByCompletedAtBeforeAndStatusIn(any(), any(), eq(100));
        }
    }

    @Nested
    @DisplayName("Cleanup Old Archives")
    class CleanupOldArchives {

        @Test
        void cleanupOldArchives_deletesOldArchives() {
            // Given
            when(archiveRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(42);

            // When
            archivalService.cleanupOldArchives();

            // Then
            verify(archiveRepository).deleteByArchivedAtBefore(any(Instant.class));
        }

        @Test
        void cleanupOldArchives_noOldArchives_returnsZero() {
            // Given
            when(archiveRepository.deleteByArchivedAtBefore(any(Instant.class))).thenReturn(0);

            // When
            archivalService.cleanupOldArchives();

            // Then
            verify(archiveRepository).deleteByArchivedAtBefore(any(Instant.class));
        }
    }
}
