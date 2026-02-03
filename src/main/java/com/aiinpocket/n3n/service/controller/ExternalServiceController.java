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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ExternalServiceController {

    private final ExternalServiceService serviceService;

    @GetMapping
    public ResponseEntity<Page<ServiceResponse>> listServices(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(serviceService.listServices(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceDetailResponse> getService(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.getService(id));
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
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        serviceService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh-schema")
    public ResponseEntity<Map<String, Object>> refreshSchema(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.refreshSchema(id));
    }

    @GetMapping("/{id}/endpoints")
    public ResponseEntity<List<EndpointResponse>> getEndpoints(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.getEndpoints(id));
    }

    @PostMapping("/{id}/endpoints")
    public ResponseEntity<EndpointResponse> createEndpoint(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEndpointRequest request) {
        EndpointResponse response = serviceService.createEndpoint(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{serviceId}/endpoints/{endpointId}")
    public ResponseEntity<EndpointResponse> updateEndpoint(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody CreateEndpointRequest request) {
        return ResponseEntity.ok(serviceService.updateEndpoint(serviceId, endpointId, request));
    }

    @DeleteMapping("/{serviceId}/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId) {
        serviceService.deleteEndpoint(serviceId, endpointId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.testConnection(id));
    }

    /**
     * Get endpoint schema for flow editor node configuration.
     * Returns configSchema and interfaceDefinition for the specified endpoint.
     */
    @GetMapping("/{serviceId}/endpoints/{endpointId}/schema")
    public ResponseEntity<EndpointSchemaResponse> getEndpointSchema(
            @PathVariable UUID serviceId,
            @PathVariable UUID endpointId) {
        return ResponseEntity.ok(serviceService.getEndpointSchema(serviceId, endpointId));
    }
}
