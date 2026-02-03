package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.service.FlowService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;
    private final FlowShareService flowShareService;

    @GetMapping
    public ResponseEntity<Page<FlowResponse>> listFlows(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(flowService.searchFlows(search, pageable));
        }
        return ResponseEntity.ok(flowService.listFlows(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlowResponse> getFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(flowService.getFlow(id));
    }

    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @Valid @RequestBody CreateFlowRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(flowService.createFlow(request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlowResponse> updateFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlowRequest request) {
        return ResponseEntity.ok(flowService.updateFlow(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable UUID id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    // Version endpoints

    @GetMapping("/{flowId}/versions")
    public ResponseEntity<List<FlowVersionResponse>> listVersions(@PathVariable UUID flowId) {
        return ResponseEntity.ok(flowService.listVersions(flowId));
    }

    @GetMapping("/{flowId}/versions/{version}")
    public ResponseEntity<FlowVersionResponse> getVersion(
            @PathVariable UUID flowId,
            @PathVariable String version) {
        return ResponseEntity.ok(flowService.getVersion(flowId, version));
    }

    @GetMapping("/{flowId}/versions/published")
    public ResponseEntity<FlowVersionResponse> getPublishedVersion(@PathVariable UUID flowId) {
        return ResponseEntity.ok(flowService.getPublishedVersion(flowId));
    }

    @PostMapping("/{flowId}/versions")
    public ResponseEntity<FlowVersionResponse> saveVersion(
            @PathVariable UUID flowId,
            @Valid @RequestBody SaveVersionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowService.saveVersion(flowId, request, userId));
    }

    @PostMapping("/{flowId}/versions/{version}/publish")
    public ResponseEntity<FlowVersionResponse> publishVersion(
            @PathVariable UUID flowId,
            @PathVariable String version) {
        return ResponseEntity.ok(flowService.publishVersion(flowId, version));
    }

    // Validation endpoints

    @GetMapping("/{flowId}/versions/{version}/validate")
    public ResponseEntity<FlowValidationResponse> validateVersion(
            @PathVariable UUID flowId,
            @PathVariable String version) {
        return ResponseEntity.ok(flowService.validateFlow(flowId, version));
    }

    @PostMapping("/validate")
    public ResponseEntity<FlowValidationResponse> validateDefinition(
            @RequestBody java.util.Map<String, Object> definition) {
        return ResponseEntity.ok(flowService.validateDefinition(definition));
    }

    // ========== Sharing endpoints ==========

    @GetMapping("/{flowId}/shares")
    public ResponseEntity<List<FlowShareResponse>> getFlowShares(
            @PathVariable UUID flowId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowShareService.getFlowShares(flowId, userId));
    }

    @PostMapping("/{flowId}/shares")
    public ResponseEntity<FlowShareResponse> shareFlow(
            @PathVariable UUID flowId,
            @Valid @RequestBody FlowShareRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(flowShareService.shareFlow(flowId, request, userId));
    }

    @PutMapping("/{flowId}/shares/{shareId}")
    public ResponseEntity<FlowShareResponse> updateShare(
            @PathVariable UUID flowId,
            @PathVariable UUID shareId,
            @RequestParam String permission,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowShareService.updateSharePermission(flowId, shareId, permission, userId));
    }

    @DeleteMapping("/{flowId}/shares/{shareId}")
    public ResponseEntity<Void> removeShare(
            @PathVariable UUID flowId,
            @PathVariable UUID shareId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        flowShareService.removeShare(flowId, shareId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<FlowShareResponse>> getSharedWithMe(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowShareService.getSharedWithMe(userId));
    }

    // ========== Node data mapping endpoints ==========

    /**
     * Get upstream node outputs for input mapping in the flow editor.
     */
    @GetMapping("/{flowId}/versions/{version}/nodes/{nodeId}/upstream-outputs")
    public ResponseEntity<List<UpstreamNodeOutput>> getUpstreamOutputs(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @PathVariable String nodeId) {
        return ResponseEntity.ok(flowService.getUpstreamOutputs(flowId, version, nodeId));
    }
}
