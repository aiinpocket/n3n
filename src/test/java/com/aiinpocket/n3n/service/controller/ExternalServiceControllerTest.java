package com.aiinpocket.n3n.service.controller;

import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.service.ExternalServiceService;
import com.aiinpocket.n3n.service.dto.*;
import jakarta.servlet.http.HttpServletRequest;
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
class ExternalServiceControllerTest {

    @Mock
    private ExternalServiceService serviceService;

    @Mock
    private IpRateLimiter ipRateLimiter;

    @InjectMocks
    private ExternalServiceController controller;

    // ===== Helper Methods =====

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private ServiceResponse sampleServiceResponse() {
        return ServiceResponse.builder()
                .id(UUID.randomUUID())
                .name("my-api-service")
                .displayName("My API Service")
                .description("A test external service")
                .baseUrl("https://api.example.com")
                .protocol("REST")
                .authType("bearer")
                .status("active")
                .endpointCount(5)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ServiceDetailResponse sampleServiceDetailResponse() {
        return ServiceDetailResponse.builder()
                .id(UUID.randomUUID())
                .name("my-api-service")
                .displayName("My API Service")
                .description("A test external service")
                .baseUrl("https://api.example.com")
                .protocol("REST")
                .authType("bearer")
                .status("active")
                .endpoints(List.of(sampleEndpointResponse()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private EndpointResponse sampleEndpointResponse() {
        return EndpointResponse.builder()
                .id(UUID.randomUUID())
                .serviceId(UUID.randomUUID())
                .name("Get Users")
                .description("Retrieve all users")
                .method("GET")
                .path("/users")
                .tags(List.of("users"))
                .isEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CreateServiceRequest sampleCreateServiceRequest() {
        var request = new CreateServiceRequest();
        request.setName("new-service");
        request.setDisplayName("New Service");
        request.setDescription("A new external service");
        request.setBaseUrl("https://api.newservice.com");
        request.setProtocol("REST");
        request.setAuthType("api_key");
        return request;
    }

    private UpdateServiceRequest sampleUpdateServiceRequest() {
        var request = new UpdateServiceRequest();
        request.setDisplayName("Updated Service Name");
        request.setDescription("Updated description");
        request.setStatus("active");
        return request;
    }

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }

    private CreateEndpointRequest sampleCreateEndpointRequest() {
        var request = new CreateEndpointRequest();
        request.setName("Create User");
        request.setDescription("Create a new user");
        request.setMethod("POST");
        request.setPath("/users");
        request.setTags(List.of("users"));
        return request;
    }

    // ===== listServices (GET /api/services) =====

    @Test
    void listServices_returnsPageOfServices() {
        var user = testUser();
        var service = sampleServiceResponse();
        Page<ServiceResponse> page = new PageImpl<>(List.of(service));
        when(serviceService.listServices(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = controller.listServices(Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("my-api-service");
        verify(serviceService).listServices(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listServices_emptyPage_returnsOk() {
        var user = testUser();
        when(serviceService.listServices(any(UUID.class), any(Pageable.class))).thenReturn(Page.empty());

        var result = controller.listServices(Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listServices_multipleServices_returnsAll() {
        var user = testUser();
        var svc1 = ServiceResponse.builder().id(UUID.randomUUID()).name("svc-1").displayName("Service 1").status("active").endpointCount(2).build();
        var svc2 = ServiceResponse.builder().id(UUID.randomUUID()).name("svc-2").displayName("Service 2").status("active").endpointCount(5).build();
        var svc3 = ServiceResponse.builder().id(UUID.randomUUID()).name("svc-3").displayName("Service 3").status("inactive").endpointCount(0).build();
        Page<ServiceResponse> page = new PageImpl<>(List.of(svc1, svc2, svc3));
        when(serviceService.listServices(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = controller.listServices(Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(ServiceResponse::getName)
                .containsExactly("svc-1", "svc-2", "svc-3");
    }

    @Test
    void listServices_withPagination_passesPageable() {
        var user = testUser();
        Pageable pageable = PageRequest.of(1, 10);
        when(serviceService.listServices(any(UUID.class), eq(pageable))).thenReturn(Page.empty());

        controller.listServices(pageable, user);

        verify(serviceService).listServices(any(UUID.class), eq(pageable));
    }

    @Test
    void listServices_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(serviceService.listServices(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

        controller.listServices(Pageable.unpaged(), user);

        verify(serviceService).listServices(eq(userId), any(Pageable.class));
    }

    // ===== getService (GET /api/services/{id}) =====

    @Test
    void getService_found_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var detail = sampleServiceDetailResponse();
        when(serviceService.getService(eq(serviceId), any(UUID.class))).thenReturn(detail);

        var result = controller.getService(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("my-api-service");
        assertThat(result.getBody().getEndpoints()).hasSize(1);
    }

    @Test
    void getService_notFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.getService(eq(serviceId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.getService(serviceId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void getService_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.getService(eq(serviceId), any(UUID.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> controller.getService(serviceId, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getService_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var detail = sampleServiceDetailResponse();
        when(serviceService.getService(any(UUID.class), eq(userId))).thenReturn(detail);

        controller.getService(UUID.randomUUID(), user);

        verify(serviceService).getService(any(UUID.class), eq(userId));
    }

    // ===== createService (POST /api/services) =====

    @Test
    void createService_success_returnsCreated() {
        var user = testUser();
        var request = sampleCreateServiceRequest();
        var response = sampleServiceResponse();
        when(serviceService.createService(any(CreateServiceRequest.class), any(UUID.class))).thenReturn(response);

        var result = controller.createService(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("my-api-service");
        assertThat(result.getBody().getStatus()).isEqualTo("active");
        verify(serviceService).createService(any(CreateServiceRequest.class), any(UUID.class));
    }

    @Test
    void createService_duplicateName_throwsException() {
        var user = testUser();
        var request = sampleCreateServiceRequest();
        when(serviceService.createService(any(CreateServiceRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Service with name 'new-service' already exists"));

        assertThatThrownBy(() -> controller.createService(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createService_internalUrl_throwsException() {
        var user = testUser();
        var request = sampleCreateServiceRequest();
        request.setBaseUrl("http://localhost:8080");
        when(serviceService.createService(any(CreateServiceRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Access to internal network addresses is not allowed: localhost"));

        assertThatThrownBy(() -> controller.createService(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal network");
    }

    @Test
    void createService_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = sampleCreateServiceRequest();
        var response = sampleServiceResponse();
        when(serviceService.createService(any(CreateServiceRequest.class), eq(userId))).thenReturn(response);

        controller.createService(request, user);

        verify(serviceService).createService(any(CreateServiceRequest.class), eq(userId));
    }

    @Test
    void createService_withSchemaUrl_returnsCreated() {
        var user = testUser();
        var request = sampleCreateServiceRequest();
        request.setSchemaUrl("https://api.example.com/openapi.json");
        var response = ServiceResponse.builder()
                .id(UUID.randomUUID())
                .name("new-service")
                .displayName("New Service")
                .status("active")
                .endpointCount(10)
                .build();
        when(serviceService.createService(any(CreateServiceRequest.class), any(UUID.class))).thenReturn(response);

        var result = controller.createService(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getEndpointCount()).isEqualTo(10);
    }

    @Test
    void createService_withEndpoints_returnsCreated() {
        var user = testUser();
        var request = sampleCreateServiceRequest();
        var endpointReq = sampleCreateEndpointRequest();
        request.setEndpoints(List.of(endpointReq));
        var response = ServiceResponse.builder()
                .id(UUID.randomUUID())
                .name("new-service")
                .displayName("New Service")
                .status("active")
                .endpointCount(1)
                .build();
        when(serviceService.createService(any(CreateServiceRequest.class), any(UUID.class))).thenReturn(response);

        var result = controller.createService(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getEndpointCount()).isEqualTo(1);
    }

    // ===== updateService (PUT /api/services/{id}) =====

    @Test
    void updateService_success_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleUpdateServiceRequest();
        var response = ServiceResponse.builder()
                .id(serviceId)
                .name("my-api-service")
                .displayName("Updated Service Name")
                .description("Updated description")
                .status("active")
                .endpointCount(3)
                .build();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateService(serviceId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDisplayName()).isEqualTo("Updated Service Name");
        assertThat(result.getBody().getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateService_notFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleUpdateServiceRequest();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.updateService(serviceId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void updateService_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleUpdateServiceRequest();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), any(UUID.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> controller.updateService(serviceId, request, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void updateService_partialUpdate_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = new UpdateServiceRequest();
        request.setDescription("Only description updated");
        var response = ServiceResponse.builder()
                .id(serviceId)
                .name("my-api-service")
                .displayName("Original Name")
                .description("Only description updated")
                .status("active")
                .endpointCount(2)
                .build();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateService(serviceId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getDescription()).isEqualTo("Only description updated");
        assertThat(result.getBody().getDisplayName()).isEqualTo("Original Name");
    }

    @Test
    void updateService_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var request = sampleUpdateServiceRequest();
        var response = sampleServiceResponse();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), eq(userId)))
                .thenReturn(response);

        controller.updateService(serviceId, request, user);

        verify(serviceService).updateService(eq(serviceId), any(UpdateServiceRequest.class), eq(userId));
    }

    // ===== deleteService (DELETE /api/services/{id}) =====

    @Test
    void deleteService_success_returnsNoContent() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        doNothing().when(serviceService).deleteService(eq(serviceId), any(UUID.class));

        var result = controller.deleteService(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(serviceService).deleteService(eq(serviceId), any(UUID.class));
    }

    @Test
    void deleteService_notFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Service not found: " + serviceId))
                .when(serviceService).deleteService(eq(serviceId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteService(serviceId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void deleteService_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("Access denied"))
                .when(serviceService).deleteService(eq(serviceId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteService(serviceId, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void deleteService_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        doNothing().when(serviceService).deleteService(eq(serviceId), eq(userId));

        controller.deleteService(serviceId, user);

        verify(serviceService).deleteService(eq(serviceId), eq(userId));
    }

    // ===== refreshSchema (POST /api/services/{id}/refresh-schema) =====

    @Test
    void refreshSchema_success_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        Map<String, Object> schemaResult = Map.of(
                "message", "Schema refreshed successfully",
                "addedEndpoints", 3,
                "updatedEndpoints", 2,
                "totalEndpoints", 5
        );
        when(serviceService.refreshSchema(eq(serviceId), any(UUID.class))).thenReturn(schemaResult);

        var result = controller.refreshSchema(serviceId, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("message", "Schema refreshed successfully");
        assertThat(result.getBody()).containsEntry("addedEndpoints", 3);
        assertThat(result.getBody()).containsEntry("updatedEndpoints", 2);
        assertThat(result.getBody()).containsEntry("totalEndpoints", 5);
    }

    @Test
    void refreshSchema_noSchemaUrl_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.refreshSchema(eq(serviceId), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Service does not have a schema URL configured"));

        assertThatThrownBy(() -> controller.refreshSchema(serviceId, user, mockHttpRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema URL");
    }

    @Test
    void refreshSchema_notFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.refreshSchema(eq(serviceId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.refreshSchema(serviceId, user, mockHttpRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void refreshSchema_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.refreshSchema(eq(serviceId), any(UUID.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> controller.refreshSchema(serviceId, user, mockHttpRequest()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void refreshSchema_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        when(serviceService.refreshSchema(eq(serviceId), eq(userId)))
                .thenReturn(Map.of("message", "ok", "addedEndpoints", 0, "updatedEndpoints", 0, "totalEndpoints", 0));

        controller.refreshSchema(serviceId, user, mockHttpRequest());

        verify(serviceService).refreshSchema(eq(serviceId), eq(userId));
    }

    // ===== getEndpoints (GET /api/services/{id}/endpoints) =====

    @Test
    void getEndpoints_success_returnsList() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpoint1 = EndpointResponse.builder()
                .id(UUID.randomUUID()).serviceId(serviceId).name("Get Users").method("GET").path("/users").build();
        var endpoint2 = EndpointResponse.builder()
                .id(UUID.randomUUID()).serviceId(serviceId).name("Create User").method("POST").path("/users").build();
        when(serviceService.getEndpoints(eq(serviceId), any(UUID.class))).thenReturn(List.of(endpoint1, endpoint2));

        var result = controller.getEndpoints(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getName()).isEqualTo("Get Users");
        assertThat(result.getBody().get(1).getName()).isEqualTo("Create User");
    }

    @Test
    void getEndpoints_emptyList_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.getEndpoints(eq(serviceId), any(UUID.class))).thenReturn(List.of());

        var result = controller.getEndpoints(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getEndpoints_notFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.getEndpoints(eq(serviceId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.getEndpoints(serviceId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void getEndpoints_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        when(serviceService.getEndpoints(eq(serviceId), eq(userId))).thenReturn(List.of());

        controller.getEndpoints(serviceId, user);

        verify(serviceService).getEndpoints(eq(serviceId), eq(userId));
    }

    // ===== createEndpoint (POST /api/services/{id}/endpoints) =====

    @Test
    void createEndpoint_success_returnsCreated() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        var response = EndpointResponse.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .name("Create User")
                .description("Create a new user")
                .method("POST")
                .path("/users")
                .tags(List.of("users"))
                .isEnabled(true)
                .build();
        when(serviceService.createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.createEndpoint(serviceId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Create User");
        assertThat(result.getBody().getMethod()).isEqualTo("POST");
        assertThat(result.getBody().getPath()).isEqualTo("/users");
    }

    @Test
    void createEndpoint_duplicateMethodPath_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        when(serviceService.createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Endpoint with method POST and path /users already exists"));

        assertThatThrownBy(() -> controller.createEndpoint(serviceId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createEndpoint_serviceNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        when(serviceService.createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.createEndpoint(serviceId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void createEndpoint_withSchemas_returnsCreated() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        request.setPathParams(Map.of("properties", Map.of("userId", Map.of("type", "string"))));
        request.setQueryParams(Map.of("properties", Map.of("page", Map.of("type", "integer"))));
        request.setRequestBody(Map.of("properties", Map.of("name", Map.of("type", "string"))));
        request.setResponseSchema(Map.of("properties", Map.of("id", Map.of("type", "string"))));

        var response = EndpointResponse.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .name("Create User")
                .method("POST")
                .path("/users")
                .pathParams(request.getPathParams())
                .queryParams(request.getQueryParams())
                .requestBody(request.getRequestBody())
                .responseSchema(request.getResponseSchema())
                .build();
        when(serviceService.createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.createEndpoint(serviceId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getPathParams()).isNotNull();
        assertThat(result.getBody().getQueryParams()).isNotNull();
        assertThat(result.getBody().getRequestBody()).isNotNull();
        assertThat(result.getBody().getResponseSchema()).isNotNull();
    }

    @Test
    void createEndpoint_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        var response = sampleEndpointResponse();
        when(serviceService.createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), eq(userId)))
                .thenReturn(response);

        controller.createEndpoint(serviceId, request, user);

        verify(serviceService).createEndpoint(eq(serviceId), any(CreateEndpointRequest.class), eq(userId));
    }

    // ===== updateEndpoint (PUT /api/services/{serviceId}/endpoints/{endpointId}) =====

    @Test
    void updateEndpoint_success_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        request.setName("Updated Endpoint");
        request.setMethod("PUT");
        request.setPath("/users/{id}");

        var response = EndpointResponse.builder()
                .id(endpointId)
                .serviceId(serviceId)
                .name("Updated Endpoint")
                .method("PUT")
                .path("/users/{id}")
                .isEnabled(true)
                .build();
        when(serviceService.updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = controller.updateEndpoint(serviceId, endpointId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Updated Endpoint");
        assertThat(result.getBody().getMethod()).isEqualTo("PUT");
        assertThat(result.getBody().getPath()).isEqualTo("/users/{id}");
    }

    @Test
    void updateEndpoint_endpointNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        when(serviceService.updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Endpoint not found: " + endpointId));

        assertThatThrownBy(() -> controller.updateEndpoint(serviceId, endpointId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Endpoint not found");
    }

    @Test
    void updateEndpoint_endpointBelongsToDifferentService_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        when(serviceService.updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Endpoint does not belong to the specified service"));

        assertThatThrownBy(() -> controller.updateEndpoint(serviceId, endpointId, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void updateEndpoint_serviceNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        when(serviceService.updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.updateEndpoint(serviceId, endpointId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void updateEndpoint_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var request = sampleCreateEndpointRequest();
        var response = sampleEndpointResponse();
        when(serviceService.updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), eq(userId)))
                .thenReturn(response);

        controller.updateEndpoint(serviceId, endpointId, request, user);

        verify(serviceService).updateEndpoint(eq(serviceId), eq(endpointId), any(CreateEndpointRequest.class), eq(userId));
    }

    // ===== deleteEndpoint (DELETE /api/services/{serviceId}/endpoints/{endpointId}) =====

    @Test
    void deleteEndpoint_success_returnsNoContent() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        doNothing().when(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), any(UUID.class));

        var result = controller.deleteEndpoint(serviceId, endpointId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), any(UUID.class));
    }

    @Test
    void deleteEndpoint_endpointNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Endpoint not found: " + endpointId))
                .when(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteEndpoint(serviceId, endpointId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Endpoint not found");
    }

    @Test
    void deleteEndpoint_endpointBelongsToDifferentService_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Endpoint does not belong to the specified service"))
                .when(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteEndpoint(serviceId, endpointId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void deleteEndpoint_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("Access denied"))
                .when(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), any(UUID.class));

        assertThatThrownBy(() -> controller.deleteEndpoint(serviceId, endpointId, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void deleteEndpoint_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        doNothing().when(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), eq(userId));

        controller.deleteEndpoint(serviceId, endpointId, user);

        verify(serviceService).deleteEndpoint(eq(serviceId), eq(endpointId), eq(userId));
    }

    // ===== testConnection (POST /api/services/{id}/test) =====

    @Test
    void testConnection_success_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        Map<String, Object> testResult = Map.of(
                "success", true,
                "latencyMs", 42L,
                "message", "Service is healthy"
        );
        when(serviceService.testConnection(eq(serviceId), any(UUID.class))).thenReturn(testResult);

        var result = controller.testConnection(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("success", true);
        assertThat(result.getBody()).containsEntry("message", "Service is healthy");
        assertThat(result.getBody()).containsKey("latencyMs");
    }

    @Test
    void testConnection_failure_returnsOkWithFailure() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        Map<String, Object> testResult = Map.of(
                "success", false,
                "latencyMs", 5000L,
                "message", "Connection test failed"
        );
        when(serviceService.testConnection(eq(serviceId), any(UUID.class))).thenReturn(testResult);

        var result = controller.testConnection(serviceId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("success", false);
        assertThat(result.getBody()).containsEntry("message", "Connection test failed");
    }

    @Test
    void testConnection_serviceNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.testConnection(eq(serviceId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.testConnection(serviceId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void testConnection_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        when(serviceService.testConnection(eq(serviceId), any(UUID.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> controller.testConnection(serviceId, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void testConnection_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        when(serviceService.testConnection(eq(serviceId), eq(userId)))
                .thenReturn(Map.of("success", true, "latencyMs", 10L, "message", "ok"));

        controller.testConnection(serviceId, user);

        verify(serviceService).testConnection(eq(serviceId), eq(userId));
    }

    // ===== getEndpointSchema (GET /api/services/{serviceId}/endpoints/{endpointId}/schema) =====

    @Test
    void getEndpointSchema_success_returnsOk() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var schemaResponse = EndpointSchemaResponse.builder()
                .serviceId(serviceId)
                .endpointId(endpointId)
                .displayName("My API - Get Users")
                .description("Retrieve all users")
                .method("GET")
                .path("/users")
                .configSchema(Map.of("type", "object", "properties", Map.of()))
                .interfaceDefinition(Map.of("inputs", List.of(), "outputs", List.of()))
                .build();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), any(UUID.class)))
                .thenReturn(schemaResponse);

        var result = controller.getEndpointSchema(serviceId, endpointId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getServiceId()).isEqualTo(serviceId);
        assertThat(result.getBody().getEndpointId()).isEqualTo(endpointId);
        assertThat(result.getBody().getDisplayName()).isEqualTo("My API - Get Users");
        assertThat(result.getBody().getMethod()).isEqualTo("GET");
        assertThat(result.getBody().getPath()).isEqualTo("/users");
        assertThat(result.getBody().getConfigSchema()).isNotNull();
        assertThat(result.getBody().getInterfaceDefinition()).isNotNull();
    }

    @Test
    void getEndpointSchema_endpointNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Endpoint not found: " + endpointId));

        assertThatThrownBy(() -> controller.getEndpointSchema(serviceId, endpointId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Endpoint not found");
    }

    @Test
    void getEndpointSchema_endpointBelongsToDifferentService_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Endpoint does not belong to the specified service"));

        assertThatThrownBy(() -> controller.getEndpointSchema(serviceId, endpointId, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void getEndpointSchema_serviceNotFound_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        assertThatThrownBy(() -> controller.getEndpointSchema(serviceId, endpointId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void getEndpointSchema_accessDenied_throwsException() {
        var user = testUser();
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), any(UUID.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> controller.getEndpointSchema(serviceId, endpointId, user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getEndpointSchema_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();
        var schemaResponse = EndpointSchemaResponse.builder()
                .serviceId(serviceId).endpointId(endpointId).displayName("Test").method("GET").path("/test").build();
        when(serviceService.getEndpointSchema(eq(serviceId), eq(endpointId), eq(userId)))
                .thenReturn(schemaResponse);

        controller.getEndpointSchema(serviceId, endpointId, user);

        verify(serviceService).getEndpointSchema(eq(serviceId), eq(endpointId), eq(userId));
    }

    // ===== Cross-cutting: All endpoints extract userId correctly =====

    @Test
    void allEndpoints_extractUserIdCorrectly() {
        var userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var serviceId = UUID.randomUUID();
        var endpointId = UUID.randomUUID();

        // listServices
        when(serviceService.listServices(eq(userId), any(Pageable.class))).thenReturn(Page.empty());
        controller.listServices(Pageable.unpaged(), user);
        verify(serviceService).listServices(eq(userId), any(Pageable.class));

        // getService
        var detail = sampleServiceDetailResponse();
        when(serviceService.getService(eq(serviceId), eq(userId))).thenReturn(detail);
        controller.getService(serviceId, user);
        verify(serviceService).getService(eq(serviceId), eq(userId));

        // createService
        var createReq = sampleCreateServiceRequest();
        var svcResp = sampleServiceResponse();
        when(serviceService.createService(any(CreateServiceRequest.class), eq(userId))).thenReturn(svcResp);
        controller.createService(createReq, user);
        verify(serviceService).createService(any(CreateServiceRequest.class), eq(userId));

        // updateService
        var updateReq = sampleUpdateServiceRequest();
        when(serviceService.updateService(eq(serviceId), any(UpdateServiceRequest.class), eq(userId))).thenReturn(svcResp);
        controller.updateService(serviceId, updateReq, user);
        verify(serviceService).updateService(eq(serviceId), any(UpdateServiceRequest.class), eq(userId));

        // deleteService
        doNothing().when(serviceService).deleteService(eq(serviceId), eq(userId));
        controller.deleteService(serviceId, user);
        verify(serviceService).deleteService(eq(serviceId), eq(userId));

        // testConnection
        when(serviceService.testConnection(eq(serviceId), eq(userId)))
                .thenReturn(Map.of("success", true, "latencyMs", 1L, "message", "ok"));
        controller.testConnection(serviceId, user);
        verify(serviceService).testConnection(eq(serviceId), eq(userId));
    }

    // ===== Response status code verification =====

    @Test
    void createService_returnsHttpStatus201() {
        var user = testUser();
        var response = sampleServiceResponse();
        when(serviceService.createService(any(), any())).thenReturn(response);

        var result = controller.createService(sampleCreateServiceRequest(), user);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void createEndpoint_returnsHttpStatus201() {
        var user = testUser();
        var response = sampleEndpointResponse();
        when(serviceService.createEndpoint(any(), any(), any())).thenReturn(response);

        var result = controller.createEndpoint(UUID.randomUUID(), sampleCreateEndpointRequest(), user);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void deleteService_returnsHttpStatus204() {
        var user = testUser();
        doNothing().when(serviceService).deleteService(any(), any());

        var result = controller.deleteService(UUID.randomUUID(), user);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteEndpoint_returnsHttpStatus204() {
        var user = testUser();
        doNothing().when(serviceService).deleteEndpoint(any(), any(), any());

        var result = controller.deleteEndpoint(UUID.randomUUID(), UUID.randomUUID(), user);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }
}
