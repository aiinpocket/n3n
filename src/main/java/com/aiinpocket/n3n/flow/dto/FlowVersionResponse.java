package com.aiinpocket.n3n.flow.dto;

import com.aiinpocket.n3n.flow.entity.FlowVersion;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class FlowVersionResponse {
    private UUID id;
    private UUID flowId;
    private String version;
    private Map<String, Object> definition;
    private Map<String, Object> settings;
    private String status;
    private Instant createdAt;
    private UUID createdBy;

    public static FlowVersionResponse from(FlowVersion v) {
        return FlowVersionResponse.builder()
            .id(v.getId())
            .flowId(v.getFlowId())
            .version(v.getVersion())
            .definition(v.getDefinition())
            .settings(v.getSettings())
            .status(v.getStatus())
            .createdAt(v.getCreatedAt())
            .createdBy(v.getCreatedBy())
            .build();
    }
}
