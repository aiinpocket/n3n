package com.aiinpocket.n3n.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * DTO for node handler information in API responses.
 */
@Data
@Builder
public class NodeHandlerInfo {

    private String type;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private boolean isTrigger;
    private boolean supportsAsync;
    private Map<String, Object> configSchema;
    private Map<String, Object> interfaceDefinition;
}
