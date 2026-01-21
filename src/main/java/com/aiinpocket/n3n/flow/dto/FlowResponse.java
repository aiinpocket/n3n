package com.aiinpocket.n3n.flow.dto;

import com.aiinpocket.n3n.flow.entity.Flow;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FlowResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String latestVersion;
    private String publishedVersion;

    public static FlowResponse from(Flow flow) {
        return FlowResponse.builder()
            .id(flow.getId())
            .name(flow.getName())
            .description(flow.getDescription())
            .createdBy(flow.getCreatedBy())
            .createdAt(flow.getCreatedAt())
            .updatedAt(flow.getUpdatedAt())
            .build();
    }

    public static FlowResponse from(Flow flow, String latestVersion, String publishedVersion) {
        return FlowResponse.builder()
            .id(flow.getId())
            .name(flow.getName())
            .description(flow.getDescription())
            .createdBy(flow.getCreatedBy())
            .createdAt(flow.getCreatedAt())
            .updatedAt(flow.getUpdatedAt())
            .latestVersion(latestVersion)
            .publishedVersion(publishedVersion)
            .build();
    }
}
