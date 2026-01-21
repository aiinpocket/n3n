package com.aiinpocket.n3n.component.dto;

import com.aiinpocket.n3n.component.entity.ComponentVersion;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ComponentVersionResponse {
    private UUID id;
    private UUID componentId;
    private String version;
    private String image;
    private Map<String, Object> interfaceDef;
    private Map<String, Object> configSchema;
    private Map<String, Object> resources;
    private Map<String, Object> healthCheck;
    private String status;
    private Instant createdAt;
    private UUID createdBy;

    public static ComponentVersionResponse from(ComponentVersion v) {
        return ComponentVersionResponse.builder()
            .id(v.getId())
            .componentId(v.getComponentId())
            .version(v.getVersion())
            .image(v.getImage())
            .interfaceDef(v.getInterfaceDef())
            .configSchema(v.getConfigSchema())
            .resources(v.getResources())
            .healthCheck(v.getHealthCheck())
            .status(v.getStatus())
            .createdAt(v.getCreatedAt())
            .createdBy(v.getCreatedBy())
            .build();
    }
}
