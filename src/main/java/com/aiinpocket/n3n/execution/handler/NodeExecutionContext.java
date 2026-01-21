package com.aiinpocket.n3n.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Context object passed to NodeHandler.execute() containing all information
 * needed for node execution.
 */
@Data
@Builder
public class NodeExecutionContext {

    /**
     * Unique identifier for this execution instance.
     */
    private UUID executionId;

    /**
     * Unique identifier for this node within the flow.
     */
    private String nodeId;

    /**
     * The type of this node (e.g., "code", "http_request").
     */
    private String nodeType;

    /**
     * Node-specific configuration from the flow definition.
     * This has already been processed for expressions.
     */
    private Map<String, Object> nodeConfig;

    /**
     * Input data from previous nodes, keyed by input port name.
     */
    private Map<String, Object> inputData;

    /**
     * Global execution context including trigger input and environment.
     */
    private Map<String, Object> globalContext;

    /**
     * All outputs from previously executed nodes, keyed by node ID.
     */
    private Map<String, Object> previousOutputs;

    /**
     * The user ID who triggered this execution.
     */
    private UUID userId;

    /**
     * The flow ID being executed.
     */
    private UUID flowId;

    /**
     * The flow version being executed.
     */
    private String flowVersion;

    /**
     * Credential resolver for accessing encrypted credentials.
     */
    private CredentialResolver credentialResolver;

    /**
     * Expression evaluator for resolving dynamic expressions.
     */
    private ExpressionEvaluator expressionEvaluator;

    /**
     * Get a configuration value with a default.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        if (nodeConfig == null) return defaultValue;
        Object value = nodeConfig.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Get a required configuration value, throwing if not present.
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequiredConfig(String key) {
        if (nodeConfig == null || !nodeConfig.containsKey(key)) {
            throw new IllegalArgumentException("Required config '" + key + "' not found");
        }
        return (T) nodeConfig.get(key);
    }

    /**
     * Get input data from a specific port.
     */
    @SuppressWarnings("unchecked")
    public <T> T getInput(String portName, T defaultValue) {
        if (inputData == null) return defaultValue;
        Object value = inputData.get(portName);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Get output from a previous node.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPreviousOutput(String nodeId) {
        if (previousOutputs == null) return null;
        return (T) previousOutputs.get(nodeId);
    }

    /**
     * Get a value from global context (trigger input, env vars, etc).
     */
    @SuppressWarnings("unchecked")
    public <T> T getGlobal(String key, T defaultValue) {
        if (globalContext == null) return defaultValue;
        Object value = globalContext.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Resolve a credential by ID.
     */
    public Map<String, Object> resolveCredential(UUID credentialId) {
        if (credentialResolver == null) {
            throw new IllegalStateException("CredentialResolver not configured");
        }
        return credentialResolver.resolve(credentialId, userId);
    }

    /**
     * Evaluate an expression in the current context.
     */
    public Object evaluateExpression(String expression) {
        if (expressionEvaluator == null) {
            throw new IllegalStateException("ExpressionEvaluator not configured");
        }
        return expressionEvaluator.evaluate(expression, this);
    }
}
