package com.aiinpocket.n3n.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class CreateSkillRequest {

    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Name must start with lowercase letter and contain only lowercase letters, numbers, and underscores")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    private String icon;

    @NotBlank(message = "Implementation type is required")
    private String implementationType;

    private Map<String, Object> implementationConfig;

    @NotNull(message = "Input schema is required")
    private Map<String, Object> inputSchema;

    private Map<String, Object> outputSchema;

    private String visibility;
}
