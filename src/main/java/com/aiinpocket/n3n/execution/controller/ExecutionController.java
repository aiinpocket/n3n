package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.common.dto.BatchDeleteRequest;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.dto.NodeExecutionResponse;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
@Tag(name = "Executions", description = "Flow execution management")
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping
    public ResponseEntity<Page<ExecutionResponse>> listExecutions(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if ((status != null && !status.isBlank()) || (search != null && !search.isBlank())) {
            return ResponseEntity.ok(executionService.listExecutions(userId, pageable, status, search));
        }
        return ResponseEntity.ok(executionService.listExecutions(userId, pageable));
    }

    @GetMapping("/by-flow/{flowId}")
    public ResponseEntity<Page<ExecutionResponse>> listExecutionsByFlow(
            @PathVariable UUID flowId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.listExecutionsByFlow(flowId, userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionResponse> getExecution(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.getExecution(id, userId));
    }

    @GetMapping("/{id}/nodes")
    public ResponseEntity<List<NodeExecutionResponse>> getNodeExecutions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.getNodeExecutions(id, userId));
    }

    @GetMapping("/{id}/nodes/{nodeId}/data")
    public ResponseEntity<Map<String, Object>> getNodeData(
            @PathVariable UUID id,
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = "^[a-zA-Z0-9_\\-]+$") String nodeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.getNodeData(id, nodeId, userId));
    }

    @GetMapping("/{id}/output")
    public ResponseEntity<Map<String, Object>> getExecutionOutput(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.getExecutionOutput(id, userId));
    }

    @PostMapping
    public ResponseEntity<ExecutionResponse> createExecution(
            @Valid @RequestBody CreateExecutionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(executionService.createExecution(request, userId));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ExecutionResponse> pauseExecution(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.pauseExecution(id, userId, reason));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ExecutionResponse> cancelExecution(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.cancelExecution(id, userId, reason));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ExecutionResponse> resumeExecution(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> resumeData,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.resumeExecution(id,
            resumeData != null ? resumeData : Map.of(), userId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ExecutionResponse> retryExecution(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(executionService.retryExecution(id, userId));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteExecutions(
            @Valid @RequestBody BatchDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<UUID> ids = request.getIds();
        int deleted = 0;
        for (UUID id : ids) {
            try {
                executionService.deleteExecution(id, userId);
                deleted++;
            } catch (Exception ignored) {
                // Skip executions that don't exist or aren't owned by user
            }
        }
        return ResponseEntity.ok(Map.of("deleted", deleted, "total", ids.size()));
    }
}
