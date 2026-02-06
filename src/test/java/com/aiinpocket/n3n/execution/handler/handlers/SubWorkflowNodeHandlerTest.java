package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubWorkflowNodeHandlerTest {

    @Mock
    private ObjectProvider<ExecutionService> executionServiceProvider;

    @Mock
    private ExecutionService executionService;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private ExecutionRepository executionRepository;

    private SubWorkflowNodeHandler handler;

    private UUID testFlowId;
    private UUID testUserId;
    private UUID testExecutionId;

    @BeforeEach
    void setUp() {
        handler = new SubWorkflowNodeHandler(executionServiceProvider, flowRepository, executionRepository);
        testFlowId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testExecutionId = UUID.randomUUID();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSubWorkflow() {
        assertThat(handler.getType()).isEqualTo("subWorkflow");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsExpected() {
        assertThat(handler.getDisplayName()).isEqualTo("Execute Sub-workflow");
    }

    // ========== Validation ==========

    @Test
    void execute_missingWorkflowId_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Workflow ID is required");
    }

    @Test
    void execute_emptyWorkflowId_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("workflowId", ""));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Workflow ID is required");
    }

    @Test
    void execute_invalidWorkflowIdFormat_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("workflowId", "not-a-uuid"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid workflow ID format");
    }

    @Test
    void execute_subFlowNotFound_returnsFailure() {
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.empty());

        NodeExecutionContext context = buildContext(Map.of("workflowId", testFlowId.toString()));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Sub-workflow not found");
    }

    @Test
    void execute_executionServiceNotAvailable_returnsFailure() {
        Flow subFlow = new Flow();
        subFlow.setId(testFlowId);
        subFlow.setName("Sub Flow");
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.of(subFlow));
        when(executionServiceProvider.getIfAvailable()).thenReturn(null);

        NodeExecutionContext context = buildContext(Map.of("workflowId", testFlowId.toString()));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("ExecutionService not available");
    }

    // ========== Async Execution (waitForCompletion=false) ==========

    @Test
    void execute_noWait_returnsTriggeredStatus() {
        Flow subFlow = new Flow();
        subFlow.setId(testFlowId);
        subFlow.setName("Sub Flow");
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.of(subFlow));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);

        ExecutionResponse execResponse = ExecutionResponse.builder().id(testExecutionId).build();
        when(executionService.startExecution(eq(testFlowId), eq(testUserId), any())).thenReturn(execResponse);

        NodeExecutionContext context = buildContext(Map.of(
                "workflowId", testFlowId.toString(),
                "waitForCompletion", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", "triggered");
        assertThat(result.getOutput()).containsEntry("subFlowName", "Sub Flow");
        assertThat(result.getOutput()).containsKey("subExecutionId");
    }

    // ========== Sync Execution (waitForCompletion=true) ==========

    @Test
    void execute_waitForCompletion_completedFlow_returnsSuccess() {
        Flow subFlow = new Flow();
        subFlow.setId(testFlowId);
        subFlow.setName("Sub Flow");
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.of(subFlow));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);

        ExecutionResponse execResponse = ExecutionResponse.builder().id(testExecutionId).build();
        when(executionService.startExecution(eq(testFlowId), eq(testUserId), any())).thenReturn(execResponse);

        Execution execution = new Execution();
        execution.setId(testExecutionId);
        execution.setStatus("completed");
        execution.setDurationMs(1234);
        when(executionRepository.findById(testExecutionId)).thenReturn(Optional.of(execution));

        NodeExecutionContext context = buildContext(Map.of(
                "workflowId", testFlowId.toString(),
                "waitForCompletion", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", "completed");
        assertThat(result.getOutput()).containsEntry("subFlowName", "Sub Flow");
    }

    @Test
    void execute_waitForCompletion_failedFlow_returnsFailure() {
        Flow subFlow = new Flow();
        subFlow.setId(testFlowId);
        subFlow.setName("Sub Flow");
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.of(subFlow));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);

        ExecutionResponse execResponse = ExecutionResponse.builder().id(testExecutionId).build();
        when(executionService.startExecution(eq(testFlowId), eq(testUserId), any())).thenReturn(execResponse);

        Execution execution = new Execution();
        execution.setId(testExecutionId);
        execution.setStatus("failed");
        when(executionRepository.findById(testExecutionId)).thenReturn(Optional.of(execution));

        NodeExecutionContext context = buildContext(Map.of(
                "workflowId", testFlowId.toString(),
                "waitForCompletion", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Sub-workflow failed");
    }

    @Test
    void execute_waitForCompletion_cancelledFlow_returnsFailure() {
        Flow subFlow = new Flow();
        subFlow.setId(testFlowId);
        subFlow.setName("Sub Flow");
        when(flowRepository.findByIdAndIsDeletedFalse(testFlowId)).thenReturn(Optional.of(subFlow));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);

        ExecutionResponse execResponse = ExecutionResponse.builder().id(testExecutionId).build();
        when(executionService.startExecution(eq(testFlowId), eq(testUserId), any())).thenReturn(execResponse);

        Execution execution = new Execution();
        execution.setId(testExecutionId);
        execution.setStatus("cancelled");
        when(executionRepository.findById(testExecutionId)).thenReturn(Optional.of(execution));

        NodeExecutionContext context = buildContext(Map.of(
                "workflowId", testFlowId.toString(),
                "waitForCompletion", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Sub-workflow cancelled");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsWorkflowIdField() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("workflowId");
        assertThat(properties).containsKey("waitForCompletion");
        assertThat(properties).containsKey("timeoutSeconds");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("sub-workflow-1")
                .nodeType("subWorkflow")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(testUserId)
                .flowId(UUID.randomUUID())
                .build();
    }
}
