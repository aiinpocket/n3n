package com.aiinpocket.n3n.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.service.dto.*;
import com.aiinpocket.n3n.service.entity.ExternalService;
import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import com.aiinpocket.n3n.service.openapi.OpenApiParserService;
import com.aiinpocket.n3n.service.repository.ExternalServiceRepository;
import com.aiinpocket.n3n.service.repository.ServiceEndpointRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExternalServiceServiceTest extends BaseServiceTest {

    @Mock
    private ExternalServiceRepository serviceRepository;

    @Mock
    private ServiceEndpointRepository endpointRepository;

    @Mock
    private OpenApiParserService openApiParser;

    @InjectMocks
    private ExternalServiceService externalServiceService;

    // ========== List Tests ==========

    @Test
    void listServices_returnsPagedServices() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        ExternalService service = createTestService();
        service.setCreatedBy(userId);
        Page<ExternalService> servicePage = new PageImpl<>(List.of(service));

        when(serviceRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)).thenReturn(servicePage);
        when(endpointRepository.countByServiceId(service.getId())).thenReturn(3);

        // When
        Page<ServiceResponse> result = externalServiceService.listServices(userId, pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEndpointCount()).isEqualTo(3);
    }

    // ========== Get Tests ==========

    @Test
    void getService_existingId_returnsServiceDetail() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        ServiceEndpoint endpoint = createTestEndpoint(service.getId());

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdOrderByPathAsc(service.getId())).thenReturn(List.of(endpoint));

        // When
        ServiceDetailResponse result = externalServiceService.getService(service.getId(), userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(service.getName());
        assertThat(result.getEndpoints()).hasSize(1);
    }

    @Test
    void getService_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(serviceRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> externalServiceService.getService(id, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    // ========== Create Tests ==========

    @Test
    void createService_validRequest_createsService() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateServiceRequest request = createServiceRequest("my-api");

        when(serviceRepository.existsByNameAndCreatedByAndIsDeletedFalse("my-api", userId)).thenReturn(false);
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> {
            ExternalService s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // When
        ServiceResponse result = externalServiceService.createService(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("my-api");
        verify(serviceRepository).save(any(ExternalService.class));
    }

    @Test
    void createService_duplicateName_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateServiceRequest request = createServiceRequest("existing-api");

        when(serviceRepository.existsByNameAndCreatedByAndIsDeletedFalse("existing-api", userId)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> externalServiceService.createService(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createService_withSchemaUrl_parsesEndpoints() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateServiceRequest request = createServiceRequest("api-with-schema");
        request.setSchemaUrl("https://api.example.com/openapi.json");

        ServiceEndpoint parsedEndpoint = createTestEndpoint(null);

        when(serviceRepository.existsByNameAndCreatedByAndIsDeletedFalse("api-with-schema", userId)).thenReturn(false);
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> {
            ExternalService s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(openApiParser.parseFromUrl(any(), eq("https://api.example.com/openapi.json"), any()))
                .thenReturn(List.of(parsedEndpoint));

        // When
        ServiceResponse result = externalServiceService.createService(request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(endpointRepository).saveAll(any());
    }

    @Test
    void createService_trailingSlashUrl_normalizes() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateServiceRequest request = createServiceRequest("api-slash");
        request.setBaseUrl("https://api.example.com/v1/");

        when(serviceRepository.existsByNameAndCreatedByAndIsDeletedFalse("api-slash", userId)).thenReturn(false);
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> {
            ExternalService s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // When
        externalServiceService.createService(request, userId);

        // Then
        verify(serviceRepository).save(argThat(s -> "https://api.example.com/v1".equals(s.getBaseUrl())));
    }

    // ========== Update Tests ==========

    @Test
    void updateService_existingService_updatesFields() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setDisplayName("Updated Name");
        request.setDescription("Updated description");

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.countByServiceId(service.getId())).thenReturn(2);

        // When
        ServiceResponse result = externalServiceService.updateService(service.getId(), request, userId);

        // Then
        assertThat(result.getDisplayName()).isEqualTo("Updated Name");
    }

    @Test
    void updateService_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(serviceRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> externalServiceService.updateService(id, new UpdateServiceRequest(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Delete Tests ==========

    @Test
    void deleteService_existingService_softDeletes() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        externalServiceService.deleteService(service.getId(), userId);

        // Then
        verify(serviceRepository).save(argThat(ExternalService::getIsDeleted));
    }

    @Test
    void deleteService_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(serviceRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> externalServiceService.deleteService(id, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Refresh Schema Tests ==========

    @Test
    void refreshSchema_withSchemaUrl_refreshesEndpoints() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        service.setSchemaUrl("https://api.example.com/openapi.json");

        ServiceEndpoint newEndpoint = createTestEndpoint(service.getId());
        newEndpoint.setMethod("POST");
        newEndpoint.setPath("/users");

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdOrderByPathAsc(service.getId())).thenReturn(List.of());
        when(openApiParser.parseFromUrl(any(), any(), any())).thenReturn(List.of(newEndpoint));
        when(endpointRepository.findByServiceIdAndMethodAndPath(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(endpointRepository.save(any(ServiceEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Map<String, Object> result = externalServiceService.refreshSchema(service.getId(), userId);

        // Then
        assertThat(result).containsEntry("addedEndpoints", 1);
        assertThat(result).containsEntry("updatedEndpoints", 0);
    }

    @Test
    void refreshSchema_noSchemaUrl_throwsException() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        service.setSchemaUrl(null);

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));

        // When/Then
        assertThatThrownBy(() -> externalServiceService.refreshSchema(service.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema URL");
    }

    @Test
    void refreshSchema_existingEndpoints_updatesInstead() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        service.setSchemaUrl("https://api.example.com/openapi.json");

        ServiceEndpoint existingEndpoint = createTestEndpoint(service.getId());
        existingEndpoint.setMethod("GET");
        existingEndpoint.setPath("/users");

        ServiceEndpoint parsedEndpoint = createTestEndpoint(service.getId());
        parsedEndpoint.setMethod("GET");
        parsedEndpoint.setPath("/users");
        parsedEndpoint.setName("Updated Users");

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdOrderByPathAsc(service.getId())).thenReturn(List.of(existingEndpoint));
        when(openApiParser.parseFromUrl(any(), any(), any())).thenReturn(List.of(parsedEndpoint));
        when(endpointRepository.findByServiceIdAndMethodAndPath(service.getId(), "GET", "/users"))
                .thenReturn(Optional.of(existingEndpoint));
        when(endpointRepository.save(any(ServiceEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceRepository.save(any(ExternalService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Map<String, Object> result = externalServiceService.refreshSchema(service.getId(), userId);

        // Then
        assertThat(result).containsEntry("addedEndpoints", 0);
        assertThat(result).containsEntry("updatedEndpoints", 1);
    }

    // ========== Endpoint Management Tests ==========

    @Test
    void getEndpoints_existingService_returnsEndpoints() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        ServiceEndpoint endpoint = createTestEndpoint(service.getId());

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdOrderByPathAsc(service.getId())).thenReturn(List.of(endpoint));

        // When
        List<EndpointResponse> result = externalServiceService.getEndpoints(service.getId(), userId);

        // Then
        assertThat(result).hasSize(1);
    }

    @Test
    void getEndpoints_nonExistingService_throwsException() {
        // Given
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(serviceRepository.findByIdAndIsDeletedFalse(serviceId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> externalServiceService.getEndpoints(serviceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createEndpoint_validRequest_createsEndpoint() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        CreateEndpointRequest request = new CreateEndpointRequest();
        request.setName("Get Users");
        request.setMethod("GET");
        request.setPath("/users");

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdAndMethodAndPath(service.getId(), "GET", "/users"))
                .thenReturn(Optional.empty());
        when(endpointRepository.save(any(ServiceEndpoint.class))).thenAnswer(invocation -> {
            ServiceEndpoint e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        // When
        EndpointResponse result = externalServiceService.createEndpoint(service.getId(), request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Get Users");
    }

    @Test
    void createEndpoint_duplicateMethodPath_throwsException() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        CreateEndpointRequest request = new CreateEndpointRequest();
        request.setName("Get Users");
        request.setMethod("GET");
        request.setPath("/users");

        ServiceEndpoint existing = createTestEndpoint(service.getId());

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findByServiceIdAndMethodAndPath(service.getId(), "GET", "/users"))
                .thenReturn(Optional.of(existing));

        // When/Then
        assertThatThrownBy(() -> externalServiceService.createEndpoint(service.getId(), request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteEndpoint_matchingService_deletes() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        ServiceEndpoint endpoint = createTestEndpoint(service.getId());

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));

        // When
        externalServiceService.deleteEndpoint(service.getId(), endpoint.getId(), userId);

        // Then
        verify(endpointRepository).delete(endpoint);
    }

    @Test
    void deleteEndpoint_mismatchedService_throwsException() {
        // Given
        ExternalService service = createTestService();
        UUID userId = service.getCreatedBy();
        UUID otherServiceId = UUID.randomUUID();
        ServiceEndpoint endpoint = createTestEndpoint(otherServiceId);

        when(serviceRepository.findByIdAndIsDeletedFalse(service.getId())).thenReturn(Optional.of(service));
        when(endpointRepository.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));

        // When/Then
        assertThatThrownBy(() -> externalServiceService.deleteEndpoint(service.getId(), endpoint.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    // ========== Helper Methods ==========

    private ExternalService createTestService() {
        return ExternalService.builder()
                .id(UUID.randomUUID())
                .name("test-api")
                .displayName("Test API")
                .description("A test API service")
                .baseUrl("https://api.example.com")
                .protocol("REST")
                .status("active")
                .createdBy(UUID.randomUUID())
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
    }

    private ServiceEndpoint createTestEndpoint(UUID serviceId) {
        return ServiceEndpoint.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .name("Get Items")
                .method("GET")
                .path("/items")
                .isEnabled(true)
                .build();
    }

    private CreateServiceRequest createServiceRequest(String name) {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName(name);
        request.setDisplayName("Test API");
        request.setDescription("Test service");
        request.setBaseUrl("https://api.example.com");
        request.setProtocol("REST");
        return request;
    }
}
