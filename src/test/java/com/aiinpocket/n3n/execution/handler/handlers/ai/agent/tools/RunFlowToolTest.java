package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.execution.service.StateManager;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunFlowToolTest extends BaseServiceTest {

    @Mock
    private ObjectProvider<ExecutionService> executionServiceProvider;

    @Mock
    private ExecutionService executionService;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private StateManager stateManager;

    private RunFlowTool tool;

    private final UUID userId = UUID.randomUUID();
    private final UUID flowId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new RunFlowTool(executionServiceProvider, flowRepository,
                executionRepository, stateManager, new ObjectMapper());
    }

    private ToolExecutionContext contextWithDepth(Integer depth) {
        Map<String, Object> flowVariables = depth != null
                ? Map.of("_subWorkflowDepth", depth)
                : Map.of();
        return new ToolExecutionContext(
                userId.toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), flowVariables);
    }

    private Flow ownedFlow() {
        return Flow.builder().id(flowId).name("My Flow").createdBy(userId).build();
    }

    private Execution executionWithStatus(String status) {
        return Execution.builder()
                .id(executionId)
                .flowVersionId(UUID.randomUUID())
                .status(status)
                .durationMs(1234)
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("run_flow");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getTimeoutSeconds()).isEqualTo(300);
        assertThat(tool.getDescription()).contains("published");
        assertThat(tool.getParametersSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Rejects another user's flow without leaking existence")
    void rejectsOtherUsersFlow() throws Exception {
        Flow otherUsersFlow = Flow.builder()
                .id(flowId).name("Secret Flow").createdBy(UUID.randomUUID()).build();
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(otherUsersFlow));

        ToolResult result = tool.execute(
                Map.of("flowId", flowId.toString()), contextWithDepth(null)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Flow not found");
        assertThat(result.error()).doesNotContain("Secret Flow");
        verify(executionServiceProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Happy path: starts execution, polls pending to completed, returns output")
    @SuppressWarnings("unchecked")
    void happyPath() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);
        when(executionService.startExecution(eq(flowId), eq(userId), any()))
                .thenReturn(ExecutionResponse.builder().id(executionId).build());
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(executionWithStatus("pending")))
                .thenReturn(Optional.of(executionWithStatus("completed")));
        when(stateManager.getExecutionOutput(executionId))
                .thenReturn(Map.of("result", "hello world"));

        ToolExecutionContext context = contextWithDepth(null);
        ToolResult result = tool.execute(Map.of(
                "flowId", flowId.toString(),
                "input", Map.of("name", "test"),
                "waitSeconds", 30
        ), context).get();

        assertThat(result.success()).isTrue();
        assertThat(result.data())
                .containsEntry("executionId", executionId.toString())
                .containsEntry("status", "completed")
                .containsEntry("durationMs", 1234);
        assertThat((String) result.data().get("output")).contains("hello world");

        // Trigger data carries agent metadata, input and depth guard
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(executionService).startExecution(eq(flowId), eq(userId), captor.capture());
        Map<String, Object> triggerData = captor.getValue();
        assertThat(triggerData)
                .containsEntry("triggeredBy", "aiAgent")
                .containsEntry("parentExecutionId", context.executionId())
                .containsEntry("_subWorkflowDepth", 1)
                .containsEntry("input", Map.of("name", "test"));
    }

    @Test
    @DisplayName("Refuses to run when recursion depth limit is reached")
    void refusesAtMaxDepth() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));

        ToolResult result = tool.execute(
                Map.of("flowId", flowId.toString()), contextWithDepth(5)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("recursion depth");
        verify(executionServiceProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Propagates incremented depth below the limit")
    @SuppressWarnings("unchecked")
    void incrementsDepth() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);
        when(executionService.startExecution(eq(flowId), eq(userId), any()))
                .thenReturn(ExecutionResponse.builder().id(executionId).build());
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(executionWithStatus("completed")));
        when(stateManager.getExecutionOutput(executionId)).thenReturn(Map.of());

        tool.execute(Map.of("flowId", flowId.toString()), contextWithDepth(2)).get();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(executionService).startExecution(eq(flowId), eq(userId), captor.capture());
        assertThat(captor.getValue()).containsEntry("_subWorkflowDepth", 3);
    }

    @Test
    @DisplayName("Returns failure with message when no published version exists")
    void noPublishedVersion() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);
        when(executionService.startExecution(eq(flowId), eq(userId), any()))
                .thenThrow(new IllegalArgumentException("No published version for flow: My Flow"));

        ToolResult result = tool.execute(
                Map.of("flowId", flowId.toString()), contextWithDepth(null)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No published version");
    }

    @Test
    @DisplayName("Returns failure when execution fails")
    void failedExecution() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);
        when(executionService.startExecution(eq(flowId), eq(userId), any()))
                .thenReturn(ExecutionResponse.builder().id(executionId).build());
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(executionWithStatus("failed")));

        ToolResult result = tool.execute(
                Map.of("flowId", flowId.toString()), contextWithDepth(null)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Flow failed").contains(executionId.toString());
    }

    @Test
    @DisplayName("Timeout: reports executionId when the flow is still running")
    void timeoutStillRunning() throws Exception {
        when(flowRepository.findByIdAndIsDeletedFalse(flowId))
                .thenReturn(Optional.of(ownedFlow()));
        when(executionServiceProvider.getIfAvailable()).thenReturn(executionService);
        when(executionService.startExecution(eq(flowId), eq(userId), any()))
                .thenReturn(ExecutionResponse.builder().id(executionId).build());
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(executionWithStatus("running")));

        // waitSeconds below minimum is clamped to 5
        ToolResult result = tool.execute(Map.of(
                "flowId", flowId.toString(),
                "waitSeconds", 1
        ), contextWithDepth(null)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error())
                .contains("still running after 5s")
                .contains(executionId.toString());
    }

    @Test
    @DisplayName("Fails on invalid flowId")
    void invalidFlowId() throws Exception {
        ToolResult result = tool.execute(
                Map.of("flowId", "not-a-uuid"), contextWithDepth(null)).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("flowId");
    }
}
