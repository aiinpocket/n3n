package com.aiinpocket.n3n.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Container Node Definition - DTO for node definitions fetched from plugin containers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerNodeDefinition {

    /**
     * Node type identifier (e.g., "puppeteer", "selenium")
     */
    private String type;

    /**
     * Display name for UI (Traditional Chinese)
     */
    private String displayName;

    /**
     * Description of what this node does
     */
    private String description;

    /**
     * Category for grouping (e.g., "automation", "browser")
     */
    private String category;

    /**
     * Icon name or URL
     */
    private String icon;

    /**
     * Whether this is a trigger node
     */
    private boolean isTrigger;

    /**
     * Whether async execution is supported
     */
    private boolean supportsAsync;

    /**
     * JSON schema for node configuration
     */
    private Map<String, Object> configSchema;

    /**
     * Input port definitions
     */
    private List<PortDefinition> inputs;

    /**
     * Output port definitions
     */
    private List<PortDefinition> outputs;

    /**
     * Keywords for search
     */
    private List<String> keywords;

    /**
     * Version of the node definition
     */
    private String version;

    /**
     * Container metadata
     */
    private ContainerMetadata container;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortDefinition {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContainerMetadata {
        private String containerId;
        private String image;
        private int port;
        private String healthEndpoint;
    }
}
