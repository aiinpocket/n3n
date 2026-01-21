package com.aiinpocket.n3n.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateTemplateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private String category;

    private List<String> tags;

    @NotNull(message = "Definition is required")
    private Map<String, Object> definition;

    private String thumbnailUrl;
}
