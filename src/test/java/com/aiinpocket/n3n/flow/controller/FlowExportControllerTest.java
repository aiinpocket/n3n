package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportPreviewResponse;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportRequest;
import com.aiinpocket.n3n.flow.service.FlowExportService;
import com.aiinpocket.n3n.flow.service.FlowImportService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
class FlowExportControllerTest {

    @Mock
    private FlowExportService exportService;

    @Mock
    private FlowImportService importService;

    @Mock
    private ActivityService activityService;

    @Mock
    private FlowShareService flowShareService;

    @Mock
    private com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    @InjectMocks
    private FlowExportController flowExportController;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private FlowExportPackage sampleExportPackage() {
        return FlowExportPackage.builder()
                .version("1.0")
                .exportedAt(Instant.now())
                .exportedBy("te***@example.com")
                .flow(FlowExportPackage.FlowData.builder()
                        .name("Test Flow")
                        .description("A test flow")
                        .definition(Map.of("nodes", List.of(), "edges", List.of()))
                        .settings(Map.of())
                        .build())
                .dependencies(FlowExportPackage.FlowDependencies.builder()
                        .components(List.of())
                        .credentialPlaceholders(List.of())
                        .build())
                .checksum("abc123")
                .build();
    }

    // ========== exportFlow (specific version) ==========

    @Test
    void exportFlow_withAccess_returnsPackageWithHeaders() {
        var flowId = UUID.randomUUID();
        var pkg = sampleExportPackage();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(exportService.exportFlow(flowId, "1.0.0", userId)).thenReturn(pkg);

        var result = flowExportController.exportFlow(flowId, "1.0.0", testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getFlow().getName()).isEqualTo("Test Flow");
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("Test%20Flow_1.0.0.json");
        verify(activityService).logFlowExport(eq(userId), eq(flowId), eq("Test Flow"), eq("json"));
    }

    @Test
    void exportFlow_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowExportController.exportFlow(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    @Test
    void exportFlow_serviceThrows_propagatesException() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(exportService.exportFlow(flowId, "1.0.0", userId))
                .thenThrow(new ResourceNotFoundException("Version not found: 1.0.0"));

        assertThatThrownBy(() -> flowExportController.exportFlow(flowId, "1.0.0", testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");
    }

    // ========== exportFlowLatest ==========

    @Test
    void exportFlowLatest_withAccess_returnsPackage() {
        var flowId = UUID.randomUUID();
        var pkg = sampleExportPackage();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(exportService.exportFlowLatest(flowId, userId)).thenReturn(pkg);

        var result = flowExportController.exportFlowLatest(flowId, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("1.0");
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("Test%20Flow.json");
        verify(activityService).logFlowExport(eq(userId), eq(flowId), eq("Test Flow"), eq("json"));
    }

    @Test
    void exportFlowLatest_noAccess_throwsNotFound() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        assertThatThrownBy(() -> flowExportController.exportFlowLatest(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(flowId.toString());
    }

    @Test
    void exportFlowLatest_noVersions_propagatesException() {
        var flowId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(exportService.exportFlowLatest(flowId, userId))
                .thenThrow(new ResourceNotFoundException("No versions found for flow: " + flowId));

        assertThatThrownBy(() -> flowExportController.exportFlowLatest(flowId, testUser()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No versions found");
    }

    // ========== previewImport ==========

    @Test
    void previewImport_returnsPreview() {
        var pkg = sampleExportPackage();
        var preview = FlowImportPreviewResponse.builder()
                .flowName("Test Flow")
                .description("A test flow")
                .nodeCount(3)
                .edgeCount(2)
                .componentStatuses(List.of())
                .credentialRequirements(List.of())
                .canImport(true)
                .blockers(List.of())
                .build();
        when(importService.previewImport(any(FlowExportPackage.class), eq(userId))).thenReturn(preview);

        var result = flowExportController.previewImport(pkg, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getFlowName()).isEqualTo("Test Flow");
        assertThat(result.getBody().getNodeCount()).isEqualTo(3);
        assertThat(result.getBody().getEdgeCount()).isEqualTo(2);
        assertThat(result.getBody().isCanImport()).isTrue();
        assertThat(result.getBody().getBlockers()).isEmpty();
    }

    @Test
    void previewImport_withBlockers_returnsCannotImport() {
        var pkg = sampleExportPackage();
        var preview = FlowImportPreviewResponse.builder()
                .flowName("Test Flow")
                .description("A test flow")
                .nodeCount(3)
                .edgeCount(2)
                .componentStatuses(List.of())
                .credentialRequirements(List.of())
                .canImport(false)
                .blockers(List.of("Some required components cannot be auto-installed"))
                .build();
        when(importService.previewImport(any(FlowExportPackage.class), eq(userId))).thenReturn(preview);

        var result = flowExportController.previewImport(pkg, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isCanImport()).isFalse();
        assertThat(result.getBody().getBlockers()).hasSize(1);
    }

    @Test
    void previewImport_invalidChecksum_throwsException() {
        var pkg = sampleExportPackage();
        when(importService.previewImport(any(FlowExportPackage.class), eq(userId)))
                .thenThrow(new IllegalArgumentException("Export package checksum verification failed"));

        assertThatThrownBy(() -> flowExportController.previewImport(pkg, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    // ========== importFlow ==========

    @Test
    void importFlow_returnsCreated() {
        var pkg = sampleExportPackage();
        var request = FlowImportRequest.builder()
                .packageData(pkg)
                .newFlowName("Imported Flow")
                .build();

        var flowResponse = FlowResponse.builder()
                .id(UUID.randomUUID())
                .name("Imported Flow")
                .description("A test flow")
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .latestVersion("1.0.0")
                .build();

        when(importService.importFlow(any(FlowImportRequest.class), eq(userId))).thenReturn(flowResponse);

        var result = flowExportController.importFlow(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Imported Flow");
        verify(activityService).logFlowImport(eq(userId), eq(flowResponse.getId()), eq("Imported Flow"));
    }

    @Test
    void importFlow_withCredentialMappings_returnsCreated() {
        var pkg = sampleExportPackage();
        var credentialMappings = Map.of("node-1", UUID.randomUUID());
        var request = FlowImportRequest.builder()
                .packageData(pkg)
                .credentialMappings(credentialMappings)
                .build();

        var flowResponse = FlowResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Flow (Imported)")
                .createdBy(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(importService.importFlow(any(FlowImportRequest.class), eq(userId))).thenReturn(flowResponse);

        var result = flowExportController.importFlow(request, testUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        verify(activityService).logFlowImport(eq(userId), eq(flowResponse.getId()), eq(flowResponse.getName()));
    }

    @Test
    void importFlow_checksumFailed_throwsException() {
        var request = FlowImportRequest.builder()
                .packageData(sampleExportPackage())
                .build();

        when(importService.importFlow(any(FlowImportRequest.class), eq(userId)))
                .thenThrow(new IllegalArgumentException("Export package checksum verification failed"));

        assertThatThrownBy(() -> flowExportController.importFlow(request, testUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void importFlow_accessDenied_throwsException() {
        var request = FlowImportRequest.builder()
                .packageData(sampleExportPackage())
                .credentialMappings(Map.of("node-1", UUID.randomUUID()))
                .build();

        when(importService.importFlow(any(FlowImportRequest.class), eq(userId)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Credential not accessible"));

        assertThatThrownBy(() -> flowExportController.importFlow(request, testUser()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Credential not accessible");
    }
}
