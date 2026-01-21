package com.aiinpocket.n3n.service.dto;

import com.aiinpocket.n3n.service.entity.ExternalService;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ServiceResponse {

    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String baseUrl;
    private String protocol;
    private String schemaUrl;
    private String authType;
    private Map<String, Object> authConfig;
    private String status;
    private int endpointCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static ServiceResponse from(ExternalService service, int endpointCount) {
        return ServiceResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .displayName(service.getDisplayName())
                .description(service.getDescription())
                .baseUrl(service.getBaseUrl())
                .protocol(service.getProtocol())
                .schemaUrl(service.getSchemaUrl())
                .authType(service.getAuthType())
                .authConfig(service.getAuthConfig())
                .status(service.getStatus())
                .endpointCount(endpointCount)
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    public static ServiceResponse from(ExternalService service) {
        return from(service, 0);
    }
}
