package com.aiinpocket.n3n.service.dto;

import com.aiinpocket.n3n.service.entity.ExternalService;
import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ServiceDetailResponse {

    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String baseUrl;
    private String protocol;
    private String schemaUrl;
    private String authType;
    private Map<String, Object> authConfig;
    private Map<String, Object> healthCheck;
    private String status;
    private List<EndpointResponse> endpoints;
    private Instant createdAt;
    private Instant updatedAt;

    public static ServiceDetailResponse from(ExternalService service, List<ServiceEndpoint> endpoints) {
        return ServiceDetailResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .displayName(service.getDisplayName())
                .description(service.getDescription())
                .baseUrl(service.getBaseUrl())
                .protocol(service.getProtocol())
                .schemaUrl(service.getSchemaUrl())
                .authType(service.getAuthType())
                .authConfig(service.getAuthConfig())
                .healthCheck(service.getHealthCheck())
                .status(service.getStatus())
                .endpoints(endpoints.stream().map(EndpointResponse::from).toList())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
