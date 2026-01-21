package com.aiinpocket.n3n.component.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.component.dto.*;
import com.aiinpocket.n3n.component.entity.Component;
import com.aiinpocket.n3n.component.entity.ComponentVersion;
import com.aiinpocket.n3n.component.repository.ComponentRepository;
import com.aiinpocket.n3n.component.repository.ComponentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentService {

    private final ComponentRepository componentRepository;
    private final ComponentVersionRepository componentVersionRepository;

    public Page<ComponentResponse> listComponents(Pageable pageable) {
        return componentRepository.findByIsDeletedFalse(pageable)
            .map(this::enrichComponent);
    }

    public Page<ComponentResponse> listComponentsByCategory(String category, Pageable pageable) {
        return componentRepository.findByCategoryAndIsDeletedFalse(category, pageable)
            .map(this::enrichComponent);
    }

    public ComponentResponse getComponent(UUID id) {
        Component component = componentRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + id));
        return enrichComponent(component);
    }

    public ComponentResponse getComponentByName(String name) {
        Component component = componentRepository.findByNameAndIsDeletedFalse(name)
            .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + name));
        return enrichComponent(component);
    }

    @Transactional
    public ComponentResponse createComponent(CreateComponentRequest request, UUID userId) {
        if (componentRepository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new IllegalArgumentException("Component with name '" + request.getName() + "' already exists");
        }

        Component component = Component.builder()
            .name(request.getName())
            .displayName(request.getDisplayName())
            .description(request.getDescription())
            .category(request.getCategory())
            .icon(request.getIcon())
            .createdBy(userId)
            .build();

        component = componentRepository.save(component);
        log.info("Component created: id={}, name={}", component.getId(), component.getName());

        return ComponentResponse.from(component);
    }

    @Transactional
    public ComponentResponse updateComponent(UUID id, UpdateComponentRequest request) {
        Component component = componentRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + id));

        if (request.getDisplayName() != null) {
            component.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            component.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            component.setCategory(request.getCategory());
        }
        if (request.getIcon() != null) {
            component.setIcon(request.getIcon());
        }

        component = componentRepository.save(component);
        return enrichComponent(component);
    }

    @Transactional
    public void deleteComponent(UUID id) {
        Component component = componentRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + id));

        component.setIsDeleted(true);
        componentRepository.save(component);
        log.info("Component deleted: id={}", id);
    }

    // Version management

    public List<ComponentVersionResponse> listVersions(UUID componentId) {
        if (!componentRepository.existsById(componentId)) {
            throw new ResourceNotFoundException("Component not found: " + componentId);
        }

        return componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(componentId)
            .stream()
            .map(ComponentVersionResponse::from)
            .toList();
    }

    public ComponentVersionResponse getVersion(UUID componentId, String version) {
        ComponentVersion v = componentVersionRepository.findByComponentIdAndVersion(componentId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));
        return ComponentVersionResponse.from(v);
    }

    public ComponentVersionResponse getActiveVersion(UUID componentId) {
        ComponentVersion v = componentVersionRepository.findByComponentIdAndStatus(componentId, "active")
            .orElseThrow(() -> new ResourceNotFoundException("No active version for component: " + componentId));
        return ComponentVersionResponse.from(v);
    }

    @Transactional
    public ComponentVersionResponse createVersion(UUID componentId, CreateVersionRequest request, UUID userId) {
        if (!componentRepository.existsById(componentId)) {
            throw new ResourceNotFoundException("Component not found: " + componentId);
        }

        if (componentVersionRepository.existsByComponentIdAndVersion(componentId, request.getVersion())) {
            throw new IllegalArgumentException("Version '" + request.getVersion() + "' already exists");
        }

        ComponentVersion version = ComponentVersion.builder()
            .componentId(componentId)
            .version(request.getVersion())
            .image(request.getImage())
            .interfaceDef(request.getInterfaceDef())
            .configSchema(request.getConfigSchema())
            .resources(request.getResources() != null ? request.getResources() : Map.of("memory", "256Mi", "cpu", "200m"))
            .healthCheck(request.getHealthCheck())
            .status("disabled")
            .createdBy(userId)
            .build();

        version = componentVersionRepository.save(version);
        log.info("Component version created: componentId={}, version={}", componentId, version.getVersion());

        return ComponentVersionResponse.from(version);
    }

    @Transactional
    public ComponentVersionResponse activateVersion(UUID componentId, String version) {
        ComponentVersion v = componentVersionRepository.findByComponentIdAndVersion(componentId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        if ("active".equals(v.getStatus())) {
            return ComponentVersionResponse.from(v);
        }

        // Deactivate current active version
        componentVersionRepository.findByComponentIdAndStatus(componentId, "active")
            .ifPresent(current -> {
                current.setStatus("deprecated");
                componentVersionRepository.save(current);
            });

        v.setStatus("active");
        v = componentVersionRepository.save(v);
        log.info("Component version activated: componentId={}, version={}", componentId, version);

        return ComponentVersionResponse.from(v);
    }

    @Transactional
    public ComponentVersionResponse deprecateVersion(UUID componentId, String version) {
        ComponentVersion v = componentVersionRepository.findByComponentIdAndVersion(componentId, version)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        v.setStatus("deprecated");
        v = componentVersionRepository.save(v);
        log.info("Component version deprecated: componentId={}, version={}", componentId, version);

        return ComponentVersionResponse.from(v);
    }

    private ComponentResponse enrichComponent(Component component) {
        List<ComponentVersion> versions = componentVersionRepository.findByComponentIdOrderByCreatedAtDesc(component.getId());
        String latestVersion = versions.isEmpty() ? null : versions.get(0).getVersion();
        String activeVersion = versions.stream()
            .filter(v -> "active".equals(v.getStatus()))
            .findFirst()
            .map(ComponentVersion::getVersion)
            .orElse(null);
        return ComponentResponse.from(component, latestVersion, activeVersion);
    }
}
