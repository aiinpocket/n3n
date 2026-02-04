package com.aiinpocket.n3n.plugin.handler;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Dynamic node handler that executes based on plugin definition.
 * This handler reads the node configuration from the plugin's node_definitions
 * and executes the appropriate logic based on the selected resource and operation.
 */
@Slf4j
public class DynamicPluginNodeHandler extends AbstractNodeHandler {

    @Getter
    private final UUID pluginId;

    private final String nodeType;
    private final Map<String, Object> nodeDefinition;
    private final Map<String, Object> configSchema;
    private final HttpClient httpClient;

    public DynamicPluginNodeHandler(
            UUID pluginId,
            String nodeType,
            Map<String, Object> nodeDefinition,
            Map<String, Object> configSchema) {
        this.pluginId = pluginId;
        this.nodeType = nodeType;
        this.nodeDefinition = nodeDefinition;
        this.configSchema = configSchema != null ? configSchema : Map.of();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getType() {
        return nodeType;
    }

    @Override
    public String getDisplayName() {
        return (String) nodeDefinition.getOrDefault("displayName", nodeType);
    }

    @Override
    public String getDescription() {
        return (String) nodeDefinition.getOrDefault("description", "Plugin node: " + nodeType);
    }

    @Override
    public String getCategory() {
        return (String) nodeDefinition.getOrDefault("category", "plugin");
    }

    @Override
    public String getIcon() {
        return (String) nodeDefinition.getOrDefault("icon", "appstore");
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return buildConfigSchema();
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "data", "type", "any", "required", false, "description", "Input data")
                ),
                "outputs", List.of(
                        Map.of("name", "result", "type", "any", "description", "Operation result")
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        try {
            String resource = getStringConfig(context, "resource", "");
            String operation = getStringConfig(context, "operation", "");

            log.info("Executing plugin node {} - resource: {}, operation: {}", nodeType, resource, operation);

            // Get the operation definition
            Map<String, Object> resources = (Map<String, Object>) nodeDefinition.get("resources");
            if (resources == null) {
                return NodeExecutionResult.failure("Plugin node has no resources defined");
            }

            Map<String, Object> resourceDef = (Map<String, Object>) resources.get(resource);
            if (resourceDef == null) {
                return NodeExecutionResult.failure("Resource not found: " + resource);
            }

            List<Map<String, Object>> operations = (List<Map<String, Object>>) resourceDef.get("operations");
            if (operations == null) {
                return NodeExecutionResult.failure("No operations defined for resource: " + resource);
            }

            Map<String, Object> operationDef = operations.stream()
                    .filter(op -> operation.equals(op.get("name")))
                    .findFirst()
                    .orElse(null);

            if (operationDef == null) {
                return NodeExecutionResult.failure("Operation not found: " + operation);
            }

            // Execute based on node type - delegate to specific handlers
            Map<String, Object> result = executeOperation(context, resource, operation, operationDef);

            return NodeExecutionResult.success(result);

        } catch (Exception e) {
            log.error("Error executing plugin node {}: {}", nodeType, e.getMessage(), e);
            return NodeExecutionResult.failure("Plugin execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeOperation(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> operationDef) throws Exception {

        // Get credential if required
        Map<String, Object> credential = Map.of();
        Object credentialId = context.getNodeConfig().get("credentialId");
        if (credentialId != null && context.getCredentialResolver() != null) {
            try {
                UUID credId = UUID.fromString(credentialId.toString());
                credential = context.resolveCredential(credId);
            } catch (Exception e) {
                log.warn("Failed to resolve credential: {}", e.getMessage());
            }
        }

        // Dispatch to handler based on node type
        return switch (nodeType) {
            case "openai" -> executeOpenAI(context, resource, operation, credential);
            case "slack" -> executeSlack(context, resource, operation, credential);
            case "googleSheets" -> executeGoogleSheets(context, resource, operation, credential);
            case "awsS3" -> executeAwsS3(context, resource, operation, credential);
            case "discord" -> executeDiscord(context, resource, operation, credential);
            case "jsonTransformer" -> executeJsonTransformer(context, resource, operation);
            default -> executeGeneric(context, resource, operation, operationDef, credential);
        };
    }

    private Map<String, Object> executeOpenAI(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> credential) throws Exception {

        String apiKey = (String) credential.getOrDefault("apiKey", "");
        if (apiKey.isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }

        String model = getStringConfig(context, "model", "gpt-4o-mini");
        String prompt = getStringConfig(context, "prompt", "");
        double temperature = getDoubleConfig(context, "temperature", 0.7);
        int maxTokens = getIntConfig(context, "maxTokens", 1024);

        if ("chat".equals(resource) && "createCompletion".equals(operation)) {
            String requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [{"role": "user", "content": "%s"}],
                    "temperature": %f,
                    "max_tokens": %d
                }
                """, model, escapeJson(prompt), temperature, maxTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return Map.of(
                    "statusCode", response.statusCode(),
                    "body", response.body(),
                    "success", response.statusCode() == 200
            );
        }

        return Map.of("error", "Unsupported operation: " + resource + "." + operation);
    }

    private Map<String, Object> executeSlack(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> credential) throws Exception {

        String botToken = (String) credential.getOrDefault("botToken", "");
        if (botToken.isEmpty()) {
            throw new IllegalArgumentException("Slack bot token is required");
        }

        if ("message".equals(resource) && "send".equals(operation)) {
            String channel = getStringConfig(context, "channel", "");
            String text = getStringConfig(context, "text", "");

            String requestBody = String.format("""
                {
                    "channel": "%s",
                    "text": "%s"
                }
                """, escapeJson(channel), escapeJson(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://slack.com/api/chat.postMessage"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + botToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return Map.of(
                    "statusCode", response.statusCode(),
                    "body", response.body(),
                    "success", response.statusCode() == 200
            );
        }

        return Map.of("error", "Unsupported operation: " + resource + "." + operation);
    }

    private Map<String, Object> executeGoogleSheets(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> credential) {
        // Placeholder - would require OAuth2 implementation
        return Map.of(
                "message", "Google Sheets integration requires OAuth2 setup",
                "resource", resource,
                "operation", operation
        );
    }

    private Map<String, Object> executeAwsS3(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> credential) {
        // Placeholder - would require AWS SDK
        return Map.of(
                "message", "AWS S3 integration requires AWS SDK setup",
                "resource", resource,
                "operation", operation
        );
    }

    private Map<String, Object> executeDiscord(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> credential) throws Exception {

        String botToken = (String) credential.getOrDefault("botToken", "");
        if (botToken.isEmpty()) {
            throw new IllegalArgumentException("Discord bot token is required");
        }

        if ("message".equals(resource) && "send".equals(operation)) {
            String channelId = getStringConfig(context, "channelId", "");
            String content = getStringConfig(context, "content", "");

            String requestBody = String.format("""
                {
                    "content": "%s"
                }
                """, escapeJson(content));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bot " + botToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return Map.of(
                    "statusCode", response.statusCode(),
                    "body", response.body(),
                    "success", response.statusCode() == 200 || response.statusCode() == 201
            );
        }

        return Map.of("error", "Unsupported operation: " + resource + "." + operation);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeJsonTransformer(
            NodeExecutionContext context,
            String resource,
            String operation) {

        Object inputData = context.getInputData().get("data");
        if (inputData == null) {
            inputData = context.getInputData();
        }

        return switch (operation) {
            case "parse" -> {
                String jsonString = getStringConfig(context, "jsonString", "");
                if (jsonString.isEmpty() && inputData instanceof String) {
                    jsonString = (String) inputData;
                }
                // Simple JSON parse - in production use Jackson
                yield Map.of("parsed", jsonString, "type", "object");
            }
            case "stringify" -> {
                yield Map.of("json", inputData.toString());
            }
            case "getValue" -> {
                String path = getStringConfig(context, "path", "");
                // Simple path extraction - in production use JsonPath
                if (inputData instanceof Map) {
                    Object value = ((Map<String, Object>) inputData).get(path);
                    yield Map.of("value", value != null ? value : "");
                }
                yield Map.of("value", "");
            }
            default -> Map.of("error", "Unsupported operation: " + operation);
        };
    }

    private Map<String, Object> executeGeneric(
            NodeExecutionContext context,
            String resource,
            String operation,
            Map<String, Object> operationDef,
            Map<String, Object> credential) {
        // Generic execution for unknown plugin types
        // Just return the configuration and input for debugging
        return Map.of(
                "nodeType", nodeType,
                "resource", resource,
                "operation", operation,
                "config", context.getNodeConfig(),
                "input", context.getInputData(),
                "message", "Generic plugin execution - implement specific handler for: " + nodeType
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConfigSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // Add credential selector
        properties.put("credentialId", Map.of(
                "type", "string",
                "title", "Credential",
                "description", "Select credential for authentication",
                "x-component", "credential-select"
        ));

        // Add resource selector based on node definition
        Map<String, Object> resources = (Map<String, Object>) nodeDefinition.get("resources");
        if (resources != null && !resources.isEmpty()) {
            List<Map<String, Object>> resourceOptions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : resources.entrySet()) {
                Map<String, Object> resDef = (Map<String, Object>) entry.getValue();
                resourceOptions.add(Map.of(
                        "value", entry.getKey(),
                        "label", resDef.getOrDefault("displayName", entry.getKey())
                ));
            }

            properties.put("resource", Map.of(
                    "type", "string",
                    "title", "Resource",
                    "description", "Select resource type",
                    "enum", resources.keySet().toArray(),
                    "x-options", resourceOptions
            ));

            // Add operation selector (dynamically based on resource)
            properties.put("operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "description", "Select operation",
                    "x-depends-on", "resource"
            ));
        }

        // Add operation definitions for frontend
        schema.put("x-operation-definitions", buildOperationDefinitions());

        schema.put("properties", properties);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildOperationDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();

        Map<String, Object> resources = (Map<String, Object>) nodeDefinition.get("resources");
        if (resources == null) return definitions;

        for (Map.Entry<String, Object> resEntry : resources.entrySet()) {
            String resourceName = resEntry.getKey();
            Map<String, Object> resourceDef = (Map<String, Object>) resEntry.getValue();

            List<Map<String, Object>> operations = (List<Map<String, Object>>) resourceDef.get("operations");
            if (operations == null) continue;

            for (Map<String, Object> opDef : operations) {
                definitions.add(Map.of(
                        "resource", resourceName,
                        "name", opDef.get("name"),
                        "displayName", opDef.getOrDefault("displayName", opDef.get("name")),
                        "fields", opDef.getOrDefault("fields", List.of())
                ));
            }
        }

        return definitions;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
