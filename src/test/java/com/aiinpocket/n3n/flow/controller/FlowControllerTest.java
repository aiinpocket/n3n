package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.dto.BatchDeleteRequest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.service.FlowService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowControllerTest {

    @Mock
    private FlowService flowService;

    @Mock
    private FlowShareService flowShareService;

    @Mock
    private ActivityService activityService;

    @Mock
    private com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    @InjectMocks
    private FlowController flowController;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private FlowResponse sampleFlowResponse() {
        return FlowResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Flow")
                .description("A test flow")
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .latestVersion("1.0.0")
                .publishedVersion("1.0.0")
                .build();
    }

    private FlowVersionResponse sampleVersionResponse(UUID flowId) {
        return FlowVersionResponse.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .version("1.0.0")
                .definition(Map.of("nodes", List.of(), "edges", List.of()))
                .settings(Map.of())
                .pinnedData(Map.of())
                .status("draft")
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
    }

    private FlowShareResponse sampleShareResponse(UUID flowId) {
        return FlowShareResponse.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .userId(UUID.randomUUID())
                .userEmail("shared@example.com")
                .permission("view")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(false)
                .build();
    }

    // ========== listFlows ==========

    @Test
    void listFlows_withoutSearch_returnsPage() {
        var pageable = PageRequest.of(0, 20);
        var flow = sampleFlowResponse();
        Page<FlowResponse> page = new PageImpl<>(List.of(flow), pageable, 1);
        when(flowService.listFlows(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = flowController.listFlows(null, pageable, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("Test Flow");
        verify(flowService).listFlows(eq(userId), any(Pageable.class));
        verify(flowService, never()).searchFlows(any(), any(), any());
    }

    @Test
    void listFlows_withEmptySearch_returnsListWithoutSearch() {
        var pageable = PageRequest.of(0, 20);
        when(flowService.listFlows(eq(userId), any(Pageable.class))).thenReturn(Page.empty(pageable));

        var result = flowController.listFlows("", pageable, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(flowService).listFlows(eq(userId), any(Pageable.class));
        verify(flowService, never()).searchFlows(any(), any(), any());
    }

    @Test
    void listFlows_withSearch_callsSearchFlows() {
        var pageable = PageRequest.of(0, 20);
        var flow = sampleFlowResponse();
        Page<FlowResponse> page = new PageImpl<>(List.of(flow), pageable, 1);
        when(flowService.searchFlows(eq(userId), eq("test"), any(Pageable.class))).thenReturn(page);

        var result = flowController.listFlows("test", pageable, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(flowService).searchFlows(eq(userId), eq("test"), any(Pageable.class));
        verify(flowService, never()).listFlows(any(), any());
    }

    // ========== getFlow ==========

    @Test
    void getFlow_withAccess_returnsFlowWithPermission() {
        var flowId = UUID.randomUUID();
        var flow = sampleFlowResponse();
        flow.setId(flowId);
        when(flowShareService.getUserPermission(flowId, userId)).thenReturn("owner");
        when(flowService.getFlow(flowId)).thenReturn(flow);

        var result = flowController.getFlow(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(flowId);
        assertThat(result.getBody().getUserPermission()).isEqualTo("owner");
    }

    @Test
    void getFlow_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.getUserPermission(flowId, userId)).thenReturn(null);

        assertThatThrownBy(() -> flowController.getFlow(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    // ========== createFlow ==========

    @Test
    void createFlow_returnsCreated() {
        var request = new CreateFlowRequest();
        request.setName("New Flow");
        request.setDescription("Description");

        var response = sampleFlowResponse();
        response.setName("New Flow");
        when(flowService.createFlow(any(CreateFlowRequest.class), eq(userId))).thenReturn(response);

        var result = flowController.createFlow(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("New Flow");
        verify(activityService).logFlowCreate(eq(userId), eq(response.getId()), eq("New Flow"));
    }

    // ========== updateFlow ==========

    @Test
    void updateFlow_withEditAccess_returnsUpdatedFlow() {
        var flowId = UUID.randomUUID();
        var request = new UpdateFlowRequest();
        request.setName("Updated Flow");

        var response = sampleFlowResponse();
        response.setId(flowId);
        response.setName("Updated Flow");
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(flowService.updateFlow(eq(flowId), any(UpdateFlowRequest.class))).thenReturn(response);

        var result = flowController.updateFlow(flowId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getName()).isEqualTo("Updated Flow");
        verify(activityService).logFlowUpdate(eq(userId), eq(flowId), eq("Updated Flow"), isNull());
    }

    @Test
    void updateFlow_viewOnlyAccess_throwsAccessDenied() {
        var flowId = UUID.randomUUID();
        var request = new UpdateFlowRequest();
        request.setName("Updated Flow");

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);

        assertThatThrownBy(() -> flowController.updateFlow(flowId, request, testUser()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Edit permission required");
    }

    @Test
    void updateFlow_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        var request = new UpdateFlowRequest();

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.updateFlow(flowId, request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    // ========== deleteFlow ==========

    @Test
    void deleteFlow_asOwner_returnsNoContent() {
        var flowId = UUID.randomUUID();
        var flow = sampleFlowResponse();
        flow.setId(flowId);
        flow.setCreatedBy(userId);
        when(flowService.getFlowForOwner(flowId, userId)).thenReturn(flow);

        var result = flowController.deleteFlow(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(flowService).deleteFlow(flowId);
        verify(activityService).logFlowDelete(eq(userId), eq(flowId), eq(flow.getName()));
    }

    @Test
    void deleteFlow_notOwner_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowService.getFlowForOwner(flowId, userId))
            .thenThrow(new ResourceNotFoundException("Flow not found: " + flowId));

        assertThatThrownBy(() -> flowController.deleteFlow(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());

        verify(flowService, never()).deleteFlow(any());
    }

    // ========== batchDeleteFlows ==========

    @Test
    void batchDeleteFlows_deletesOwnedFlows() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        var id3 = UUID.randomUUID();

        var flow1 = sampleFlowResponse();
        flow1.setId(id1);
        flow1.setCreatedBy(userId);

        var flow2 = sampleFlowResponse();
        flow2.setId(id2);
        flow2.setCreatedBy(UUID.randomUUID()); // not owned

        var flow3 = sampleFlowResponse();
        flow3.setId(id3);
        flow3.setCreatedBy(userId);

        when(flowService.getFlow(id1)).thenReturn(flow1);
        when(flowService.getFlow(id2)).thenReturn(flow2);
        when(flowService.getFlow(id3)).thenReturn(flow3);

        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2, id3));

        var result = flowController.batchDeleteFlows(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("deleted")).isEqualTo(2);
        assertThat(result.getBody().get("total")).isEqualTo(3);
        verify(flowService).deleteFlow(id1);
        verify(flowService, never()).deleteFlow(id2);
        verify(flowService).deleteFlow(id3);
    }

    @Test
    void batchDeleteFlows_skipsNotFound() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();

        var flow1 = sampleFlowResponse();
        flow1.setId(id1);
        flow1.setCreatedBy(userId);

        when(flowService.getFlow(id1)).thenReturn(flow1);
        when(flowService.getFlow(id2)).thenThrow(new ResourceNotFoundException("Flow not found: " + id2));

        var request = new BatchDeleteRequest();
        request.setIds(List.of(id1, id2));

        var result = flowController.batchDeleteFlows(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("deleted")).isEqualTo(1);
        assertThat(result.getBody().get("total")).isEqualTo(2);
    }

    // ========== cloneFlow ==========

    @Test
    void cloneFlow_withAccess_returnsCreated() {
        var flowId = UUID.randomUUID();
        var cloned = sampleFlowResponse();
        cloned.setName("Test Flow (Copy)");
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.cloneFlow(eq(flowId), eq("My Clone"), eq(userId))).thenReturn(cloned);

        var result = flowController.cloneFlow(flowId, "My Clone", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        verify(activityService).logFlowCreate(eq(userId), eq(cloned.getId()), eq(cloned.getName()));
    }

    @Test
    void cloneFlow_withoutName_passesNull() {
        var flowId = UUID.randomUUID();
        var cloned = sampleFlowResponse();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.cloneFlow(eq(flowId), isNull(), eq(userId))).thenReturn(cloned);

        var result = flowController.cloneFlow(flowId, null, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(flowService).cloneFlow(eq(flowId), isNull(), eq(userId));
    }

    @Test
    void cloneFlow_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.cloneFlow(flowId, null, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    // ========== listVersions ==========

    @Test
    void listVersions_withAccess_returnsList() {
        var flowId = UUID.randomUUID();
        var version = sampleVersionResponse(flowId);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.listVersions(flowId)).thenReturn(List.of(version));

        var result = flowController.listVersions(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void listVersions_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.listVersions(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    // ========== getVersion ==========

    @Test
    void getVersion_withAccess_returnsVersion() {
        var flowId = UUID.randomUUID();
        var version = sampleVersionResponse(flowId);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.getVersion(flowId, "1.0.0")).thenReturn(version);

        var result = flowController.getVersion(flowId, "1.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getBody().getFlowId()).isEqualTo(flowId);
    }

    @Test
    void getVersion_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.getVersion(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== getPublishedVersion ==========

    @Test
    void getPublishedVersion_withAccess_returnsPublishedVersion() {
        var flowId = UUID.randomUUID();
        var version = sampleVersionResponse(flowId);
        version.setStatus("published");
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.getPublishedVersion(flowId)).thenReturn(version);

        var result = flowController.getPublishedVersion(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("published");
    }

    @Test
    void getPublishedVersion_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.getPublishedVersion(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== saveVersion ==========

    @Test
    void saveVersion_withEditAccess_returnsVersion() {
        var flowId = UUID.randomUUID();
        var request = new SaveVersionRequest();
        request.setVersion("2.0.0");
        request.setDefinition(Map.of("nodes", List.of(), "edges", List.of()));
        request.setSettings(Map.of());

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var versionResponse = sampleVersionResponse(flowId);
        versionResponse.setVersion("2.0.0");

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowService.saveVersion(eq(flowId), any(SaveVersionRequest.class), eq(userId))).thenReturn(versionResponse);

        var result = flowController.saveVersion(flowId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("2.0.0");
        verify(activityService).logVersionCreate(eq(userId), eq(flowId), eq(flow.getName()), eq("2.0.0"));
    }

    @Test
    void saveVersion_viewOnlyAccess_throwsAccessDenied() {
        var flowId = UUID.randomUUID();
        var request = new SaveVersionRequest();
        request.setVersion("2.0.0");
        request.setDefinition(Map.of());

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);

        assertThatThrownBy(() -> flowController.saveVersion(flowId, request, testUser()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Edit permission required");
    }

    @Test
    void saveVersion_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        var request = new SaveVersionRequest();
        request.setVersion("2.0.0");
        request.setDefinition(Map.of());

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.saveVersion(flowId, request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== publishVersion ==========

    @Test
    void publishVersion_withEditAccess_returnsPublishedVersion() {
        var flowId = UUID.randomUUID();
        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var currentPublished = sampleVersionResponse(flowId);
        currentPublished.setVersion("1.0.0");
        currentPublished.setStatus("published");

        var newPublished = sampleVersionResponse(flowId);
        newPublished.setVersion("2.0.0");
        newPublished.setStatus("published");

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowService.getPublishedVersion(flowId)).thenReturn(currentPublished);
        when(flowService.publishVersion(flowId, "2.0.0")).thenReturn(newPublished);

        var result = flowController.publishVersion(flowId, "2.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("2.0.0");
        verify(activityService).logVersionPublish(eq(userId), eq(flowId), eq(flow.getName()), eq("2.0.0"), eq("1.0.0"));
    }

    @Test
    void publishVersion_noPreviousPublished_logsNullPreviousVersion() {
        var flowId = UUID.randomUUID();
        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var newPublished = sampleVersionResponse(flowId);
        newPublished.setVersion("1.0.0");
        newPublished.setStatus("published");

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);
        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowService.getPublishedVersion(flowId)).thenThrow(new ResourceNotFoundException("No published version"));
        when(flowService.publishVersion(flowId, "1.0.0")).thenReturn(newPublished);

        var result = flowController.publishVersion(flowId, "1.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).logVersionPublish(eq(userId), eq(flowId), eq(flow.getName()), eq("1.0.0"), isNull());
    }

    @Test
    void publishVersion_viewOnlyAccess_throwsAccessDenied() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);

        assertThatThrownBy(() -> flowController.publishVersion(flowId, "1.0.0", testUser()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Edit permission required");
    }

    @Test
    void publishVersion_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.publishVersion(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== validateVersion ==========

    @Test
    void validateVersion_withAccess_returnsValidation() {
        var flowId = UUID.randomUUID();
        var validation = FlowValidationResponse.builder()
                .valid(true)
                .errors(List.of())
                .warnings(List.of())
                .entryPoints(List.of("node-1"))
                .exitPoints(List.of("node-3"))
                .executionOrder(List.of("node-1", "node-2", "node-3"))
                .build();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.validateFlow(flowId, "1.0.0")).thenReturn(validation);

        var result = flowController.validateVersion(flowId, "1.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isValid()).isTrue();
        assertThat(result.getBody().getEntryPoints()).containsExactly("node-1");
    }

    @Test
    void validateVersion_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.validateVersion(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== validateDefinition ==========

    @Test
    void validateDefinition_returnsValidation() {
        var definition = Map.<String, Object>of("nodes", List.of(), "edges", List.of());
        var validation = FlowValidationResponse.builder()
                .valid(true)
                .errors(List.of())
                .warnings(List.of("Empty flow"))
                .build();
        when(flowService.validateDefinition(definition)).thenReturn(validation);

        var result = flowController.validateDefinition(definition);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isValid()).isTrue();
        assertThat(result.getBody().getWarnings()).contains("Empty flow");
    }

    @Test
    void validateDefinition_nullDefinition_returnsValidation() {
        var validation = FlowValidationResponse.builder()
                .valid(false)
                .errors(List.of("Definition is null"))
                .build();
        when(flowService.validateDefinition(null)).thenReturn(validation);

        var result = flowController.validateDefinition(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isValid()).isFalse();
    }

    // ========== getFlowShares ==========

    @Test
    void getFlowShares_returnsList() {
        var flowId = UUID.randomUUID();
        var share = sampleShareResponse(flowId);
        when(flowShareService.getFlowShares(flowId, userId)).thenReturn(List.of(share));

        var result = flowController.getFlowShares(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getFlowId()).isEqualTo(flowId);
    }

    // ========== shareFlow ==========

    @Test
    void shareFlow_returnsCreated() {
        var flowId = UUID.randomUUID();
        var request = new FlowShareRequest();
        request.setEmail("collaborator@example.com");
        request.setPermission("edit");

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var shareResponse = FlowShareResponse.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .invitedEmail("collaborator@example.com")
                .permission("edit")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(true)
                .build();

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.shareFlow(eq(flowId), any(FlowShareRequest.class), eq(userId))).thenReturn(shareResponse);

        var result = flowController.shareFlow(flowId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getInvitedEmail()).isEqualTo("collaborator@example.com");
        assertThat(result.getBody().getPermission()).isEqualTo("edit");
        verify(activityService).logFlowShare(eq(userId), eq(flowId), eq(flow.getName()), eq("collaborator@example.com"), eq("edit"));
    }

    @Test
    void shareFlow_withUserId_usesUserEmail() {
        var flowId = UUID.randomUUID();
        var targetUserId = UUID.randomUUID();

        var request = new FlowShareRequest();
        request.setUserId(targetUserId);
        request.setPermission("view");

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var shareResponse = FlowShareResponse.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .userId(targetUserId)
                .userEmail("user@example.com")
                .permission("view")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(false)
                .build();

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.shareFlow(eq(flowId), any(FlowShareRequest.class), eq(userId))).thenReturn(shareResponse);

        var result = flowController.shareFlow(flowId, request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // When invitedEmail is null, falls back to userEmail
        verify(activityService).logFlowShare(eq(userId), eq(flowId), eq(flow.getName()), eq("user@example.com"), eq("view"));
    }

    // ========== updateShare ==========

    @Test
    void updateShare_returnsUpdatedShare() {
        var flowId = UUID.randomUUID();
        var shareId = UUID.randomUUID();

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var updatedShare = FlowShareResponse.builder()
                .id(shareId)
                .flowId(flowId)
                .userEmail("collaborator@example.com")
                .permission("admin")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(false)
                .build();

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.updateSharePermission(flowId, shareId, "admin", userId)).thenReturn(updatedShare);

        var result = flowController.updateShare(flowId, shareId, "admin", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getPermission()).isEqualTo("admin");
        verify(activityService).logFlowShareUpdate(eq(userId), eq(flowId), eq(flow.getName()), eq("collaborator@example.com"), isNull(), eq("admin"));
    }

    // ========== removeShare ==========

    @Test
    void removeShare_returnsNoContent() {
        var flowId = UUID.randomUUID();
        var shareId = UUID.randomUUID();

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var share = FlowShareResponse.builder()
                .id(shareId)
                .flowId(flowId)
                .userEmail("collaborator@example.com")
                .permission("edit")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(false)
                .build();

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.getFlowShares(flowId, userId)).thenReturn(List.of(share));

        var result = flowController.removeShare(flowId, shareId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(flowShareService).removeShare(flowId, shareId, userId);
        verify(activityService).logFlowShareRevoke(eq(userId), eq(flowId), eq(flow.getName()), eq("collaborator@example.com"));
    }

    @Test
    void removeShare_shareNotInList_logsUnknown() {
        var flowId = UUID.randomUUID();
        var shareId = UUID.randomUUID();

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.getFlowShares(flowId, userId)).thenReturn(List.of());

        var result = flowController.removeShare(flowId, shareId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(activityService).logFlowShareRevoke(eq(userId), eq(flowId), eq(flow.getName()), eq("unknown"));
    }

    @Test
    void removeShare_withInvitedEmail_logsInvitedEmail() {
        var flowId = UUID.randomUUID();
        var shareId = UUID.randomUUID();

        var flow = sampleFlowResponse();
        flow.setId(flowId);

        var share = FlowShareResponse.builder()
                .id(shareId)
                .flowId(flowId)
                .invitedEmail("invited@example.com")
                .userEmail(null)
                .permission("view")
                .sharedBy(userId)
                .sharedAt(Instant.now())
                .pending(true)
                .build();

        when(flowService.getFlow(flowId)).thenReturn(flow);
        when(flowShareService.getFlowShares(flowId, userId)).thenReturn(List.of(share));

        var result = flowController.removeShare(flowId, shareId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(activityService).logFlowShareRevoke(eq(userId), eq(flowId), eq(flow.getName()), eq("invited@example.com"));
    }

    // ========== getSharedWithMe ==========

    @Test
    void getSharedWithMe_returnsList() {
        var share = sampleShareResponse(UUID.randomUUID());
        when(flowShareService.getSharedWithMe(userId)).thenReturn(List.of(share));

        var result = flowController.getSharedWithMe(testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getSharedWithMe_empty_returnsEmptyList() {
        when(flowShareService.getSharedWithMe(userId)).thenReturn(List.of());

        var result = flowController.getSharedWithMe(testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    // ========== getUpstreamOutputs ==========

    @Test
    void getUpstreamOutputs_withAccess_returnsOutputs() {
        var flowId = UUID.randomUUID();
        var output = UpstreamNodeOutput.builder()
                .nodeId("node-1")
                .nodeLabel("HTTP Request")
                .nodeType("httpRequest")
                .outputSchema(Map.of("type", "object"))
                .flattenedFields(List.of(
                        UpstreamNodeOutput.OutputField.builder()
                                .path("data.name")
                                .type("string")
                                .description("Name field")
                                .expression("{{ $node[\"node-1\"].json.data.name }}")
                                .build()
                ))
                .build();

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.getUpstreamOutputs(flowId, "1.0.0", "node-2")).thenReturn(List.of(output));

        var result = flowController.getUpstreamOutputs(flowId, "1.0.0", "node-2", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getNodeId()).isEqualTo("node-1");
        assertThat(result.getBody().get(0).getFlattenedFields()).hasSize(1);
    }

    @Test
    void getUpstreamOutputs_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.getUpstreamOutputs(flowId, "1.0.0", "node-2", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== getPinnedData ==========

    @Test
    void getPinnedData_withAccess_returnsPinnedData() {
        var flowId = UUID.randomUUID();
        var pinnedData = Map.<String, Object>of("node-1", Map.of("key", "value"));
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(flowService.getPinnedData(flowId, "1.0.0")).thenReturn(pinnedData);

        var result = flowController.getPinnedData(flowId, "1.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsKey("node-1");
    }

    @Test
    void getPinnedData_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.getPinnedData(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== pinNodeData ==========

    @Test
    void pinNodeData_withEditAccess_returnsOk() {
        var flowId = UUID.randomUUID();
        var request = new PinDataRequest();
        request.setNodeId("node-1");
        request.setData(Map.of("output", "test-data"));

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);

        var result = flowController.pinNodeData(flowId, "1.0.0", request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(flowService).pinNodeData(flowId, "1.0.0", request);
    }

    @Test
    void pinNodeData_noEditAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        var request = new PinDataRequest();
        request.setNodeId("node-1");
        request.setData(Map.of("output", "test-data"));

        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.pinNodeData(flowId, "1.0.0", request, testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== unpinNodeData ==========

    @Test
    void unpinNodeData_withEditAccess_returnsNoContent() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(true);

        var result = flowController.unpinNodeData(flowId, "1.0.0", "node-1", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(flowService).unpinNodeData(flowId, "1.0.0", "node-1");
    }

    @Test
    void unpinNodeData_noEditAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasEditAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowController.unpinNodeData(flowId, "1.0.0", "node-1", testUser()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
