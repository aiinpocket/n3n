package com.aiinpocket.n3n.template.dto;

import com.aiinpocket.n3n.template.entity.FlowTemplate;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TemplateResponse {

    private UUID id;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private Map<String, Object> definition;
    private String thumbnailUrl;
    private boolean isOfficial;
    private int usageCount;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static TemplateResponse from(FlowTemplate template) {
        return TemplateResponse.builder()
            .id(template.getId())
            .name(template.getName())
            .description(template.getDescription())
            .category(template.getCategory())
            .tags(template.getTags())
            .definition(template.getDefinition())
            .thumbnailUrl(template.getThumbnailUrl())
            .isOfficial(Boolean.TRUE.equals(template.getIsOfficial()))
            .usageCount(template.getUsageCount() != null ? template.getUsageCount() : 0)
            .createdBy(template.getCreatedBy())
            .createdAt(template.getCreatedAt())
            .updatedAt(template.getUpdatedAt())
            .build();
    }

    public static TemplateResponse summary(FlowTemplate template) {
        return TemplateResponse.builder()
            .id(template.getId())
            .name(template.getName())
            .description(template.getDescription())
            .category(template.getCategory())
            .tags(template.getTags())
            .thumbnailUrl(template.getThumbnailUrl())
            .isOfficial(Boolean.TRUE.equals(template.getIsOfficial()))
            .usageCount(template.getUsageCount() != null ? template.getUsageCount() : 0)
            .createdAt(template.getCreatedAt())
            .build();
    }
}
