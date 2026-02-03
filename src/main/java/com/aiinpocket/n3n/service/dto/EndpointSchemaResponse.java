package com.aiinpocket.n3n.service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for endpoint schema information.
 * Used by the flow editor to display node configuration options.
 */
@Data
@Builder
public class EndpointSchemaResponse {

    private UUID serviceId;
    private UUID endpointId;
    private String displayName;
    private String description;
    private String method;
    private String path;

    /**
     * JSON Schema format configuration schema.
     * Contains properties for pathParams, queryParams, and requestBody.
     */
    private Map<String, Object> configSchema;

    /**
     * Interface definition describing inputs and outputs.
     * Format: { "inputs": [...], "outputs": [...] }
     */
    private Map<String, Object> interfaceDefinition;
}
