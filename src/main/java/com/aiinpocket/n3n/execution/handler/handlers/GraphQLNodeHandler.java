package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Handler for GraphQL nodes.
 * Executes GraphQL queries and mutations.
 */
@Component
@Slf4j
public class GraphQLNodeHandler extends AbstractNodeHandler {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .build();

    @Override
    public String getType() {
        return "graphql";
    }

    @Override
    public String getDisplayName() {
        return "GraphQL";
    }

    @Override
    public String getDescription() {
        return "Executes GraphQL queries and mutations.";
    }

    @Override
    public String getCategory() {
        return "Communication";
    }

    @Override
    public String getIcon() {
        return "api";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String endpoint = getStringConfig(context, "endpoint", "");
        String query = getStringConfig(context, "query", "");
        String operationName = getStringConfig(context, "operationName", "");

        // Get variables from config or input
        Object variablesConfig = context.getNodeConfig().get("variables");
        Map<String, Object> variables = new HashMap<>();
        if (variablesConfig instanceof Map) {
            variables = (Map<String, Object>) variablesConfig;
        }

        // Merge with input data if specified
        if (context.getInputData() != null) {
            Object inputVars = context.getInputData().get("variables");
            if (inputVars instanceof Map) {
                variables.putAll((Map<String, Object>) inputVars);
            }
        }

        if (endpoint.isEmpty()) {
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("GraphQL endpoint is required")
                .build();
        }

        if (query.isEmpty()) {
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("GraphQL query is required")
                .build();
        }

        log.info("Executing GraphQL query to: {}", endpoint);

        try {
            // Build request body
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("query", query);
            if (!operationName.isEmpty()) {
                requestBody.put("operationName", operationName);
            }
            if (!variables.isEmpty()) {
                requestBody.put("variables", variables);
            }

            String jsonBody = toJson(requestBody);

            // Build headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            // Add auth headers if credential provided
            Object headersConfig = context.getNodeConfig().get("headers");
            if (headersConfig instanceof Map) {
                ((Map<String, Object>) headersConfig).forEach((k, v) ->
                    headers.put(k, v != null ? v.toString() : ""));
            }

            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            headers.forEach(requestBuilder::header);

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse response
            Map<String, Object> responseData = parseJson(response.body());

            Map<String, Object> output = new HashMap<>();
            output.put("statusCode", response.statusCode());
            output.put("data", responseData.get("data"));
            output.put("errors", responseData.get("errors"));
            output.put("extensions", responseData.get("extensions"));

            boolean hasErrors = responseData.containsKey("errors") && responseData.get("errors") != null;

            return NodeExecutionResult.builder()
                .success(!hasErrors || responseData.containsKey("data"))
                .output(output)
                .errorMessage(hasErrors ? "GraphQL returned errors" : null)
                .build();

        } catch (Exception e) {
            log.error("GraphQL request failed: {}", e.getMessage(), e);
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("GraphQL request failed: " + e.getMessage())
                .build();
        }
    }

    private String toJson(Map<String, Object> map) {
        // Simple JSON serialization - in production use Jackson/Gson
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(toJsonValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(",");
                sb.append(toJsonValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Simple JSON parsing - in production use Jackson/Gson
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("endpoint", "query"),
            "properties", Map.of(
                "endpoint", Map.of(
                    "type", "string",
                    "title", "Endpoint",
                    "description", "GraphQL endpoint URL"
                ),
                "query", Map.of(
                    "type", "string",
                    "title", "Query",
                    "description", "GraphQL query or mutation",
                    "format", "code"
                ),
                "operationName", Map.of(
                    "type", "string",
                    "title", "Operation Name",
                    "description", "Name of the operation (for documents with multiple operations)"
                ),
                "variables", Map.of(
                    "type", "object",
                    "title", "Variables",
                    "description", "GraphQL variables"
                ),
                "headers", Map.of(
                    "type", "object",
                    "title", "Headers",
                    "description", "Additional HTTP headers"
                ),
                "credentialId", Map.of(
                    "type", "string",
                    "title", "Credential",
                    "description", "ID of credential for authentication"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "variables", "type", "object", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "data", "type", "any"),
                Map.of("name", "errors", "type", "array")
            )
        );
    }
}
