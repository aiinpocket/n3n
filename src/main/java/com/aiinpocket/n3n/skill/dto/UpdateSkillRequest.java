package com.aiinpocket.n3n.skill.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateSkillRequest {
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private Boolean isEnabled;
    private Map<String, Object> implementationConfig;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String visibility;
}
