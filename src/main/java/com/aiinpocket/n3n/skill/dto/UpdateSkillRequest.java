package com.aiinpocket.n3n.skill.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateSkillRequest {
    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;
    @Size(max = 100, message = "Icon must be at most 100 characters")
    private String icon;
    private Boolean isEnabled;
    @Size(max = 50, message = "Implementation config must have at most 50 fields")
    private Map<String, Object> implementationConfig;
    @Size(max = 100, message = "Input schema must have at most 100 fields")
    private Map<String, Object> inputSchema;
    @Size(max = 100, message = "Output schema must have at most 100 fields")
    private Map<String, Object> outputSchema;
    @Size(max = 20, message = "Visibility must be at most 20 characters")
    private String visibility;
}
