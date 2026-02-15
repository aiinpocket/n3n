package com.aiinpocket.n3n.component.controller;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.component.dto.*;
import com.aiinpocket.n3n.component.service.ComponentService;
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
class ComponentControllerTest {

    @Mock
    private ComponentService componentService;

    @InjectMocks
    private ComponentController componentController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private ComponentResponse sampleComponentResponse() {
        return ComponentResponse.builder()
                .id(UUID.randomUUID())
                .name("my-component")
                .displayName("My Component")
                .description("A test component")
                .category("data")
                .icon("database")
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .latestVersion("1.0.0")
                .activeVersion("1.0.0")
                .build();
    }

    private ComponentVersionResponse sampleVersionResponse(UUID componentId) {
        return ComponentVersionResponse.builder()
                .id(UUID.randomUUID())
                .componentId(componentId)
                .version("1.0.0")
                .image("myrepo/my-component:1.0.0")
                .interfaceDef(Map.of("inputs", List.of(), "outputs", List.of()))
                .configSchema(Map.of("type", "object"))
                .resources(Map.of("memory", "256Mi", "cpu", "200m"))
                .healthCheck(Map.of("path", "/health"))
                .status("active")
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .build();
    }

    // ===== listComponents (GET /api/components) =====

    @Test
    void listComponents_withoutCategory_returnsAllComponents() {
        var component = sampleComponentResponse();
        Page<ComponentResponse> page = new PageImpl<>(List.of(component));
        when(componentService.listComponents(any(Pageable.class))).thenReturn(page);

        var result = componentController.listComponents(null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("my-component");
        verify(componentService).listComponents(any(Pageable.class));
        verify(componentService, never()).listComponentsByCategory(any(), any());
    }

    @Test
    void listComponents_withCategory_returnsFilteredComponents() {
        var component = sampleComponentResponse();
        Page<ComponentResponse> page = new PageImpl<>(List.of(component));
        when(componentService.listComponentsByCategory(eq("data"), any(Pageable.class))).thenReturn(page);

        var result = componentController.listComponents("data", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        verify(componentService).listComponentsByCategory(eq("data"), any(Pageable.class));
        verify(componentService, never()).listComponents(any());
    }

    @Test
    void listComponents_emptyResult_returnsEmptyPage() {
        Page<ComponentResponse> emptyPage = new PageImpl<>(List.of());
        when(componentService.listComponents(any(Pageable.class))).thenReturn(emptyPage);

        var result = componentController.listComponents(null, PageRequest.of(0, 20));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).isEmpty();
    }

    // ===== getComponent (GET /api/components/{id}) =====

    @Test
    void getComponent_existingId_returnsComponent() {
        var component = sampleComponentResponse();
        UUID id = component.getId();
        when(componentService.getComponent(id)).thenReturn(component);

        var result = componentController.getComponent(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(id);
        assertThat(result.getBody().getName()).isEqualTo("my-component");
        verify(componentService).getComponent(id);
    }

    @Test
    void getComponent_nonExistentId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(componentService.getComponent(id))
                .thenThrow(new ResourceNotFoundException("Component not found: " + id));

        assertThatThrownBy(() -> componentController.getComponent(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    // ===== getComponentByName (GET /api/components/by-name/{name}) =====

    @Test
    void getComponentByName_existingName_returnsComponent() {
        var component = sampleComponentResponse();
        when(componentService.getComponentByName("my-component")).thenReturn(component);

        var result = componentController.getComponentByName("my-component");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("my-component");
        verify(componentService).getComponentByName("my-component");
    }

    @Test
    void getComponentByName_nonExistentName_throwsResourceNotFound() {
        when(componentService.getComponentByName("unknown"))
                .thenThrow(new ResourceNotFoundException("Component not found: unknown"));

        assertThatThrownBy(() -> componentController.getComponentByName("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    // ===== createComponent (POST /api/components) =====

    @Test
    void createComponent_validRequest_returnsCreatedComponent() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());
        var component = sampleComponentResponse();

        var request = new CreateComponentRequest();
        request.setName("my-component");
        request.setDisplayName("My Component");
        request.setDescription("A test component");
        request.setCategory("data");
        request.setIcon("database");

        when(componentService.createComponent(any(CreateComponentRequest.class), eq(userId)))
                .thenReturn(component);

        var result = componentController.createComponent(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("my-component");
        verify(componentService).createComponent(any(CreateComponentRequest.class), eq(userId));
    }

    @Test
    void createComponent_duplicateName_throwsIllegalArgument() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());

        var request = new CreateComponentRequest();
        request.setName("existing-component");
        request.setDisplayName("Existing Component");

        when(componentService.createComponent(any(CreateComponentRequest.class), eq(userId)))
                .thenThrow(new IllegalArgumentException("Component with name 'existing-component' already exists"));

        assertThatThrownBy(() -> componentController.createComponent(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createComponent_extractsUserIdFromUserDetails() {
        var user = testUser();
        UUID expectedUserId = UUID.fromString(user.getUsername());
        var component = sampleComponentResponse();

        var request = new CreateComponentRequest();
        request.setName("new-component");
        request.setDisplayName("New Component");

        when(componentService.createComponent(any(CreateComponentRequest.class), eq(expectedUserId)))
                .thenReturn(component);

        componentController.createComponent(request, user);

        verify(componentService).createComponent(any(CreateComponentRequest.class), eq(expectedUserId));
    }

    // ===== updateComponent (PUT /api/components/{id}) =====

    @Test
    void updateComponent_validRequest_returnsUpdatedComponent() {
        UUID id = UUID.randomUUID();
        var component = sampleComponentResponse();
        component.setDisplayName("Updated Component");

        var request = new UpdateComponentRequest();
        request.setDisplayName("Updated Component");

        when(componentService.updateComponent(eq(id), any(UpdateComponentRequest.class)))
                .thenReturn(component);

        var result = componentController.updateComponent(id, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDisplayName()).isEqualTo("Updated Component");
        verify(componentService).updateComponent(eq(id), any(UpdateComponentRequest.class));
    }

    @Test
    void updateComponent_nonExistentId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        var request = new UpdateComponentRequest();
        request.setDisplayName("Updated Component");

        when(componentService.updateComponent(eq(id), any(UpdateComponentRequest.class)))
                .thenThrow(new ResourceNotFoundException("Component not found: " + id));

        assertThatThrownBy(() -> componentController.updateComponent(id, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    @Test
    void updateComponent_partialUpdate_passesRequestToService() {
        UUID id = UUID.randomUUID();
        var component = sampleComponentResponse();

        var request = new UpdateComponentRequest();
        request.setDescription("Only updating description");

        when(componentService.updateComponent(eq(id), any(UpdateComponentRequest.class)))
                .thenReturn(component);

        var result = componentController.updateComponent(id, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(componentService).updateComponent(eq(id), any(UpdateComponentRequest.class));
    }

    // ===== deleteComponent (DELETE /api/components/{id}) =====

    @Test
    void deleteComponent_existingId_returnsNoContent() {
        UUID id = UUID.randomUUID();
        doNothing().when(componentService).deleteComponent(id);

        var result = componentController.deleteComponent(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(componentService).deleteComponent(id);
    }

    @Test
    void deleteComponent_nonExistentId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Component not found: " + id))
                .when(componentService).deleteComponent(id);

        assertThatThrownBy(() -> componentController.deleteComponent(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    // ===== listVersions (GET /api/components/{componentId}/versions) =====

    @Test
    void listVersions_existingComponent_returnsVersions() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        when(componentService.listVersions(componentId)).thenReturn(List.of(version));

        var result = componentController.listVersions(componentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getVersion()).isEqualTo("1.0.0");
        verify(componentService).listVersions(componentId);
    }

    @Test
    void listVersions_nonExistentComponent_throwsResourceNotFound() {
        UUID componentId = UUID.randomUUID();
        when(componentService.listVersions(componentId))
                .thenThrow(new ResourceNotFoundException("Component not found: " + componentId));

        assertThatThrownBy(() -> componentController.listVersions(componentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    @Test
    void listVersions_noVersions_returnsEmptyList() {
        UUID componentId = UUID.randomUUID();
        when(componentService.listVersions(componentId)).thenReturn(List.of());

        var result = componentController.listVersions(componentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // ===== getVersion (GET /api/components/{componentId}/versions/{version}) =====

    @Test
    void getVersion_existingVersion_returnsVersion() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        when(componentService.getVersion(componentId, "1.0.0")).thenReturn(version);

        var result = componentController.getVersion(componentId, "1.0.0");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getBody().getComponentId()).isEqualTo(componentId);
        verify(componentService).getVersion(componentId, "1.0.0");
    }

    @Test
    void getVersion_nonExistentVersion_throwsResourceNotFound() {
        UUID componentId = UUID.randomUUID();
        when(componentService.getVersion(componentId, "9.9.9"))
                .thenThrow(new ResourceNotFoundException("Version not found: 9.9.9"));

        assertThatThrownBy(() -> componentController.getVersion(componentId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");
    }

    // ===== getActiveVersion (GET /api/components/{componentId}/versions/active) =====

    @Test
    void getActiveVersion_hasActiveVersion_returnsActiveVersion() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        when(componentService.getActiveVersion(componentId)).thenReturn(version);

        var result = componentController.getActiveVersion(componentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("active");
        verify(componentService).getActiveVersion(componentId);
    }

    @Test
    void getActiveVersion_noActiveVersion_throwsResourceNotFound() {
        UUID componentId = UUID.randomUUID();
        when(componentService.getActiveVersion(componentId))
                .thenThrow(new ResourceNotFoundException("No active version for component: " + componentId));

        assertThatThrownBy(() -> componentController.getActiveVersion(componentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active version");
    }

    // ===== createVersion (POST /api/components/{componentId}/versions) =====

    @Test
    void createVersion_validRequest_returnsCreatedVersion() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);

        var request = new CreateVersionRequest();
        request.setVersion("1.0.0");
        request.setImage("myrepo/my-component:1.0.0");
        request.setInterfaceDef(Map.of("inputs", List.of(), "outputs", List.of()));
        request.setConfigSchema(Map.of("type", "object"));

        when(componentService.createVersion(eq(componentId), any(CreateVersionRequest.class), eq(userId)))
                .thenReturn(version);

        var result = componentController.createVersion(componentId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getBody().getComponentId()).isEqualTo(componentId);
        verify(componentService).createVersion(eq(componentId), any(CreateVersionRequest.class), eq(userId));
    }

    @Test
    void createVersion_nonExistentComponent_throwsResourceNotFound() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());
        UUID componentId = UUID.randomUUID();

        var request = new CreateVersionRequest();
        request.setVersion("1.0.0");
        request.setImage("myrepo/my-component:1.0.0");
        request.setInterfaceDef(Map.of("inputs", List.of()));

        when(componentService.createVersion(eq(componentId), any(CreateVersionRequest.class), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Component not found: " + componentId));

        assertThatThrownBy(() -> componentController.createVersion(componentId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component not found");
    }

    @Test
    void createVersion_duplicateVersion_throwsIllegalArgument() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());
        UUID componentId = UUID.randomUUID();

        var request = new CreateVersionRequest();
        request.setVersion("1.0.0");
        request.setImage("myrepo/my-component:1.0.0");
        request.setInterfaceDef(Map.of("inputs", List.of()));

        when(componentService.createVersion(eq(componentId), any(CreateVersionRequest.class), eq(userId)))
                .thenThrow(new IllegalArgumentException("Version '1.0.0' already exists"));

        assertThatThrownBy(() -> componentController.createVersion(componentId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createVersion_extractsUserIdFromUserDetails() {
        var user = testUser();
        UUID expectedUserId = UUID.fromString(user.getUsername());
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);

        var request = new CreateVersionRequest();
        request.setVersion("2.0.0");
        request.setImage("myrepo/my-component:2.0.0");
        request.setInterfaceDef(Map.of("inputs", List.of()));

        when(componentService.createVersion(eq(componentId), any(CreateVersionRequest.class), eq(expectedUserId)))
                .thenReturn(version);

        componentController.createVersion(componentId, request, user);

        verify(componentService).createVersion(eq(componentId), any(CreateVersionRequest.class), eq(expectedUserId));
    }

    // ===== activateVersion (POST /api/components/{componentId}/versions/{version}/activate) =====

    @Test
    void activateVersion_existingVersion_returnsActivatedVersion() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        when(componentService.activateVersion(componentId, "1.0.0")).thenReturn(version);

        var result = componentController.activateVersion(componentId, "1.0.0");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("active");
        verify(componentService).activateVersion(componentId, "1.0.0");
    }

    @Test
    void activateVersion_nonExistentVersion_throwsResourceNotFound() {
        UUID componentId = UUID.randomUUID();
        when(componentService.activateVersion(componentId, "9.9.9"))
                .thenThrow(new ResourceNotFoundException("Version not found: 9.9.9"));

        assertThatThrownBy(() -> componentController.activateVersion(componentId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");
    }

    // ===== deprecateVersion (POST /api/components/{componentId}/versions/{version}/deprecate) =====

    @Test
    void deprecateVersion_existingVersion_returnsDeprecatedVersion() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        version.setStatus("deprecated");
        when(componentService.deprecateVersion(componentId, "1.0.0")).thenReturn(version);

        var result = componentController.deprecateVersion(componentId, "1.0.0");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("deprecated");
        verify(componentService).deprecateVersion(componentId, "1.0.0");
    }

    @Test
    void deprecateVersion_nonExistentVersion_throwsResourceNotFound() {
        UUID componentId = UUID.randomUUID();
        when(componentService.deprecateVersion(componentId, "9.9.9"))
                .thenThrow(new ResourceNotFoundException("Version not found: 9.9.9"));

        assertThatThrownBy(() -> componentController.deprecateVersion(componentId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found");
    }

    // ===== Edge cases =====

    @Test
    void listComponents_multiplePages_returnsCorrectPage() {
        var comp1 = sampleComponentResponse();
        var comp2 = sampleComponentResponse();
        comp2.setName("another-component");
        Page<ComponentResponse> page = new PageImpl<>(List.of(comp1, comp2), PageRequest.of(0, 10), 2);
        when(componentService.listComponents(any(Pageable.class))).thenReturn(page);

        var result = componentController.listComponents(null, PageRequest.of(0, 10));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(2);
        assertThat(result.getBody().getContent()).hasSize(2);
    }

    @Test
    void listVersions_multipleVersions_returnsAll() {
        UUID componentId = UUID.randomUUID();
        var v1 = sampleVersionResponse(componentId);
        v1.setVersion("1.0.0");
        var v2 = sampleVersionResponse(componentId);
        v2.setVersion("2.0.0");
        v2.setStatus("deprecated");
        when(componentService.listVersions(componentId)).thenReturn(List.of(v1, v2));

        var result = componentController.listVersions(componentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
    }

    @Test
    void getVersion_returnsCompleteVersionData() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        when(componentService.getVersion(componentId, "1.0.0")).thenReturn(version);

        var result = componentController.getVersion(componentId, "1.0.0");

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getImage()).isEqualTo("myrepo/my-component:1.0.0");
        assertThat(result.getBody().getInterfaceDef()).isNotNull();
        assertThat(result.getBody().getConfigSchema()).isNotNull();
        assertThat(result.getBody().getResources()).isNotNull();
        assertThat(result.getBody().getHealthCheck()).isNotNull();
        assertThat(result.getBody().getCreatedBy()).isNotNull();
        assertThat(result.getBody().getCreatedAt()).isNotNull();
    }

    @Test
    void createComponent_responseContainsAllFields() {
        var user = testUser();
        UUID userId = UUID.fromString(user.getUsername());
        var component = sampleComponentResponse();

        var request = new CreateComponentRequest();
        request.setName("full-component");
        request.setDisplayName("Full Component");
        request.setDescription("Complete component");
        request.setCategory("integration");
        request.setIcon("plug");

        when(componentService.createComponent(any(CreateComponentRequest.class), eq(userId)))
                .thenReturn(component);

        var result = componentController.createComponent(request, user);

        assertThat(result.getBody()).isNotNull();
        var body = result.getBody();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getName()).isNotNull();
        assertThat(body.getDisplayName()).isNotNull();
        assertThat(body.getDescription()).isNotNull();
        assertThat(body.getCategory()).isNotNull();
        assertThat(body.getIcon()).isNotNull();
        assertThat(body.getCreatedBy()).isNotNull();
        assertThat(body.getCreatedAt()).isNotNull();
    }

    @Test
    void deleteComponent_calledOnce_verifySingleInvocation() {
        UUID id = UUID.randomUUID();
        doNothing().when(componentService).deleteComponent(id);

        componentController.deleteComponent(id);

        verify(componentService, times(1)).deleteComponent(id);
    }

    @Test
    void activateVersion_alreadyActive_returnsOk() {
        UUID componentId = UUID.randomUUID();
        var version = sampleVersionResponse(componentId);
        // Service returns the already-active version unchanged
        when(componentService.activateVersion(componentId, "1.0.0")).thenReturn(version);

        var result = componentController.activateVersion(componentId, "1.0.0");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("active");
    }
}
