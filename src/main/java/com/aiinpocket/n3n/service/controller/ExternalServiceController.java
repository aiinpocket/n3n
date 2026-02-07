package com.aiinpocket.n3n.service.controller;

import com.aiinpocket.n3n.service.ExternalServiceService;
import com.aiinpocket.n3n.service.dto.*;
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
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "External Services", description = "External service integration")
public class ExternalServiceController {

    private final ExternalServiceService serviceService;

    @GetMapping
    public ResponseEntity<Page<ServiceResponse>> listServices(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.listServices(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDetailResponse> getService(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.getService(id, userId));
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(
            @Valid @RequestBody CreateServiceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ServiceResponse response = serviceService.createService(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.updateService(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        serviceService.deleteService(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh-schema")
    public ResponseEntity<Map<String, Object>> refreshSchema(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.refreshSchema(id, userId));
    }

    @GetMapping("/{id}/endpoints")
    public ResponseEntity<List<EndpointResponse>> getEndpoints(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.getEndpoints(id, userId));
    }

    @PostMapping("/{id}/endpoints")
    public ResponseEntity<EndpointResponse> createEndpoint(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEndpointRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        EndpointResponse response = serviceService.createEndpoint(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{serviceId}/endpoints/{endpointId}")
    public ResponseEntity<EndpointResponse> updateEndpoint(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateEndpointRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.updateEndpoint(serviceId, endpointId, request, userId));
    }

    @DeleteMapping("/{serviceId}/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        serviceService.deleteEndpoint(serviceId, endpointId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.testConnection(id, userId));
    }

    @GetMapping("/{serviceId}/endpoints/{endpointId}/schema")
    public ResponseEntity<EndpointSchemaResponse> getEndpointSchema(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serviceService.getEndpointSchema(serviceId, endpointId, userId));
    }
}
