package com.aiinpocket.n3n.service.dto;

import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class EndpointResponse {

    private UUID id;
    private UUID serviceId;
    private String name;
    private String description;
    private String method;
    private String path;
    private Map<String, Object> pathParams;
    private Map<String, Object> queryParams;
    private Map<String, Object> requestBody;
    private Map<String, Object> responseSchema;
    private List<String> tags;
    private Boolean isEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    public static EndpointResponse from(ServiceEndpoint endpoint) {
        return EndpointResponse.builder()
                .id(endpoint.getId())
                .serviceId(endpoint.getServiceId())
                .name(endpoint.getName())
                .description(endpoint.getDescription())
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .pathParams(endpoint.getPathParams())
                .queryParams(endpoint.getQueryParams())
                .requestBody(endpoint.getRequestBody())
                .responseSchema(endpoint.getResponseSchema())
                .tags(endpoint.getTags())
                .isEnabled(endpoint.getIsEnabled())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();
    }
}
