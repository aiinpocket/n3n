package com.aiinpocket.n3n.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.service.dto.*;
import com.aiinpocket.n3n.service.entity.ExternalService;
import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import com.aiinpocket.n3n.service.openapi.OpenApiParserService;
import com.aiinpocket.n3n.service.repository.ExternalServiceRepository;
import com.aiinpocket.n3n.service.repository.ServiceEndpointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ExternalServiceService {

    private final ExternalServiceRepository serviceRepository;
    private final ServiceEndpointRepository endpointRepository;
    private final OpenApiParserService openApiParser;
    private final RestClient restClient;

    public ExternalServiceService(ExternalServiceRepository serviceRepository,
                                  ServiceEndpointRepository endpointRepository,
                                  OpenApiParserService openApiParser) {
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.openApiParser = openApiParser;
        this.restClient = RestClient.create();
    }

    public Page<ServiceResponse> listServices(UUID userId, Pageable pageable) {
        return serviceRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(service -> {
                    int count = endpointRepository.countByServiceId(service.getId());
                    return ServiceResponse.from(service, count);
                });
    }

    private ExternalService findServiceWithOwnerCheck(UUID id, UUID userId) {
        ExternalService service = serviceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        if (!service.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return service;
    }

    public ServiceDetailResponse getService(UUID id, UUID userId) {
        ExternalService service = findServiceWithOwnerCheck(id, userId);
        List<ServiceEndpoint> endpoints = endpointRepository.findByServiceIdOrderByPathAsc(id);
        return ServiceDetailResponse.from(service, endpoints);
    }

    @Transactional
    public ServiceResponse createService(CreateServiceRequest request, UUID userId) {
        if (serviceRepository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new IllegalArgumentException("Service with name '" + request.getName() + "' already exists");
        }

        ExternalService service = ExternalService.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .baseUrl(normalizeUrl(request.getBaseUrl()))
                .protocol(request.getProtocol() != null ? request.getProtocol() : "REST")
                .schemaUrl(request.getSchemaUrl())
                .authType(request.getAuthType())
                .authConfig(request.getAuthConfig())
                .healthCheck(request.getHealthCheck())
                .status("active")
                .createdBy(userId)
                .build();

        service = serviceRepository.save(service);
        log.info("Created external service: id={}, name={}", service.getId(), service.getName());

        int endpointCount = 0;

        if (request.getSchemaUrl() != null && !request.getSchemaUrl().isBlank()) {
            try {
                List<ServiceEndpoint> endpoints = openApiParser.parseFromUrl(
                        service.getBaseUrl(), service.getSchemaUrl(), service.getId());
                endpointRepository.saveAll(endpoints);
                endpointCount = endpoints.size();
                log.info("Parsed {} endpoints from OpenAPI schema for service {}", endpointCount, service.getName());
            } catch (Exception e) {
                log.warn("Failed to parse OpenAPI schema for service {}: {}", service.getName(), e.getMessage());
                service.setStatus("error");
                serviceRepository.save(service);
            }
        }

        if (request.getEndpoints() != null && !request.getEndpoints().isEmpty()) {
            for (CreateEndpointRequest endpointReq : request.getEndpoints()) {
                ServiceEndpoint endpoint = createEndpointFromRequest(endpointReq, service.getId());
                endpointRepository.save(endpoint);
                endpointCount++;
            }
            log.info("Created {} manual endpoints for service {}", request.getEndpoints().size(), service.getName());
        }

        return ServiceResponse.from(service, endpointCount);
    }

    @Transactional
    public ServiceResponse updateService(UUID id, UpdateServiceRequest request, UUID userId) {
        ExternalService service = serviceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));

        if (request.getDisplayName() != null) {
            service.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription());
        }
        if (request.getBaseUrl() != null) {
            service.setBaseUrl(normalizeUrl(request.getBaseUrl()));
        }
        if (request.getSchemaUrl() != null) {
            service.setSchemaUrl(request.getSchemaUrl());
        }
        if (request.getAuthType() != null) {
            service.setAuthType(request.getAuthType());
        }
        if (request.getAuthConfig() != null) {
            service.setAuthConfig(request.getAuthConfig());
        }
        if (request.getHealthCheck() != null) {
            service.setHealthCheck(request.getHealthCheck());
        }
        if (request.getStatus() != null) {
            service.setStatus(request.getStatus());
        }

        service = serviceRepository.save(service);
        int count = endpointRepository.countByServiceId(id);
        log.info("Updated external service: id={}", id);

        return ServiceResponse.from(service, count);
    }

    @Transactional
    public void deleteService(UUID id, UUID userId) {
        ExternalService service = findServiceWithOwnerCheck(id, userId);
        service.setIsDeleted(true);
        serviceRepository.save(service);
        log.info("Deleted external service: id={}, name={}", id, service.getName());
    }

    @Transactional
    public Map<String, Object> refreshSchema(UUID id, UUID userId) {
        ExternalService service = findServiceWithOwnerCheck(id, userId);

        if (service.getSchemaUrl() == null || service.getSchemaUrl().isBlank()) {
            throw new IllegalArgumentException("Service does not have a schema URL configured");
        }

        List<ServiceEndpoint> existingEndpoints = endpointRepository.findByServiceIdOrderByPathAsc(id);
        int existingCount = existingEndpoints.size();

        List<ServiceEndpoint> newEndpoints = openApiParser.parseFromUrl(
                service.getBaseUrl(), service.getSchemaUrl(), id);

        int added = 0;
        int updated = 0;

        for (ServiceEndpoint newEndpoint : newEndpoints) {
            var existing = endpointRepository.findByServiceIdAndMethodAndPath(
                    id, newEndpoint.getMethod(), newEndpoint.getPath());

            if (existing.isPresent()) {
                ServiceEndpoint existingEndpoint = existing.get();
                existingEndpoint.setName(newEndpoint.getName());
                existingEndpoint.setDescription(newEndpoint.getDescription());
                existingEndpoint.setPathParams(newEndpoint.getPathParams());
                existingEndpoint.setQueryParams(newEndpoint.getQueryParams());
                existingEndpoint.setRequestBody(newEndpoint.getRequestBody());
                existingEndpoint.setResponseSchema(newEndpoint.getResponseSchema());
                existingEndpoint.setTags(newEndpoint.getTags());
                endpointRepository.save(existingEndpoint);
                updated++;
            } else {
                endpointRepository.save(newEndpoint);
                added++;
            }
        }

        service.setStatus("active");
        serviceRepository.save(service);

        log.info("Refreshed schema for service {}: added={}, updated={}", service.getName(), added, updated);

        return Map.of(
                "message", "Schema refreshed successfully",
                "addedEndpoints", added,
                "updatedEndpoints", updated,
                "totalEndpoints", newEndpoints.size()
        );
    }

    public List<EndpointResponse> getEndpoints(UUID serviceId, UUID userId) {
        findServiceWithOwnerCheck(serviceId, userId);
        return endpointRepository.findByServiceIdOrderByPathAsc(serviceId)
                .stream()
                .map(EndpointResponse::from)
                .toList();
    }

    @Transactional
    public EndpointResponse createEndpoint(UUID serviceId, CreateEndpointRequest request, UUID userId) {
        findServiceWithOwnerCheck(serviceId, userId);

        if (endpointRepository.findByServiceIdAndMethodAndPath(serviceId, request.getMethod(), request.getPath()).isPresent()) {
            throw new IllegalArgumentException("Endpoint with method " + request.getMethod() + " and path " + request.getPath() + " already exists");
        }

        ServiceEndpoint endpoint = createEndpointFromRequest(request, serviceId);
        endpoint = endpointRepository.save(endpoint);
        log.info("Created endpoint: id={}, serviceId={}, method={}, path={}", endpoint.getId(), serviceId, endpoint.getMethod(), endpoint.getPath());

        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public EndpointResponse updateEndpoint(UUID serviceId, UUID endpointId, CreateEndpointRequest request, UUID userId) {
        findServiceWithOwnerCheck(serviceId, userId);
        ServiceEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));

        if (!endpoint.getServiceId().equals(serviceId)) {
            throw new IllegalArgumentException("Endpoint does not belong to the specified service");
        }

        endpoint.setName(request.getName());
        endpoint.setDescription(request.getDescription());
        endpoint.setMethod(request.getMethod());
        endpoint.setPath(request.getPath());
        endpoint.setPathParams(request.getPathParams());
        endpoint.setQueryParams(request.getQueryParams());
        endpoint.setRequestBody(request.getRequestBody());
        endpoint.setResponseSchema(request.getResponseSchema());
        endpoint.setTags(request.getTags());

        endpoint = endpointRepository.save(endpoint);
        log.info("Updated endpoint: id={}", endpointId);

        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public void deleteEndpoint(UUID serviceId, UUID endpointId, UUID userId) {
        findServiceWithOwnerCheck(serviceId, userId);
        ServiceEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));

        if (!endpoint.getServiceId().equals(serviceId)) {
            throw new IllegalArgumentException("Endpoint does not belong to the specified service");
        }

        endpointRepository.delete(endpoint);
        log.info("Deleted endpoint: id={}", endpointId);
    }

    public Map<String, Object> testConnection(UUID id, UUID userId) {
        ExternalService service = findServiceWithOwnerCheck(id, userId);

        String testUrl = service.getBaseUrl();
        if (service.getHealthCheck() != null && service.getHealthCheck().get("path") != null) {
            testUrl = testUrl + service.getHealthCheck().get("path");
        }

        long startTime = System.currentTimeMillis();
        try {
            restClient.get().uri(testUrl).retrieve().toBodilessEntity();
            long latency = System.currentTimeMillis() - startTime;

            return Map.of(
                    "success", true,
                    "latencyMs", latency,
                    "message", "Service is healthy"
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Connection test failed for service {}: {}", service.getName(), e.getMessage());

            return Map.of(
                    "success", false,
                    "latencyMs", latency,
                    "message", "Connection failed: " + e.getMessage()
            );
        }
    }

    private ServiceEndpoint createEndpointFromRequest(CreateEndpointRequest request, UUID serviceId) {
        return ServiceEndpoint.builder()
                .serviceId(serviceId)
                .name(request.getName())
                .description(request.getDescription())
                .method(request.getMethod())
                .path(request.getPath())
                .pathParams(request.getPathParams())
                .queryParams(request.getQueryParams())
                .requestBody(request.getRequestBody())
                .responseSchema(request.getResponseSchema())
                .tags(request.getTags())
                .isEnabled(true)
                .build();
    }

    private String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Get endpoint schema for flow editor node configuration.
     */
    /**
     * Internal use: get endpoint schema without ownership check (for flow engine).
     */
    public EndpointSchemaResponse getEndpointSchema(UUID serviceId, UUID endpointId) {
        ExternalService service = serviceRepository.findByIdAndIsDeletedFalse(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
        return getEndpointSchemaInternal(service, serviceId, endpointId);
    }

    public EndpointSchemaResponse getEndpointSchema(UUID serviceId, UUID endpointId, UUID userId) {
        ExternalService service = findServiceWithOwnerCheck(serviceId, userId);
        return getEndpointSchemaInternal(service, serviceId, endpointId);
    }

    private EndpointSchemaResponse getEndpointSchemaInternal(ExternalService service, UUID serviceId, UUID endpointId) {

        ServiceEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));

        if (!endpoint.getServiceId().equals(serviceId)) {
            throw new IllegalArgumentException("Endpoint does not belong to the specified service");
        }

        Map<String, Object> configSchema = buildConfigSchema(endpoint);
        Map<String, Object> interfaceDefinition = buildInterfaceDefinition(endpoint);

        return EndpointSchemaResponse.builder()
                .serviceId(serviceId)
                .endpointId(endpointId)
                .displayName(service.getDisplayName() + " - " + endpoint.getName())
                .description(endpoint.getDescription())
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .configSchema(configSchema)
                .interfaceDefinition(interfaceDefinition)
                .build();
    }

    /**
     * Build JSON Schema format configSchema from endpoint definition.
     */
    private Map<String, Object> buildConfigSchema(ServiceEndpoint endpoint) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();

        // Add path parameters section
        if (endpoint.getPathParams() != null && !endpoint.getPathParams().isEmpty()) {
            Map<String, Object> pathParamsSchema = new java.util.LinkedHashMap<>();
            pathParamsSchema.put("type", "object");
            pathParamsSchema.put("title", "Path Parameters");
            pathParamsSchema.put("description", "URL path parameters (e.g., /users/{userId})");
            pathParamsSchema.put("properties", endpoint.getPathParams().getOrDefault("properties", Map.of()));

            // Mark all path params as required
            Object pathRequired = endpoint.getPathParams().get("required");
            if (pathRequired != null) {
                pathParamsSchema.put("required", pathRequired);
            }

            properties.put("pathParams", pathParamsSchema);
            required.add("pathParams");
        }

        // Add query parameters section
        if (endpoint.getQueryParams() != null && !endpoint.getQueryParams().isEmpty()) {
            Map<String, Object> queryParamsSchema = new java.util.LinkedHashMap<>();
            queryParamsSchema.put("type", "object");
            queryParamsSchema.put("title", "Query Parameters");
            queryParamsSchema.put("description", "URL query parameters");
            queryParamsSchema.put("properties", endpoint.getQueryParams().getOrDefault("properties", Map.of()));

            Object queryRequired = endpoint.getQueryParams().get("required");
            if (queryRequired != null) {
                queryParamsSchema.put("required", queryRequired);
            }

            properties.put("queryParams", queryParamsSchema);
        }

        // Add request body section (for POST, PUT, PATCH)
        if (endpoint.getRequestBody() != null && !endpoint.getRequestBody().isEmpty()) {
            Map<String, Object> requestBodySchema = new java.util.LinkedHashMap<>();
            requestBodySchema.put("type", "object");
            requestBodySchema.put("title", "Request Body");
            requestBodySchema.put("description", "HTTP request body");

            // Copy all properties from requestBody schema
            if (endpoint.getRequestBody().containsKey("properties")) {
                requestBodySchema.put("properties", endpoint.getRequestBody().get("properties"));
            }
            if (endpoint.getRequestBody().containsKey("required")) {
                requestBodySchema.put("required", endpoint.getRequestBody().get("required"));
            }

            properties.put("requestBody", requestBodySchema);

            // Mark as required if method requires body
            String method = endpoint.getMethod().toUpperCase();
            if (List.of("POST", "PUT", "PATCH").contains(method)) {
                required.add("requestBody");
            }
        }

        // Add headers section
        properties.put("headers", Map.of(
                "type", "object",
                "title", "Custom Headers",
                "description", "Additional HTTP headers",
                "additionalProperties", Map.of("type", "string")
        ));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "x-endpoint-config", true
        );
    }

    /**
     * Build interface definition (inputs/outputs) from endpoint definition.
     */
    private Map<String, Object> buildInterfaceDefinition(ServiceEndpoint endpoint) {
        List<Map<String, Object>> inputs = new java.util.ArrayList<>();
        List<Map<String, Object>> outputs = new java.util.ArrayList<>();

        // Input: general input from previous node
        inputs.add(Map.of(
                "name", "input",
                "type", "any",
                "required", false,
                "description", "Input data from previous node (can be used in expressions)"
        ));

        // Output: based on responseSchema
        if (endpoint.getResponseSchema() != null && !endpoint.getResponseSchema().isEmpty()) {
            Map<String, Object> outputDef = new java.util.LinkedHashMap<>();
            outputDef.put("name", "output");
            outputDef.put("type", "object");
            outputDef.put("description", "HTTP response data");
            outputDef.put("schema", endpoint.getResponseSchema());
            outputs.add(outputDef);
        } else {
            outputs.add(Map.of(
                    "name", "output",
                    "type", "any",
                    "description", "HTTP response (structure depends on the API)"
            ));
        }

        // Also include status and headers in output
        outputs.add(Map.of(
                "name", "status",
                "type", "integer",
                "description", "HTTP status code"
        ));
        outputs.add(Map.of(
                "name", "headers",
                "type", "object",
                "description", "Response headers"
        ));

        return Map.of("inputs", inputs, "outputs", outputs);
    }
}
