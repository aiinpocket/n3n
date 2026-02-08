package com.aiinpocket.n3n.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateTemplateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @Size(max = 20, message = "Tags list must have at most 20 items")
    private List<String> tags;

    @NotNull(message = "Definition is required")
    private Map<String, Object> definition;

    @Size(max = 500, message = "Thumbnail URL must be at most 500 characters")
    private String thumbnailUrl;
}
