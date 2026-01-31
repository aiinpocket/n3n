package com.aiinpocket.n3n.execution.handler.multiop;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Abstract base class for n8n-style multi-operation nodes.
 *
 * Multi-operation nodes support:
 * - Resource selection (grouping of operations)
 * - Operation selection (specific action to perform)
 * - Dynamic fields based on selected operation
 * - Credential integration via credentialId
 *
 * Example structure:
 * <pre>
 * ┌─────────────────────────────────────┐
 * │  Credential: [Select credential ▼] │
 * ├─────────────────────────────────────┤
 * │  Resource:   [Messages ▼]          │
 * ├─────────────────────────────────────┤
 * │  Operation:  [Send Message ▼]      │
 * ├─────────────────────────────────────┤
 * │  ┌── Dynamic Fields ─────────────┐ │
 * │  │ Channel:  [#general]          │ │
 * │  │ Text:     [Hello World]       │ │
 * │  └───────────────────────────────┘ │
 * └─────────────────────────────────────┘
 * </pre>
 *
 * Subclasses must implement:
 * - getResources(): Define available resources
 * - getOperations(): Define operations for each resource
 * - executeOperation(): Perform the actual operation
 */
@Slf4j
public abstract class MultiOperationNodeHandler extends AbstractNodeHandler {

    /**
     * Get available resources for this node.
     * Resources group related operations (e.g., "message", "channel", "user").
     *
     * @return map of resource name to ResourceDef
     */
    public abstract Map<String, ResourceDef> getResources();

    /**
     * Get operations available for each resource.
     *
     * @return map of resource name to list of OperationDef
     */
    public abstract Map<String, List<OperationDef>> getOperations();

    /**
     * Execute the selected operation.
     *
     * @param context    execution context
     * @param resource   selected resource name
     * @param operation  selected operation name
     * @param credential resolved credential data (may be empty if no credential)
     * @param params     operation parameters extracted from config
     * @return execution result
     */
    public abstract NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    );

    /**
     * Get the credential type this node requires (e.g., "openai", "slack").
     * Return null if no credential is needed.
     */
    public String getCredentialType() {
        return null;
    }

    @Override
    protected final NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Extract resource and operation from config
        String resource = getStringConfig(context, "resource", "");
        String operation = getStringConfig(context, "operation", "");

        if (resource.isEmpty()) {
            return NodeExecutionResult.failure("Resource not selected");
        }
        if (operation.isEmpty()) {
            return NodeExecutionResult.failure("Operation not selected");
        }

        // Validate resource exists
        if (!getResources().containsKey(resource)) {
            return NodeExecutionResult.failure("Unknown resource: " + resource);
        }

        // Validate operation exists for resource
        List<OperationDef> ops = getOperations().get(resource);
        if (ops == null || ops.stream().noneMatch(op -> op.getName().equals(operation))) {
            return NodeExecutionResult.failure("Unknown operation: " + operation + " for resource: " + resource);
        }

        // Resolve credential if specified
        Map<String, Object> credential = resolveCredential(context);

        // Extract operation parameters
        Map<String, Object> params = extractOperationParams(context, resource, operation);

        log.debug("Executing {}.{}.{} with {} params",
            getType(), resource, operation, params.size());

        return executeOperation(context, resource, operation, credential, params);
    }

    /**
     * Resolve the credential from context if credentialId is provided.
     */
    protected Map<String, Object> resolveCredential(NodeExecutionContext context) {
        Object credentialIdObj = context.getNodeConfig().get("credentialId");
        if (credentialIdObj == null) {
            return Map.of();
        }

        try {
            UUID credentialId;
            if (credentialIdObj instanceof UUID) {
                credentialId = (UUID) credentialIdObj;
            } else {
                credentialId = UUID.fromString(credentialIdObj.toString());
            }
            return context.resolveCredential(credentialId);
        } catch (Exception e) {
            log.warn("Failed to resolve credential: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Extract parameters for the selected operation from config.
     */
    protected Map<String, Object> extractOperationParams(
        NodeExecutionContext context,
        String resource,
        String operation
    ) {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> config = context.getNodeConfig();

        // Find the operation definition
        List<OperationDef> ops = getOperations().get(resource);
        if (ops == null) return params;

        OperationDef opDef = ops.stream()
            .filter(op -> op.getName().equals(operation))
            .findFirst()
            .orElse(null);

        if (opDef == null || opDef.getFields() == null) return params;

        // Extract values for each field
        for (FieldDef field : opDef.getFields()) {
            String fieldName = field.getName();
            if (config.containsKey(fieldName)) {
                Object value = config.get(fieldName);
                // Apply default if value is null/empty
                if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                    if (field.getDefaultValue() != null) {
                        value = field.getDefaultValue();
                    }
                }
                params.put(fieldName, value);
            } else if (field.getDefaultValue() != null) {
                params.put(fieldName, field.getDefaultValue());
            }
        }

        return params;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        // Credential selector (if credential type is defined)
        if (getCredentialType() != null) {
            properties.put("credentialId", Map.of(
                "type", "string",
                "title", "Credential",
                "format", "credential",
                "x-credential-type", getCredentialType()
            ));
        }

        // Resource selector
        List<String> resourceNames = new ArrayList<>(getResources().keySet());
        List<String> resourceLabels = resourceNames.stream()
            .map(name -> getResources().get(name).getDisplayName())
            .toList();

        properties.put("resource", Map.of(
            "type", "string",
            "title", "Resource",
            "enum", resourceNames,
            "enumNames", resourceLabels,
            "default", resourceNames.isEmpty() ? "" : resourceNames.get(0)
        ));

        // Operation selector (will be filtered by frontend based on resource)
        List<String> allOpNames = new ArrayList<>();
        List<String> allOpLabels = new ArrayList<>();
        for (Map.Entry<String, List<OperationDef>> entry : getOperations().entrySet()) {
            for (OperationDef op : entry.getValue()) {
                if (!allOpNames.contains(op.getName())) {
                    allOpNames.add(op.getName());
                    allOpLabels.add(op.getDisplayName());
                }
            }
        }

        properties.put("operation", Map.of(
            "type", "string",
            "title", "Operation",
            "enum", allOpNames,
            "enumNames", allOpLabels
        ));

        // Add all possible field properties (frontend will show/hide based on operation)
        Set<String> addedFields = new HashSet<>();
        for (Map.Entry<String, List<OperationDef>> entry : getOperations().entrySet()) {
            for (OperationDef op : entry.getValue()) {
                if (op.getFields() != null) {
                    for (FieldDef field : op.getFields()) {
                        if (!addedFields.contains(field.getName())) {
                            properties.put(field.getName(), field.toJsonSchema());
                            addedFields.add(field.getName());
                        }
                    }
                }
            }
        }

        schema.put("properties", properties);

        // Build x-operation-definitions for frontend dynamic rendering
        List<Map<String, Object>> opDefs = new ArrayList<>();
        for (Map.Entry<String, List<OperationDef>> entry : getOperations().entrySet()) {
            String resourceName = entry.getKey();
            for (OperationDef op : entry.getValue()) {
                OperationDef withResource = OperationDef.builder()
                    .name(op.getName())
                    .displayName(op.getDisplayName())
                    .description(op.getDescription())
                    .resource(resourceName)
                    .fields(op.getFields())
                    .requiresCredential(op.isRequiresCredential())
                    .outputDescription(op.getOutputDescription())
                    .build();
                opDefs.add(withResource.toDefinition());
            }
        }
        schema.put("x-operation-definitions", opDefs);

        // Build x-resources for frontend
        List<Map<String, Object>> resourceDefs = new ArrayList<>();
        for (Map.Entry<String, ResourceDef> entry : getResources().entrySet()) {
            ResourceDef res = entry.getValue();
            Map<String, Object> resDef = new LinkedHashMap<>();
            resDef.put("name", entry.getKey());
            resDef.put("displayName", res.getDisplayName());
            if (res.getDescription() != null) {
                resDef.put("description", res.getDescription());
            }
            if (res.getIcon() != null) {
                resDef.put("icon", res.getIcon());
            }
            resourceDefs.add(resDef);
        }
        schema.put("x-resources", resourceDefs);

        // Mark this as a multi-operation node
        schema.put("x-multi-operation", true);
        if (getCredentialType() != null) {
            schema.put("x-credential-type", getCredentialType());
        }

        return schema;
    }

    /**
     * Helper to get a required string parameter.
     */
    protected String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }

    /**
     * Helper to get an optional string parameter.
     */
    protected String getParam(Map<String, Object> params, String name, String defaultValue) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Helper to get an optional integer parameter.
     */
    protected int getIntParam(Map<String, Object> params, String name, int defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Helper to get an optional double parameter.
     */
    protected double getDoubleParam(Map<String, Object> params, String name, double defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Helper to get an optional boolean parameter.
     */
    protected boolean getBoolParam(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Helper to get a list parameter.
     */
    @SuppressWarnings("unchecked")
    protected List<Object> getListParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<>();
    }

    /**
     * Helper to get a map parameter.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getMapParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    /**
     * Helper to get credential value.
     */
    protected String getCredentialValue(Map<String, Object> credential, String key) {
        Object value = credential.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Helper to get required credential value.
     */
    protected String getRequiredCredential(Map<String, Object> credential, String key) {
        String value = getCredentialValue(credential, key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Credential field '" + key + "' is required");
        }
        return value;
    }
}
