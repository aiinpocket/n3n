package com.aiinpocket.n3n.skill.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SkillDto {
    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private Boolean isBuiltin;
    private Boolean isEnabled;
    private String implementationType;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String visibility;
    private Instant createdAt;
    private Instant updatedAt;
}
