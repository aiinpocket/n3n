package com.aiinpocket.n3n.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateComponentRequest {
    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String description;

    private String category;

    private String icon;
}
