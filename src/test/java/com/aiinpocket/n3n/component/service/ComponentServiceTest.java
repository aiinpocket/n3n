package com.aiinpocket.n3n.component.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.component.dto.*;
import com.aiinpocket.n3n.component.entity.Component;
import com.aiinpocket.n3n.component.entity.ComponentVersion;
import com.aiinpocket.n3n.component.repository.ComponentRepository;
import com.aiinpocket.n3n.component.repository.ComponentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComponentServiceTest extends BaseServiceTest {

    @Mock
    private ComponentRepository componentRepository;

    @Mock
    private ComponentVersionRepository componentVersionRepository;

    @InjectMocks
    private ComponentService componentService;

    private UUID userId;
    private UUID componentId;
    private Component testComponent;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        componentId = UUID.randomUUID();
        testComponent = Component.builder()
            .id(componentId)
            .name("http-request")
            .displayName("HTTP Request")
            .description("Send HTTP requests")
            .category("integration")
            .createdBy(userId)
            .isDeleted(false)
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("List Components")
    class ListComponents {

        @Test
        void listComponents_returnsEnrichedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Component> page = new PageImpl<>(List.of(testComponent));
            when(componentRepository.findByIsDeletedFalse(pageable)).thenReturn(page);
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            Page<ComponentResponse> result = componentService.listComponents(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("http-request");
        }

        @Test
        void listComponents_emptyResult() {
            Pageable pageable = PageRequest.of(0, 10);
            when(componentRepository.findByIsDeletedFalse(pageable)).thenReturn(Page.empty());

            Page<ComponentResponse> result = componentService.listComponents(pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        void listComponentsByCategory_filtersCorrectly() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Component> page = new PageImpl<>(List.of(testComponent));
            when(componentRepository.findByCategoryAndIsDeletedFalse("integration", pageable)).thenReturn(page);
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            Page<ComponentResponse> result = componentService.listComponentsByCategory("integration", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get Component")
    class GetComponent {

        @Test
        void getComponent_existingId_returnsComponent() {
            when(componentRepository.findByIdAndIsDeletedFalse(componentId)).thenReturn(Optional.of(testComponent));
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            ComponentResponse result = componentService.getComponent(componentId);

            assertThat(result.getName()).isEqualTo("http-request");
            assertThat(result.getDisplayName()).isEqualTo("HTTP Request");
        }

        @Test
        void getComponent_nonExistingId_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(componentRepository.findByIdAndIsDeletedFalse(nonExistingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.getComponent(nonExistingId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getComponentByName_existingName_returnsComponent() {
            when(componentRepository.findByNameAndIsDeletedFalse("http-request")).thenReturn(Optional.of(testComponent));
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            ComponentResponse result = componentService.getComponentByName("http-request");

            assertThat(result.getName()).isEqualTo("http-request");
        }

        @Test
        void getComponentByName_nonExisting_throwsException() {
            when(componentRepository.findByNameAndIsDeletedFalse("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.getComponentByName("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getComponent_withVersions_enrichesResponse() {
            ComponentVersion v1 = ComponentVersion.builder()
                .id(UUID.randomUUID())
                .componentId(componentId)
                .version("1.0.0")
                .status("active")
                .build();
            ComponentVersion v2 = ComponentVersion.builder()
                .id(UUID.randomUUID())
                .componentId(componentId)
                .version("2.0.0")
                .status("disabled")
                .build();

            when(componentRepository.findByIdAndIsDeletedFalse(componentId)).thenReturn(Optional.of(testComponent));
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of(v2, v1));

            ComponentResponse result = componentService.getComponent(componentId);

            assertThat(result.getLatestVersion()).isEqualTo("2.0.0");
            assertThat(result.getActiveVersion()).isEqualTo("1.0.0");
        }
    }

    @Nested
    @DisplayName("Create Component")
    class CreateComponent {

        @Test
        void createComponent_validRequest_createsSuccessfully() {
            CreateComponentRequest request = new CreateComponentRequest();
            request.setName("my-component");
            request.setDisplayName("My Component");
            request.setDescription("A test component");
            request.setCategory("utility");

            when(componentRepository.existsByNameAndIsDeletedFalse("my-component")).thenReturn(false);
            when(componentRepository.save(any(Component.class))).thenAnswer(inv -> {
                Component c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            ComponentResponse result = componentService.createComponent(request, userId);

            assertThat(result.getName()).isEqualTo("my-component");
            assertThat(result.getDisplayName()).isEqualTo("My Component");
            verify(componentRepository).save(argThat(c ->
                c.getName().equals("my-component") && c.getCreatedBy().equals(userId)
            ));
        }

        @Test
        void createComponent_duplicateName_throwsException() {
            CreateComponentRequest request = new CreateComponentRequest();
            request.setName("existing-component");
            request.setDisplayName("Existing");

            when(componentRepository.existsByNameAndIsDeletedFalse("existing-component")).thenReturn(true);

            assertThatThrownBy(() -> componentService.createComponent(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Update Component")
    class UpdateComponent {

        @Test
        void updateComponent_allFields_updatesSuccessfully() {
            UpdateComponentRequest request = new UpdateComponentRequest();
            request.setDisplayName("Updated Name");
            request.setDescription("Updated desc");
            request.setCategory("updated-category");
            request.setIcon("new-icon.png");

            when(componentRepository.findByIdAndIsDeletedFalse(componentId)).thenReturn(Optional.of(testComponent));
            when(componentRepository.save(any(Component.class))).thenReturn(testComponent);
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            componentService.updateComponent(componentId, request);

            verify(componentRepository).save(argThat(c ->
                "Updated Name".equals(c.getDisplayName()) &&
                "Updated desc".equals(c.getDescription()) &&
                "updated-category".equals(c.getCategory()) &&
                "new-icon.png".equals(c.getIcon())
            ));
        }

        @Test
        void updateComponent_partialUpdate_onlyChangesSpecifiedFields() {
            UpdateComponentRequest request = new UpdateComponentRequest();
            request.setDisplayName("New Display Name");

            when(componentRepository.findByIdAndIsDeletedFalse(componentId)).thenReturn(Optional.of(testComponent));
            when(componentRepository.save(any(Component.class))).thenReturn(testComponent);
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of());

            componentService.updateComponent(componentId, request);

            verify(componentRepository).save(argThat(c ->
                "New Display Name".equals(c.getDisplayName()) &&
                "Send HTTP requests".equals(c.getDescription())
            ));
        }

        @Test
        void updateComponent_nonExisting_throwsException() {
            UpdateComponentRequest request = new UpdateComponentRequest();
            UUID nonExisting = UUID.randomUUID();
            when(componentRepository.findByIdAndIsDeletedFalse(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.updateComponent(nonExisting, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete Component")
    class DeleteComponent {

        @Test
        void deleteComponent_existing_softDeletes() {
            when(componentRepository.findByIdAndIsDeletedFalse(componentId)).thenReturn(Optional.of(testComponent));
            when(componentRepository.save(any(Component.class))).thenReturn(testComponent);

            componentService.deleteComponent(componentId);

            verify(componentRepository).save(argThat(c -> c.getIsDeleted()));
        }

        @Test
        void deleteComponent_nonExisting_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(componentRepository.findByIdAndIsDeletedFalse(nonExisting)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.deleteComponent(nonExisting))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Version Management")
    class VersionManagement {

        private ComponentVersion activeVersion;
        private ComponentVersion disabledVersion;

        @BeforeEach
        void setUp() {
            activeVersion = ComponentVersion.builder()
                .id(UUID.randomUUID())
                .componentId(componentId)
                .version("1.0.0")
                .image("registry.io/comp:1.0.0")
                .interfaceDef(Map.of("inputs", List.of()))
                .status("active")
                .createdBy(userId)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

            disabledVersion = ComponentVersion.builder()
                .id(UUID.randomUUID())
                .componentId(componentId)
                .version("2.0.0")
                .image("registry.io/comp:2.0.0")
                .interfaceDef(Map.of("inputs", List.of()))
                .status("disabled")
                .createdBy(userId)
                .createdAt(Instant.now())
                .build();
        }

        @Test
        void listVersions_existingComponent_returnsVersions() {
            when(componentRepository.existsById(componentId)).thenReturn(true);
            when(componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId))
                .thenReturn(List.of(disabledVersion, activeVersion));

            List<ComponentVersionResponse> result = componentService.listVersions(componentId);

            assertThat(result).hasSize(2);
        }

        @Test
        void listVersions_nonExistingComponent_throwsException() {
            UUID nonExisting = UUID.randomUUID();
            when(componentRepository.existsById(nonExisting)).thenReturn(false);

            assertThatThrownBy(() -> componentService.listVersions(nonExisting))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getVersion_existing_returnsVersion() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "1.0.0"))
                .thenReturn(Optional.of(activeVersion));

            ComponentVersionResponse result = componentService.getVersion(componentId, "1.0.0");

            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getStatus()).isEqualTo("active");
        }

        @Test
        void getVersion_nonExisting_throwsException() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "9.9.9"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.getVersion(componentId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getActiveVersion_existing_returnsActiveVersion() {
            when(componentVersionRepository.findByComponentIdAndStatus(componentId, "active"))
                .thenReturn(Optional.of(activeVersion));

            ComponentVersionResponse result = componentService.getActiveVersion(componentId);

            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getStatus()).isEqualTo("active");
        }

        @Test
        void getActiveVersion_noActive_throwsException() {
            when(componentVersionRepository.findByComponentIdAndStatus(componentId, "active"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.getActiveVersion(componentId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void createVersion_validRequest_createsWithDefaults() {
            CreateVersionRequest request = new CreateVersionRequest();
            request.setVersion("3.0.0");
            request.setImage("registry.io/comp:3.0.0");
            request.setInterfaceDef(Map.of("inputs", List.of()));

            when(componentRepository.existsById(componentId)).thenReturn(true);
            when(componentVersionRepository.existsByComponentIdAndVersion(componentId, "3.0.0")).thenReturn(false);
            when(componentVersionRepository.save(any(ComponentVersion.class))).thenAnswer(inv -> {
                ComponentVersion v = inv.getArgument(0);
                v.setId(UUID.randomUUID());
                return v;
            });

            ComponentVersionResponse result = componentService.createVersion(componentId, request, userId);

            assertThat(result.getVersion()).isEqualTo("3.0.0");
            assertThat(result.getStatus()).isEqualTo("disabled");
            verify(componentVersionRepository).save(argThat(v ->
                v.getResources().containsKey("memory") && v.getResources().containsKey("cpu")
            ));
        }

        @Test
        void createVersion_duplicateVersion_throwsException() {
            CreateVersionRequest request = new CreateVersionRequest();
            request.setVersion("1.0.0");
            request.setImage("registry.io/comp:1.0.0");
            request.setInterfaceDef(Map.of("inputs", List.of()));

            when(componentRepository.existsById(componentId)).thenReturn(true);
            when(componentVersionRepository.existsByComponentIdAndVersion(componentId, "1.0.0")).thenReturn(true);

            assertThatThrownBy(() -> componentService.createVersion(componentId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        void createVersion_nonExistingComponent_throwsException() {
            CreateVersionRequest request = new CreateVersionRequest();
            request.setVersion("1.0.0");
            request.setImage("img");
            request.setInterfaceDef(Map.of());
            UUID nonExisting = UUID.randomUUID();
            when(componentRepository.existsById(nonExisting)).thenReturn(false);

            assertThatThrownBy(() -> componentService.createVersion(nonExisting, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void createVersion_withCustomResources_usesProvided() {
            Map<String, Object> customResources = Map.of("memory", "1Gi", "cpu", "1000m");
            CreateVersionRequest request = new CreateVersionRequest();
            request.setVersion("3.0.0");
            request.setImage("img");
            request.setInterfaceDef(Map.of());
            request.setResources(customResources);

            when(componentRepository.existsById(componentId)).thenReturn(true);
            when(componentVersionRepository.existsByComponentIdAndVersion(componentId, "3.0.0")).thenReturn(false);
            when(componentVersionRepository.save(any(ComponentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

            componentService.createVersion(componentId, request, userId);

            verify(componentVersionRepository).save(argThat(v ->
                "1Gi".equals(v.getResources().get("memory"))
            ));
        }

        @Test
        void activateVersion_deprecatesPreviousActive() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "2.0.0"))
                .thenReturn(Optional.of(disabledVersion));
            when(componentVersionRepository.findByComponentIdAndStatus(componentId, "active"))
                .thenReturn(Optional.of(activeVersion));
            when(componentVersionRepository.save(any(ComponentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

            ComponentVersionResponse result = componentService.activateVersion(componentId, "2.0.0");

            assertThat(result.getStatus()).isEqualTo("active");
            verify(componentVersionRepository, times(2)).save(any(ComponentVersion.class));
            verify(componentVersionRepository).save(argThat(v ->
                "1.0.0".equals(v.getVersion()) && "deprecated".equals(v.getStatus())
            ));
        }

        @Test
        void activateVersion_alreadyActive_returnsWithoutChange() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "1.0.0"))
                .thenReturn(Optional.of(activeVersion));

            ComponentVersionResponse result = componentService.activateVersion(componentId, "1.0.0");

            assertThat(result.getStatus()).isEqualTo("active");
            verify(componentVersionRepository, never()).save(any());
        }

        @Test
        void deprecateVersion_setsStatusDeprecated() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "1.0.0"))
                .thenReturn(Optional.of(activeVersion));
            when(componentVersionRepository.save(any(ComponentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

            ComponentVersionResponse result = componentService.deprecateVersion(componentId, "1.0.0");

            assertThat(result.getStatus()).isEqualTo("deprecated");
        }

        @Test
        void deprecateVersion_nonExisting_throwsException() {
            when(componentVersionRepository.findByComponentIdAndVersion(componentId, "9.9.9"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> componentService.deprecateVersion(componentId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
