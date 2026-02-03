package com.aiinpocket.n3n.flow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for upstream node output information.
 * Used by the flow editor to display available data for input mapping.
 */
@Data
@Builder
public class UpstreamNodeOutput {

    /**
     * The node ID (e.g., "node-1")
     */
    private String nodeId;

    /**
     * The display label of the node
     */
    private String nodeLabel;

    /**
     * The node type (e.g., "httpRequest", "code", "externalService")
     */
    private String nodeType;

    /**
     * JSON Schema of the node's output
     */
    private Map<String, Object> outputSchema;

    /**
     * Flattened list of output fields for easy selection
     */
    private List<OutputField> flattenedFields;

    @Data
    @Builder
    public static class OutputField {
        /**
         * Path to the field (e.g., "data.items[0].name")
         */
        private String path;

        /**
         * Data type (e.g., "string", "number", "object", "array")
         */
        private String type;

        /**
         * Description of the field
         */
        private String description;

        /**
         * Expression to use in input mapping (e.g., "{{ $node[\"http_1\"].json.data.items[0].name }}")
         */
        private String expression;
    }
}
