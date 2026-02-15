package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.common.dto.BatchDeleteRequest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.dto.NodeExecutionResponse;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionControllerTest {

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private ExecutionController executionController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private ExecutionResponse sampleExecution(String status) {
        return ExecutionResponse.builder()
                .id(UUID.randomUUID())
                .flowVersionId(UUID.randomUUID())
                .status(status)
                .flowId(UUID.randomUUID())
                .flowName("Test Flow")
                .flowVersion("1.0.0")
                .triggeredBy(UUID.randomUUID())
                .triggerType("manual")
                .startedAt(Instant.now())
                .durationMs(1500)
                .retryCount(0)
                .maxRetries(3)
                .canRetry(false)
                .build();
    }

    // ==================== listExecutions ====================

    @Test
    void listExecutions_noFilters_returnsOk() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("completed")));
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), null, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getStatus()).isEqualTo("completed");
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listExecutions_withValidStatus_callsFilteredMethod() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("running")));
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class), eq("running"), isNull()))
                .thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), "running", null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent().get(0).getStatus()).isEqualTo("running");
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class), eq("running"), isNull());
    }

    @Test
    void listExecutions_withSearch_callsFilteredMethod() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("completed")));
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class), isNull(), eq("myflow")))
                .thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), null, "myflow", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class), isNull(), eq("myflow"));
    }

    @Test
    void listExecutions_withStatusAndSearch_callsFilteredMethod() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("failed")));
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class), eq("failed"), eq("test")))
                .thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), "failed", "test", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class), eq("failed"), eq("test"));
    }

    @Test
    void listExecutions_withInvalidStatus_ignoresStatus() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("completed")));
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), "invalidStatus", null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Invalid status is set to null, and no search, so calls the simple overload
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listExecutions_withBlankStatus_callsSimpleOverload() {
        var user = testUser();
        var page = Page.<ExecutionResponse>empty();
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), "  ", null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Blank status is not in VALID_STATUSES -> set to null -> calls simple overload
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listExecutions_emptyResult_returnsEmptyPage() {
        var user = testUser();
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class))).thenReturn(Page.empty());

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), null, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    // ==================== listExecutionsByFlow ====================

    @Test
    void listExecutionsByFlow_returnsOk() {
        var user = testUser();
        UUID flowId = UUID.randomUUID();
        var page = new PageImpl<>(List.of(sampleExecution("completed")));
        when(executionService.listExecutionsByFlow(eq(flowId), any(UUID.class), any(Pageable.class)))
                .thenReturn(page);

        var result = executionController.listExecutionsByFlow(flowId, PageRequest.of(0, 20), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(executionService).listExecutionsByFlow(eq(flowId), any(UUID.class), any(Pageable.class));
    }

    @Test
    void listExecutionsByFlow_accessDenied_throwsException() {
        var user = testUser();
        UUID flowId = UUID.randomUUID();
        when(executionService.listExecutionsByFlow(eq(flowId), any(UUID.class), any(Pageable.class)))
                .thenThrow(new AccessDeniedException("Access denied to flow: " + flowId));

        assertThatThrownBy(() -> executionController.listExecutionsByFlow(flowId, PageRequest.of(0, 20), user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    // ==================== getExecution ====================

    @Test
    void getExecution_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("completed");
        when(executionService.getExecution(eq(executionId), any(UUID.class))).thenReturn(response);

        var result = executionController.getExecution(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("completed");
        assertThat(result.getBody().getFlowName()).isEqualTo("Test Flow");
    }

    @Test
    void getExecution_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getExecution(eq(executionId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.getExecution(executionId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Execution not found");
    }

    @Test
    void getExecution_accessDenied_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getExecution(eq(executionId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> executionController.getExecution(executionId, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== getNodeExecutions ====================

    @Test
    void getNodeExecutions_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var nodeResponse = NodeExecutionResponse.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId("node-1")
                .componentName("httpRequest")
                .componentVersion("1.0.0")
                .status("completed")
                .startedAt(Instant.now().minusSeconds(5))
                .completedAt(Instant.now())
                .durationMs(5000)
                .build();
        when(executionService.getNodeExecutions(eq(executionId), any(UUID.class)))
                .thenReturn(List.of(nodeResponse));

        var result = executionController.getNodeExecutions(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getNodeId()).isEqualTo("node-1");
        assertThat(result.getBody().get(0).getComponentName()).isEqualTo("httpRequest");
        assertThat(result.getBody().get(0).getDurationMs()).isEqualTo(5000);
    }

    @Test
    void getNodeExecutions_emptyList_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getNodeExecutions(eq(executionId), any(UUID.class)))
                .thenReturn(List.of());

        var result = executionController.getNodeExecutions(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getNodeExecutions_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getNodeExecutions(eq(executionId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.getNodeExecutions(executionId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== getNodeData ====================

    @Test
    void getNodeData_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        String nodeId = "node-1";
        Map<String, Object> nodeData = Map.of(
                "input", Map.of("key", "value"),
                "output", Map.of("result", "success"));
        when(executionService.getNodeData(eq(executionId), eq(nodeId), any(UUID.class)))
                .thenReturn(nodeData);

        var result = executionController.getNodeData(executionId, nodeId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsKey("input");
        assertThat(result.getBody()).containsKey("output");
    }

    @Test
    void getNodeData_emptyOutput_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        String nodeId = "node-2";
        Map<String, Object> nodeData = Map.of("input", Map.of(), "output", Map.of());
        when(executionService.getNodeData(eq(executionId), eq(nodeId), any(UUID.class)))
                .thenReturn(nodeData);

        var result = executionController.getNodeData(executionId, nodeId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void getNodeData_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        String nodeId = "nonexistent-node";
        when(executionService.getNodeData(eq(executionId), eq(nodeId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.getNodeData(executionId, nodeId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== getExecutionOutput ====================

    @Test
    void getExecutionOutput_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        Map<String, Object> output = Map.of(
                "node-1", Map.of("data", "result1"),
                "node-2", Map.of("data", "result2"));
        when(executionService.getExecutionOutput(eq(executionId), any(UUID.class)))
                .thenReturn(output);

        var result = executionController.getExecutionOutput(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody()).containsKey("node-1");
        assertThat(result.getBody()).containsKey("node-2");
    }

    @Test
    void getExecutionOutput_emptyOutput_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getExecutionOutput(eq(executionId), any(UUID.class)))
                .thenReturn(Map.of());

        var result = executionController.getExecutionOutput(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getExecutionOutput_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.getExecutionOutput(eq(executionId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.getExecutionOutput(executionId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== createExecution ====================

    @Test
    void createExecution_returnsCreated() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());
        request.setInput(Map.of("param1", "value1"));

        var response = sampleExecution("pending");
        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = executionController.createExecution(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("pending");
        verify(executionService).createExecution(any(CreateExecutionRequest.class), any(UUID.class));
    }

    @Test
    void createExecution_withVersion_returnsCreated() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());
        request.setVersion("2.0.0");
        request.setInput(Map.of("key", "value"));
        request.setContext(Map.of("env", "test"));

        var response = sampleExecution("pending");
        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = executionController.createExecution(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void createExecution_flowNotFound_throwsException() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());

        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Flow not found"));

        assertThatThrownBy(() -> executionController.createExecution(request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flow not found");
    }

    @Test
    void createExecution_noPublishedVersion_throwsException() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());

        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("No published version available"));

        assertThatThrownBy(() -> executionController.createExecution(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No published version");
    }

    @Test
    void createExecution_dedup_throwsException() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());

        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenThrow(new IllegalStateException("An execution for this flow is already being created. Please wait a moment."));

        assertThatThrownBy(() -> executionController.createExecution(request, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being created");
    }

    @Test
    void createExecution_accessDenied_throwsException() {
        var user = testUser();
        var request = new CreateExecutionRequest();
        request.setFlowId(UUID.randomUUID());

        when(executionService.createExecution(any(CreateExecutionRequest.class), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied to flow"));

        assertThatThrownBy(() -> executionController.createExecution(request, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== pauseExecution ====================

    @Test
    void pauseExecution_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("waiting");
        response.setPausedAt(Instant.now());
        response.setPauseReason("Manual pause");
        when(executionService.pauseExecution(eq(executionId), any(UUID.class), eq("Manual pause")))
                .thenReturn(response);

        var result = executionController.pauseExecution(executionId, "Manual pause", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("waiting");
        assertThat(result.getBody().getPauseReason()).isEqualTo("Manual pause");
    }

    @Test
    void pauseExecution_withoutReason_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("waiting");
        response.setPauseReason("Manually paused by user");
        when(executionService.pauseExecution(eq(executionId), any(UUID.class), isNull()))
                .thenReturn(response);

        var result = executionController.pauseExecution(executionId, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("waiting");
    }

    @Test
    void pauseExecution_notRunning_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.pauseExecution(eq(executionId), any(UUID.class), any()))
                .thenThrow(new IllegalArgumentException("Cannot pause execution in status: completed"));

        assertThatThrownBy(() -> executionController.pauseExecution(executionId, null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot pause");
    }

    @Test
    void pauseExecution_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.pauseExecution(eq(executionId), any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.pauseExecution(executionId, null, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== cancelExecution ====================

    @Test
    void cancelExecution_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("cancelled");
        response.setCancelReason("No longer needed");
        response.setCancelledAt(Instant.now());
        when(executionService.cancelExecution(eq(executionId), any(UUID.class), eq("No longer needed")))
                .thenReturn(response);

        var result = executionController.cancelExecution(executionId, "No longer needed", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("cancelled");
        assertThat(result.getBody().getCancelReason()).isEqualTo("No longer needed");
    }

    @Test
    void cancelExecution_withoutReason_returnsOk() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("cancelled");
        when(executionService.cancelExecution(eq(executionId), any(UUID.class), isNull()))
                .thenReturn(response);

        var result = executionController.cancelExecution(executionId, null, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("cancelled");
    }

    @Test
    void cancelExecution_invalidStatus_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.cancelExecution(eq(executionId), any(UUID.class), any()))
                .thenThrow(new IllegalArgumentException("Cannot cancel execution in status: completed"));

        assertThatThrownBy(() -> executionController.cancelExecution(executionId, null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel");
    }

    @Test
    void cancelExecution_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.cancelExecution(eq(executionId), any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.cancelExecution(executionId, null, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== retryExecution ====================

    @Test
    void retryExecution_returnsCreated() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        var response = sampleExecution("pending");
        response.setRetryOf(executionId);
        response.setRetryCount(1);
        response.setTriggerType("retry");
        when(executionService.retryExecution(eq(executionId), any(UUID.class)))
                .thenReturn(response);

        var result = executionController.retryExecution(executionId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getRetryOf()).isEqualTo(executionId);
        assertThat(result.getBody().getRetryCount()).isEqualTo(1);
        assertThat(result.getBody().getTriggerType()).isEqualTo("retry");
    }

    @Test
    void retryExecution_notFailedOrCancelled_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.retryExecution(eq(executionId), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Can only retry failed or cancelled executions. Current status: running"));

        assertThatThrownBy(() -> executionController.retryExecution(executionId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Can only retry");
    }

    @Test
    void retryExecution_maxRetriesReached_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.retryExecution(eq(executionId), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Maximum retry count reached: 3"));

        assertThatThrownBy(() -> executionController.retryExecution(executionId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum retry count");
    }

    @Test
    void retryExecution_notFound_throwsException() {
        var user = testUser();
        UUID executionId = UUID.randomUUID();
        when(executionService.retryExecution(eq(executionId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Execution not found: " + executionId));

        assertThatThrownBy(() -> executionController.retryExecution(executionId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== batchDeleteExecutions ====================

    @Test
    void batchDelete_allSuccessful_returnsCorrectCount() {
        var user = testUser();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2, id3));

        // All deletes succeed (no exceptions thrown)
        doNothing().when(executionService).deleteExecution(any(UUID.class), any(UUID.class));

        var result = executionController.batchDeleteExecutions(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("deleted")).isEqualTo(3);
        assertThat(result.getBody().get("total")).isEqualTo(3);
        verify(executionService, times(3)).deleteExecution(any(UUID.class), any(UUID.class));
    }

    @Test
    void batchDelete_someNotFound_skipsAndContinues() {
        var user = testUser();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2, id3));

        UUID userId = UUID.fromString(user.getUsername());
        // First succeeds, second throws not found, third succeeds
        doNothing().when(executionService).deleteExecution(eq(id1), eq(userId));
        doThrow(new ResourceNotFoundException("Execution not found: " + id2))
                .when(executionService).deleteExecution(eq(id2), eq(userId));
        doNothing().when(executionService).deleteExecution(eq(id3), eq(userId));

        var result = executionController.batchDeleteExecutions(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("deleted")).isEqualTo(2);
        assertThat(result.getBody().get("total")).isEqualTo(3);
    }

    @Test
    void batchDelete_someAccessDenied_skipsAndContinues() {
        var user = testUser();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2));

        UUID userId = UUID.fromString(user.getUsername());
        // First throws access denied, second succeeds
        doThrow(new AccessDeniedException("Access denied"))
                .when(executionService).deleteExecution(eq(id1), eq(userId));
        doNothing().when(executionService).deleteExecution(eq(id2), eq(userId));

        var result = executionController.batchDeleteExecutions(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("deleted")).isEqualTo(1);
        assertThat(result.getBody().get("total")).isEqualTo(2);
    }

    @Test
    void batchDelete_allNotFound_returnsZeroDeleted() {
        var user = testUser();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2));

        doThrow(new ResourceNotFoundException("Not found"))
                .when(executionService).deleteExecution(any(UUID.class), any(UUID.class));

        var result = executionController.batchDeleteExecutions(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("deleted")).isEqualTo(0);
        assertThat(result.getBody().get("total")).isEqualTo(2);
    }

    @Test
    void batchDelete_singleItem_returnsCorrectCount() {
        var user = testUser();
        UUID id1 = UUID.randomUUID();
        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1));

        doNothing().when(executionService).deleteExecution(any(UUID.class), any(UUID.class));

        var result = executionController.batchDeleteExecutions(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("deleted")).isEqualTo(1);
        assertThat(result.getBody().get("total")).isEqualTo(1);
    }

    // ==================== Status validation edge cases ====================

    @Test
    void listExecutions_allValidStatuses_areAccepted() {
        var user = testUser();
        var page = Page.<ExecutionResponse>empty();

        // Test each valid status
        for (String status : List.of("pending", "running", "completed", "failed", "cancelled", "waiting", "paused")) {
            when(executionService.listExecutions(any(UUID.class), any(Pageable.class), eq(status), isNull()))
                    .thenReturn(page);

            var result = executionController.listExecutions(
                    PageRequest.of(0, 20), status, null, user);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void listExecutions_invalidStatusWithSearch_usesSearchOnly() {
        var user = testUser();
        var page = new PageImpl<>(List.of(sampleExecution("completed")));
        // Invalid status is set to null, but search is present, so calls filtered method with null status
        when(executionService.listExecutions(any(UUID.class), any(Pageable.class), isNull(), eq("myflow")))
                .thenReturn(page);

        var result = executionController.listExecutions(
                PageRequest.of(0, 20), "bogusStatus", "myflow", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).listExecutions(any(UUID.class), any(Pageable.class), isNull(), eq("myflow"));
    }

    // ==================== UserDetails UUID parsing ====================

    @Test
    void allEndpoints_extractUserId_fromUserDetails() {
        // Verify that the controller correctly parses UUID from UserDetails.getUsername()
        UUID expectedUserId = UUID.randomUUID();
        UserDetails user = User.withUsername(expectedUserId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();

        var response = sampleExecution("completed");
        when(executionService.getExecution(any(UUID.class), eq(expectedUserId))).thenReturn(response);

        var result = executionController.getExecution(UUID.randomUUID(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).getExecution(any(UUID.class), eq(expectedUserId));
    }
}
