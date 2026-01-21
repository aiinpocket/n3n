package com.aiinpocket.n3n.execution.handler;

import java.util.Map;

/**
 * Interface for all node handlers in the n3n workflow execution engine.
 * Each node type (code, http_request, condition, etc.) implements this interface.
 */
public interface NodeHandler {

    /**
     * Get the unique type identifier for this handler.
     * This should match the node type in flow definitions.
     * @return type identifier (e.g., "code", "http_request", "condition")
     */
    String getType();

    /**
     * Get the display name for this node type.
     * @return human-readable display name
     */
    String getDisplayName();

    /**
     * Get the description of what this node does.
     * @return description text
     */
    String getDescription();

    /**
     * Get the category this node belongs to.
     * @return category (e.g., "triggers", "actions", "logic", "integrations")
     */
    String getCategory();

    /**
     * Get the icon identifier for this node type.
     * @return icon name or URL
     */
    default String getIcon() {
        return "default";
    }

    /**
     * Execute this node with the given context.
     * @param context execution context containing node config, inputs, and credentials
     * @return execution result with output data or error information
     */
    NodeExecutionResult execute(NodeExecutionContext context);

    /**
     * Get the JSON Schema for this node's configuration.
     * Used by the frontend to render the configuration form.
     * @return JSON Schema as a Map
     */
    Map<String, Object> getConfigSchema();

    /**
     * Get the interface definition (inputs/outputs) for this node.
     * @return interface definition with inputs and outputs arrays
     */
    Map<String, Object> getInterfaceDefinition();

    /**
     * Whether this node supports async execution.
     * @return true if node can be executed asynchronously
     */
    default boolean supportsAsync() {
        return false;
    }

    /**
     * Whether this node is a trigger node (entry point for flows).
     * @return true if this is a trigger node
     */
    default boolean isTrigger() {
        return false;
    }

    /**
     * Validate the node configuration before execution.
     * @param config the node configuration
     * @return validation result with any errors
     */
    default ValidationResult validateConfig(Map<String, Object> config) {
        return ValidationResult.valid();
    }
}
