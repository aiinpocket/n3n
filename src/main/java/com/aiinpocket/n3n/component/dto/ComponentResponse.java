package com.aiinpocket.n3n.component.dto;

import com.aiinpocket.n3n.component.entity.Component;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ComponentResponse {
    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String latestVersion;
    private String activeVersion;

    public static ComponentResponse from(Component c) {
        return ComponentResponse.builder()
            .id(c.getId())
            .name(c.getName())
            .displayName(c.getDisplayName())
            .description(c.getDescription())
            .category(c.getCategory())
            .icon(c.getIcon())
            .createdBy(c.getCreatedBy())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }

    public static ComponentResponse from(Component c, String latestVersion, String activeVersion) {
        ComponentResponse resp = from(c);
        resp.setLatestVersion(latestVersion);
        resp.setActiveVersion(activeVersion);
        return resp;
    }
}
