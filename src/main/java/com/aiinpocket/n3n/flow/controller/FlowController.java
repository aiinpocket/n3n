package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.service.FlowService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import com.aiinpocket.n3n.common.dto.BatchDeleteRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flows", description = "Flow CRUD and management")
public class FlowController {

    private final FlowService flowService;
    private final FlowShareService flowShareService;
    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<Page<FlowResponse>> listFlows(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(flowService.searchFlows(userId, search, pageable));
        }
        return ResponseEntity.ok(flowService.listFlows(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlowResponse> getFlow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(id, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + id);
        }
        return ResponseEntity.ok(flowService.getFlow(id));
    }

    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @Valid @RequestBody CreateFlowRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowResponse response = flowService.createFlow(request, userId);
        activityService.logFlowCreate(userId, response.getId(), response.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlowResponse> updateFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlowRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasEditAccess(id, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + id);
        }
        FlowResponse response = flowService.updateFlow(id, request);
        activityService.logFlowUpdate(userId, response.getId(), response.getName(), null);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowResponse flow = flowService.getFlow(id);
        if (!flow.getCreatedBy().equals(userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + id);
        }
        flowService.deleteFlow(id);
        activityService.logFlowDelete(userId, id, flow.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/batch")
    public ResponseEntity<java.util.Map<String, Object>> batchDeleteFlows(
            @Valid @RequestBody BatchDeleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<UUID> ids = request.getIds();
        int deleted = 0;
        for (UUID id : ids) {
            try {
                FlowResponse flow = flowService.getFlow(id);
                if (flow.getCreatedBy().equals(userId)) {
                    flowService.deleteFlow(id);
                    activityService.logFlowDelete(userId, id, flow.getName());
                    deleted++;
                }
            } catch (com.aiinpocket.n3n.common.exception.ResourceNotFoundException e) {
                log.debug("Skipping batch delete for flow {}: {}", id, e.getMessage());
            }
        }
        return ResponseEntity.ok(java.util.Map.of("deleted", deleted, "total", ids.size()));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<FlowResponse> cloneFlow(
            @PathVariable UUID id,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(id, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + id);
        }
        FlowResponse response = flowService.cloneFlow(id, name, userId);
        activityService.logFlowCreate(userId, response.getId(), response.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Version endpoints

    @GetMapping("/{flowId}/versions")
    public ResponseEntity<List<FlowVersionResponse>> listVersions(
            @PathVariable UUID flowId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        return ResponseEntity.ok(flowService.listVersions(flowId));
    }

    @GetMapping("/{flowId}/versions/{version}")
    public ResponseEntity<FlowVersionResponse> getVersion(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        return ResponseEntity.ok(flowService.getVersion(flowId, version));
    }

    @GetMapping("/{flowId}/versions/published")
    public ResponseEntity<FlowVersionResponse> getPublishedVersion(
            @PathVariable UUID flowId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        return ResponseEntity.ok(flowService.getPublishedVersion(flowId));
    }

    @PostMapping("/{flowId}/versions")
    public ResponseEntity<FlowVersionResponse> saveVersion(
            @PathVariable UUID flowId,
            @Valid @RequestBody SaveVersionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasEditAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        FlowResponse flow = flowService.getFlow(flowId);
        FlowVersionResponse response = flowService.saveVersion(flowId, request, userId);
        activityService.logVersionCreate(userId, flowId, flow.getName(), response.getVersion());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{flowId}/versions/{version}/publish")
    public ResponseEntity<FlowVersionResponse> publishVersion(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasEditAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        FlowResponse flow = flowService.getFlow(flowId);
        // Get current published version for audit log
        String previousVersion = null;
        try {
            FlowVersionResponse currentPublished = flowService.getPublishedVersion(flowId);
            previousVersion = currentPublished.getVersion();
        } catch (Exception e) {
            // No previous published version
        }
        FlowVersionResponse response = flowService.publishVersion(flowId, version);
        activityService.logVersionPublish(userId, flowId, flow.getName(), version, previousVersion);
        return ResponseEntity.ok(response);
    }

    // Validation endpoints

    @GetMapping("/{flowId}/versions/{version}/validate")
    public ResponseEntity<FlowValidationResponse> validateVersion(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
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
        FlowResponse flow = flowService.getFlow(flowId);
        FlowShareResponse response = flowShareService.shareFlow(flowId, request, userId);
        String sharedWithEmail = response.getInvitedEmail() != null ? response.getInvitedEmail() : response.getUserEmail();
        activityService.logFlowShare(userId, flowId, flow.getName(), sharedWithEmail, response.getPermission());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{flowId}/shares/{shareId}")
    public ResponseEntity<FlowShareResponse> updateShare(
            @PathVariable UUID flowId,
            @PathVariable UUID shareId,
            @RequestParam String permission,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowResponse flow = flowService.getFlow(flowId);
        FlowShareResponse response = flowShareService.updateSharePermission(flowId, shareId, permission, userId);
        String sharedWithEmail = response.getInvitedEmail() != null ? response.getInvitedEmail() : response.getUserEmail();
        activityService.logFlowShareUpdate(userId, flowId, flow.getName(), sharedWithEmail, null, permission);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{flowId}/shares/{shareId}")
    public ResponseEntity<Void> removeShare(
            @PathVariable UUID flowId,
            @PathVariable UUID shareId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        FlowResponse flow = flowService.getFlow(flowId);
        // Get share info before removing for audit log
        List<FlowShareResponse> shares = flowShareService.getFlowShares(flowId, userId);
        String revokedEmail = shares.stream()
            .filter(s -> s.getId().equals(shareId))
            .findFirst()
            .map(s -> s.getInvitedEmail() != null ? s.getInvitedEmail() : s.getUserEmail())
            .orElse("unknown");
        flowShareService.removeShare(flowId, shareId, userId);
        activityService.logFlowShareRevoke(userId, flowId, flow.getName(), revokedEmail);
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
            @PathVariable String nodeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        return ResponseEntity.ok(flowService.getUpstreamOutputs(flowId, version, nodeId));
    }

    // ========== Data Pinning endpoints ==========

    /**
     * Get all pinned data for a flow version.
     */
    @GetMapping("/{flowId}/versions/{version}/pinned-data")
    public ResponseEntity<java.util.Map<String, Object>> getPinnedData(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        return ResponseEntity.ok(flowService.getPinnedData(flowId, version));
    }

    /**
     * Pin data to a specific node.
     */
    @PostMapping("/{flowId}/versions/{version}/pin")
    public ResponseEntity<Void> pinNodeData(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @Valid @RequestBody PinDataRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasEditAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        flowService.pinNodeData(flowId, version, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Unpin data from a specific node.
     */
    @DeleteMapping("/{flowId}/versions/{version}/pin/{nodeId}")
    public ResponseEntity<Void> unpinNodeData(
            @PathVariable UUID flowId,
            @PathVariable String version,
            @PathVariable String nodeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!flowShareService.hasEditAccess(flowId, userId)) {
            throw new com.aiinpocket.n3n.common.exception.ResourceNotFoundException("Flow not found: " + flowId);
        }
        flowService.unpinNodeData(flowId, version, nodeId);
        return ResponseEntity.noContent().build();
    }
}
