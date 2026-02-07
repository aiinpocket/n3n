package com.aiinpocket.n3n.component.controller;

import com.aiinpocket.n3n.component.dto.*;
import com.aiinpocket.n3n.component.service.ComponentService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/components")
@RequiredArgsConstructor
@Tag(name = "Components", description = "Component registry")
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping
    public ResponseEntity<Page<ComponentResponse>> listComponents(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        if (category != null) {
            return ResponseEntity.ok(componentService.listComponentsByCategory(category, pageable));
        }
        return ResponseEntity.ok(componentService.listComponents(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComponentResponse> getComponent(@PathVariable UUID id) {
        return ResponseEntity.ok(componentService.getComponent(id));
    }

    @GetMapping("/by-name/{name}")
    public ResponseEntity<ComponentResponse> getComponentByName(@PathVariable String name) {
        return ResponseEntity.ok(componentService.getComponentByName(name));
    }

    @PostMapping
    public ResponseEntity<ComponentResponse> createComponent(
            @Valid @RequestBody CreateComponentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(componentService.createComponent(request, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComponentResponse> updateComponent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateComponentRequest request) {
        return ResponseEntity.ok(componentService.updateComponent(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteComponent(@PathVariable UUID id) {
        componentService.deleteComponent(id);
        return ResponseEntity.noContent().build();
    }

    // Version endpoints

    @GetMapping("/{componentId}/versions")
    public ResponseEntity<List<ComponentVersionResponse>> listVersions(@PathVariable UUID componentId) {
        return ResponseEntity.ok(componentService.listVersions(componentId));
    }

    @GetMapping("/{componentId}/versions/{version}")
    public ResponseEntity<ComponentVersionResponse> getVersion(
            @PathVariable UUID componentId,
            @PathVariable String version) {
        return ResponseEntity.ok(componentService.getVersion(componentId, version));
    }

    @GetMapping("/{componentId}/versions/active")
    public ResponseEntity<ComponentVersionResponse> getActiveVersion(@PathVariable UUID componentId) {
        return ResponseEntity.ok(componentService.getActiveVersion(componentId));
    }

    @PostMapping("/{componentId}/versions")
    public ResponseEntity<ComponentVersionResponse> createVersion(
            @PathVariable UUID componentId,
            @Valid @RequestBody CreateVersionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(componentService.createVersion(componentId, request, userId));
    }

    @PostMapping("/{componentId}/versions/{version}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComponentVersionResponse> activateVersion(
            @PathVariable UUID componentId,
            @PathVariable String version) {
        return ResponseEntity.ok(componentService.activateVersion(componentId, version));
    }

    @PostMapping("/{componentId}/versions/{version}/deprecate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComponentVersionResponse> deprecateVersion(
            @PathVariable UUID componentId,
            @PathVariable String version) {
        return ResponseEntity.ok(componentService.deprecateVersion(componentId, version));
    }
}
