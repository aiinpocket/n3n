package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateEndpointRequest {

    @NotBlank(message = "Endpoint name is required")
    private String name;

    private String description;

    @NotBlank(message = "HTTP method is required")
    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$", message = "Invalid HTTP method")
    private String method;

    @NotBlank(message = "Path is required")
    private String path;

    private Map<String, Object> pathParams;

    private Map<String, Object> queryParams;

    private Map<String, Object> requestBody;

    private Map<String, Object> responseSchema;

    private List<String> tags;
}
