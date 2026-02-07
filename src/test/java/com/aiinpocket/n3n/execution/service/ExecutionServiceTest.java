package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.service.CredentialService;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.dto.NodeExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.NodeExecution;
import com.aiinpocket.n3n.execution.expression.N3nExpressionEvaluator;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.repository.NodeExecutionRepository;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.flow.service.DagParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExecutionServiceTest extends BaseServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private NodeExecutionRepository nodeExecutionRepository;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private DagParser dagParser;

    @Mock
    private StateManager stateManager;

    @Mock
    private ExecutionNotificationService notificationService;

    @Mock
    private NodeHandlerRegistry handlerRegistry;

    @Mock
    private N3nExpressionEvaluator expressionEvaluator;

    @Mock
    private CredentialService credentialService;

    @Mock
    private ActivityService activityService;

    @Mock
    private com.aiinpocket.n3n.flow.service.FlowShareService flowShareService;

    @InjectMocks
    private ExecutionService executionService;

    private UUID flowId;
    private UUID userId;
    private UUID versionId;
    private UUID executionId;
    private Flow testFlow;
    private FlowVersion testVersion;

    @BeforeEach
    void setUp() {
        flowId = UUID.randomUUID();
        userId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        testFlow = TestDataFactory.createFlow("Test Flow", userId);
        testFlow.setId(flowId);

        testVersion = TestDataFactory.createPublishedVersion(flowId, "1.0.0");
        testVersion.setId(versionId);

        // Default: user has access to the flow (owner)
        lenient().when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
    }

    @Nested
    @DisplayName("List Executions")
    class ListExecutions {

        @Test
        void listExecutions_returnsPaginatedResults() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .triggerType("manual")
                    .build();
            Page<Execution> page = new PageImpl<>(List.of(execution));

            when(executionRepository.findByTriggeredByOrderByStartedAtDesc(userId, pageable)).thenReturn(page);
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            Page<ExecutionResponse> result = executionService.listExecutions(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getFlowName()).isEqualTo("Test Flow");
        }

        @Test
        void listExecutions_emptyResult_returnsEmptyPage() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            when(executionRepository.findByTriggeredByOrderByStartedAtDesc(userId, pageable))
                    .thenReturn(Page.empty());

            // When
            Page<ExecutionResponse> result = executionService.listExecutions(userId, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        void listExecutionsByFlow_noVersions_returnsEmpty() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
            when(flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId))
                    .thenReturn(List.of());

            // When
            Page<ExecutionResponse> result = executionService.listExecutionsByFlow(flowId, userId, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        void listExecutionsByFlow_withPublishedVersion_queriesByPublished() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            FlowVersion draft = TestDataFactory.createFlowVersion(flowId, "2.0.0");
            draft.setId(UUID.randomUUID());

            when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
            when(flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId))
                    .thenReturn(List.of(draft, testVersion));
            when(executionRepository.findByFlowVersionIdOrderByStartedAtDesc(testVersion.getId(), pageable))
                    .thenReturn(Page.empty());

            // When
            executionService.listExecutionsByFlow(flowId, userId, pageable);

            // Then
            verify(executionRepository).findByFlowVersionIdOrderByStartedAtDesc(testVersion.getId(), pageable);
        }
    }

    @Nested
    @DisplayName("Get Execution")
    class GetExecution {

        @Test
        void getExecution_existing_returnsEnrichedResponse() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .triggerType("manual")
                    .startedAt(Instant.now().minusSeconds(10))
                    .completedAt(Instant.now())
                    .durationMs(10000)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.getExecution(executionId, userId);

            // Then
            assertThat(result.getId()).isEqualTo(executionId);
            assertThat(result.getStatus()).isEqualTo("completed");
            assertThat(result.getFlowName()).isEqualTo("Test Flow");
            assertThat(result.getFlowVersion()).isEqualTo("1.0.0");
        }

        @Test
        void getExecution_nonExisting_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.getExecution(executionId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Execution not found");
        }

        @Test
        void getExecution_noFlowVersion_returnsResponseWithoutFlowInfo() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("running")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.empty());

            // When
            ExecutionResponse result = executionService.getExecution(executionId, userId);

            // Then
            assertThat(result.getId()).isEqualTo(executionId);
            assertThat(result.getFlowName()).isNull();
        }
    }

    @Nested
    @DisplayName("Get Node Executions")
    class GetNodeExecutions {

        @Test
        void getNodeExecutions_existing_returnsList() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .build();

            NodeExecution ne1 = NodeExecution.builder()
                    .id(UUID.randomUUID())
                    .executionId(executionId)
                    .nodeId("node1")
                    .componentName("trigger")
                    .componentVersion("1.0.0")
                    .status("completed")
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .durationMs(5000)
                    .build();
            NodeExecution ne2 = NodeExecution.builder()
                    .id(UUID.randomUUID())
                    .executionId(executionId)
                    .nodeId("node2")
                    .componentName("action")
                    .componentVersion("1.0.0")
                    .status("completed")
                    .startedAt(Instant.now())
                    .completedAt(Instant.now())
                    .durationMs(1000)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(nodeExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId))
                    .thenReturn(List.of(ne1, ne2));

            // When
            List<NodeExecutionResponse> result = executionService.getNodeExecutions(executionId, userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getNodeId()).isEqualTo("node1");
            assertThat(result.get(1).getNodeId()).isEqualTo("node2");
        }

        @Test
        void getNodeExecutions_nonExistingExecution_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.getNodeExecutions(executionId, userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Cancel Execution")
    class CancelExecution {

        @Test
        void cancelExecution_runningExecution_cancelsSuccessfully() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("running")
                    .triggeredBy(userId)
                    .startedAt(Instant.now().minusSeconds(10))
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.cancelExecution(executionId, userId, "User cancelled");

            // Then
            assertThat(result.getStatus()).isEqualTo("cancelled");
            assertThat(result.getCancelReason()).isEqualTo("User cancelled");
            verify(stateManager).updateExecutionStatus(executionId, "cancelled");
            verify(notificationService).notifyExecutionCancelled(executionId, "User cancelled");
            verify(activityService).logExecutionCancel(userId, executionId, "User cancelled");
        }

        @Test
        void cancelExecution_pendingExecution_cancelsSuccessfully() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("pending")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.cancelExecution(executionId, userId, "Not needed");

            // Then
            assertThat(result.getStatus()).isEqualTo("cancelled");
        }

        @Test
        void cancelExecution_waitingExecution_cancelsSuccessfully() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("waiting")
                    .triggeredBy(userId)
                    .startedAt(Instant.now().minusSeconds(60))
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.cancelExecution(executionId, userId, "Timeout");

            // Then
            assertThat(result.getStatus()).isEqualTo("cancelled");
            assertThat(result.getDurationMs()).isNotNull();
        }

        @Test
        void cancelExecution_completedExecution_throwsException() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            // When/Then
            assertThatThrownBy(() -> executionService.cancelExecution(executionId, userId, "reason"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot cancel execution in status: completed");
        }

        @Test
        void cancelExecution_failedExecution_throwsException() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("failed")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            // When/Then
            assertThatThrownBy(() -> executionService.cancelExecution(executionId, userId, "reason"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void cancelExecution_nonExisting_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.cancelExecution(executionId, userId, "reason"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Get Execution Output")
    class GetExecutionOutput {

        @Test
        void getExecutionOutput_existingExecution_delegatesToStateManager() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .build();
            Map<String, Object> output = Map.of("node1", Map.of("result", "data"));
            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(stateManager.getExecutionOutput(executionId)).thenReturn(output);

            // When
            Map<String, Object> result = executionService.getExecutionOutput(executionId, userId);

            // Then
            assertThat(result).containsKey("node1");
            verify(stateManager).getExecutionOutput(executionId);
        }

        @Test
        void getExecutionOutput_nonExisting_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.getExecutionOutput(executionId, userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Retry Execution")
    class RetryExecution {

        @Test
        void retryExecution_failedExecution_createsRetry() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("failed")
                    .triggeredBy(userId)
                    .triggerInput(Map.of("key", "value"))
                    .triggerContext(Map.of("ctx", "data"))
                    .retryCount(0)
                    .maxRetries(3)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.retryExecution(executionId, userId);

            // Then
            assertThat(result.getRetryOf()).isEqualTo(executionId);
            assertThat(result.getRetryCount()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo("pending");
            verify(stateManager).initExecution(any(UUID.class), any());
            verify(activityService).logExecutionRetry(eq(userId), any(UUID.class), eq(executionId), eq(1));
        }

        @Test
        void retryExecution_cancelledExecution_createsRetry() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("cancelled")
                    .triggeredBy(userId)
                    .retryCount(1)
                    .maxRetries(3)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.retryExecution(executionId, userId);

            // Then
            assertThat(result.getRetryCount()).isEqualTo(2);
        }

        @Test
        void retryExecution_completedExecution_throwsException() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("completed")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));

            // When/Then
            assertThatThrownBy(() -> executionService.retryExecution(executionId, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Can only retry failed or cancelled executions");
        }

        @Test
        void retryExecution_runningExecution_throwsException() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("running")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));

            // When/Then
            assertThatThrownBy(() -> executionService.retryExecution(executionId, userId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void retryExecution_maxRetriesReached_throwsException() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("failed")
                    .triggeredBy(userId)
                    .retryCount(3)
                    .maxRetries(3)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));

            // When/Then
            assertThatThrownBy(() -> executionService.retryExecution(executionId, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Maximum retry count reached");
        }

        @Test
        void retryExecution_nullRetryCount_treatsAsZero() {
            // Given
            Execution original = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("failed")
                    .triggeredBy(userId)
                    .retryCount(null)
                    .maxRetries(null)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(original));
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.retryExecution(executionId, userId);

            // Then
            assertThat(result.getRetryCount()).isEqualTo(1);
            assertThat(result.getMaxRetries()).isEqualTo(3); // default
        }

        @Test
        void retryExecution_nonExisting_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.retryExecution(executionId, userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Resume Execution")
    class ResumeExecution {

        @Test
        void resumeExecution_waitingExecution_resumesSuccessfully() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("waiting")
                    .triggeredBy(userId)
                    .waitingNodeId("approval-node")
                    .build();
            Map<String, Object> resumeData = Map.of("approved", true);

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.resumeExecution(executionId, resumeData, userId);

            // Then
            assertThat(result).isNotNull();
            verify(activityService).logExecutionResume(userId, executionId, "approval-node");
        }

        @Test
        void resumeExecution_nonWaitingExecution_throwsException() {
            // Given
            Execution execution = Execution.builder()
                    .id(executionId)
                    .flowVersionId(versionId)
                    .status("running")
                    .triggeredBy(userId)
                    .build();

            when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

            // When/Then
            assertThatThrownBy(() -> executionService.resumeExecution(executionId, Map.of(), userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot resume execution in status: running");
        }

        @Test
        void resumeExecution_nonExisting_throwsException() {
            // Given
            when(executionRepository.findById(executionId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.resumeExecution(executionId, Map.of(), userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create Execution")
    class CreateExecution {

        @Test
        void createExecution_withSpecificVersion_usesSpecifiedVersion() {
            // Given
            CreateExecutionRequest request = new CreateExecutionRequest();
            request.setFlowId(flowId);
            request.setVersion("1.0.0");
            request.setInput(Map.of("key", "value"));

            DagParser.ParseResult parseResult = new DagParser.ParseResult();
            parseResult.setValid(true);
            parseResult.setErrors(List.of());
            parseResult.setWarnings(List.of());
            parseResult.setEntryPoints(List.of("node1"));
            parseResult.setExitPoints(List.of("node2"));
            parseResult.setExecutionOrder(List.of("node1", "node2"));
            parseResult.setDependencies(Map.of());

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                    .thenReturn(Optional.of(testVersion));
            when(dagParser.parse(any())).thenReturn(parseResult);
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.createExecution(request, userId);

            // Then
            assertThat(result.getStatus()).isEqualTo("pending");
            assertThat(result.getTriggerType()).isEqualTo("manual");
            verify(stateManager).initExecution(any(UUID.class), any());
            verify(activityService).logExecutionStart(eq(userId), any(UUID.class), eq(flowId), eq("Test Flow"), eq("manual"));
        }

        @Test
        void createExecution_noVersion_usesPublishedVersion() {
            // Given
            CreateExecutionRequest request = new CreateExecutionRequest();
            request.setFlowId(flowId);
            // version is null

            DagParser.ParseResult parseResult = new DagParser.ParseResult();
            parseResult.setValid(true);
            parseResult.setErrors(List.of());
            parseResult.setWarnings(List.of());
            parseResult.setEntryPoints(List.of("node1"));
            parseResult.setExitPoints(List.of("node2"));
            parseResult.setExecutionOrder(List.of("node1", "node2"));
            parseResult.setDependencies(Map.of());

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                    .thenReturn(Optional.of(testVersion));
            when(dagParser.parse(any())).thenReturn(parseResult);
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            executionService.createExecution(request, userId);

            // Then
            verify(flowVersionRepository).findByFlowIdAndStatus(flowId, "published");
        }

        @Test
        void createExecution_flowNotFound_throwsException() {
            // Given
            CreateExecutionRequest request = new CreateExecutionRequest();
            request.setFlowId(flowId);

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.createExecution(request, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Flow not found");
        }

        @Test
        void createExecution_versionNotFound_throwsException() {
            // Given
            CreateExecutionRequest request = new CreateExecutionRequest();
            request.setFlowId(flowId);
            request.setVersion("9.9.9");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "9.9.9"))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.createExecution(request, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Version not found");
        }

        @Test
        void createExecution_invalidDag_throwsException() {
            // Given
            CreateExecutionRequest request = new CreateExecutionRequest();
            request.setFlowId(flowId);
            request.setVersion("1.0.0");

            DagParser.ParseResult parseResult = new DagParser.ParseResult();
            parseResult.setValid(false);
            parseResult.setErrors(List.of("Cycle detected", "No entry point"));
            parseResult.setWarnings(List.of());
            parseResult.setEntryPoints(List.of());
            parseResult.setExitPoints(List.of());
            parseResult.setExecutionOrder(List.of());
            parseResult.setDependencies(Map.of());

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                    .thenReturn(Optional.of(testVersion));
            when(dagParser.parse(any())).thenReturn(parseResult);

            // When/Then
            assertThatThrownBy(() -> executionService.createExecution(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid flow definition");
        }
    }

    @Nested
    @DisplayName("Start Execution (Scheduler)")
    class StartExecution {

        @Test
        void startExecution_validFlow_createsExecution() {
            // Given
            Map<String, Object> triggerData = Map.of("source", "scheduler");

            DagParser.ParseResult parseResult = new DagParser.ParseResult();
            parseResult.setValid(true);
            parseResult.setErrors(List.of());
            parseResult.setWarnings(List.of());
            parseResult.setEntryPoints(List.of("node1"));
            parseResult.setExitPoints(List.of("node2"));
            parseResult.setExecutionOrder(List.of("node1", "node2"));
            parseResult.setDependencies(Map.of());

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                    .thenReturn(Optional.of(testVersion));
            when(dagParser.parse(any())).thenReturn(parseResult);
            when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(flowVersionRepository.findById(versionId)).thenReturn(Optional.of(testVersion));
            when(flowRepository.findById(flowId)).thenReturn(Optional.of(testFlow));

            // When
            ExecutionResponse result = executionService.startExecution(flowId, userId, triggerData);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("pending");
        }

        @Test
        void startExecution_noPublishedVersion_throwsException() {
            // Given
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> executionService.startExecution(flowId, userId, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No published version");
        }
    }

    @Nested
    @DisplayName("ExecutionResponse canRetry")
    class CanRetry {

        @Test
        void canRetry_failedWithRetriesLeft_returnsTrue() {
            Execution e = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("failed").retryCount(1).maxRetries(3)
                    .build();

            ExecutionResponse resp = ExecutionResponse.from(e);
            assertThat(resp.isCanRetry()).isTrue();
        }

        @Test
        void canRetry_failedMaxRetries_returnsFalse() {
            Execution e = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("failed").retryCount(3).maxRetries(3)
                    .build();

            ExecutionResponse resp = ExecutionResponse.from(e);
            assertThat(resp.isCanRetry()).isFalse();
        }

        @Test
        void canRetry_completedExecution_returnsFalse() {
            Execution e = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("completed").retryCount(0).maxRetries(3)
                    .build();

            ExecutionResponse resp = ExecutionResponse.from(e);
            assertThat(resp.isCanRetry()).isFalse();
        }

        @Test
        void canRetry_cancelledWithRetriesLeft_returnsTrue() {
            Execution e = Execution.builder()
                    .id(executionId).flowVersionId(versionId)
                    .status("cancelled").retryCount(0).maxRetries(3)
                    .build();

            ExecutionResponse resp = ExecutionResponse.from(e);
            assertThat(resp.isCanRetry()).isTrue();
        }
    }
}
