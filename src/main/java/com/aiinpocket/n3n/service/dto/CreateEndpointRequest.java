package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateEndpointRequest {

    @NotBlank(message = "Endpoint name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotBlank(message = "HTTP method is required")
    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$", message = "Invalid HTTP method")
    private String method;

    @NotBlank(message = "Path is required")
    @Size(max = 500, message = "Path must be at most 500 characters")
    private String path;

    private Map<String, Object> pathParams;

    private Map<String, Object> queryParams;

    private Map<String, Object> requestBody;

    private Map<String, Object> responseSchema;

    private List<String> tags;
}
