package com.aiinpocket.n3n.execution.controller;

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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping
    public ResponseEntity<Page<ExecutionResponse>> listExecutions(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(executionService.listExecutions(pageable));
    }

    @GetMapping("/by-flow/{flowId}")
    public ResponseEntity<Page<ExecutionResponse>> listExecutionsByFlow(
            @PathVariable UUID flowId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(executionService.listExecutionsByFlow(flowId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionResponse> getExecution(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getExecution(id));
    }

    @GetMapping("/{id}/nodes")
    public ResponseEntity<List<NodeExecutionResponse>> getNodeExecutions(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getNodeExecutions(id));
    }

    @GetMapping("/{id}/output")
    public ResponseEntity<Map<String, Object>> getExecutionOutput(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getExecutionOutput(id));
    }

    @PostMapping
    public ResponseEntity<ExecutionResponse> createExecution(
            @Valid @RequestBody CreateExecutionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(executionService.createExecution(request, userId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ExecutionResponse> cancelExecution(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(executionService.cancelExecution(id, userId, reason));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ExecutionResponse> retryExecution(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(executionService.retryExecution(id, userId));
    }
}
