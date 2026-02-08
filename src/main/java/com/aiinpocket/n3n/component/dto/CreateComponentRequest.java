package com.aiinpocket.n3n.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateComponentRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 100, message = "Icon must be at most 100 characters")
    private String icon;
}
