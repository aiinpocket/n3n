package com.aiinpocket.n3n.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateSkillRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "Name must start with lowercase letter and contain only lowercase letters, numbers, and underscores")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 100, message = "Icon must be at most 100 characters")
    private String icon;

    @NotBlank(message = "Implementation type is required")
    @Size(max = 50, message = "Implementation type must be at most 50 characters")
    private String implementationType;

    @Size(max = 50, message = "Implementation config must have at most 50 fields")
    private Map<String, Object> implementationConfig;

    @NotNull(message = "Input schema is required")
    @Size(max = 100, message = "Input schema must have at most 100 fields")
    private Map<String, Object> inputSchema;

    @Size(max = 100, message = "Output schema must have at most 100 fields")
    private Map<String, Object> outputSchema;

    @Size(max = 20, message = "Visibility must be at most 20 characters")
    private String visibility;
}
