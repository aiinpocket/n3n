package com.aiinpocket.n3n.component.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateComponentRequest {
    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;
    @Size(max = 100, message = "Icon must be at most 100 characters")
    private String icon;
}
