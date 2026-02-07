package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for respond-to-webhook nodes.
 * Sends an HTTP response back to the webhook caller that triggered the workflow.
 * Used in conjunction with Webhook Trigger nodes configured with "lastNode" response mode.
 */
@Component
@Slf4j
public class RespondWebhookNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "respondWebhook";
    }

    @Override
    public String getDisplayName() {
        return "Respond to Webhook";
    }

    @Override
    public String getDescription() {
        return "Sends an HTTP response back to the webhook caller.";
    }

    @Override
    public String getCategory() {
        return "Output";
    }

    @Override
    public String getIcon() {
        return "send";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        int statusCode = getIntConfig(context, "statusCode", 200);
        String contentType = getStringConfig(context, "contentType", "application/json");
        Map<String, Object> customHeaders = getMapConfig(context, "headers");

        // Validate status code range
        if (statusCode < 100 || statusCode > 599) {
            return NodeExecutionResult.failure("Invalid HTTP status code: " + statusCode + ". Must be between 100 and 599.");
        }

        // Determine response body
        Object responseBody = resolveResponseBody(context);

        // Build the response data that the execution engine will use to respond
        Map<String, Object> webhookResponse = new LinkedHashMap<>();
        webhookResponse.put("statusCode", statusCode);
        webhookResponse.put("contentType", contentType);

        // Merge custom headers
        Map<String, Object> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("Content-Type", contentType);
        if (customHeaders != null && !customHeaders.isEmpty()) {
            responseHeaders.putAll(customHeaders);
        }
        webhookResponse.put("headers", responseHeaders);

        // Set body
        if (responseBody != null) {
            webhookResponse.put("body", responseBody);
        } else {
            webhookResponse.put("body", Map.of());
        }

        // Build output
        Map<String, Object> output = new HashMap<>();
        output.put("webhookResponse", webhookResponse);
        output.put("responseSent", true);
        output.put("statusCode", statusCode);

        log.debug("Respond to webhook: status={}, contentType={}", statusCode, contentType);

        // Return result with metadata that the execution engine uses
        // to send the actual HTTP response
        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .metadata(Map.of(
                "webhookResponse", webhookResponse
            ))
            .build();
    }

    /**
     * Resolve the response body from config or input data.
     */
    @SuppressWarnings("unchecked")
    private Object resolveResponseBody(NodeExecutionContext context) {
        String bodyMode = getStringConfig(context, "bodyMode", "auto");

        switch (bodyMode) {
            case "json":
                // Use the configured body as a JSON object
                Object bodyConfig = context.getNodeConfig().get("body");
                if (bodyConfig != null) {
                    return bodyConfig;
                }
                return Map.of();

            case "text":
                // Use the configured body as plain text
                return getStringConfig(context, "bodyText", "");

            case "expression":
                // Evaluate an expression to determine the body
                String expression = getStringConfig(context, "bodyExpression", "");
                if (!expression.isEmpty()) {
                    try {
                        return context.evaluateExpression(expression);
                    } catch (Exception e) {
                        log.warn("Failed to evaluate body expression: {}", e.getMessage());
                        return Map.of("error", "Expression evaluation failed: " + e.getMessage());
                    }
                }
                return Map.of();

            case "input":
                // Forward input data as the response body
                Map<String, Object> inputData = context.getInputData();
                return inputData != null ? inputData : Map.of();

            case "auto":
            default:
                // Auto-detect: use input data if available, otherwise configured body
                Map<String, Object> input = context.getInputData();
                if (input != null && !input.isEmpty()) {
                    return input;
                }
                Object configBody = context.getNodeConfig().get("body");
                return configBody != null ? configBody : Map.of();
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("statusCode", Map.of(
            "type", "integer",
            "title", "Status Code",
            "description", "HTTP response status code",
            "default", 200,
            "minimum", 100,
            "maximum", 599
        ));

        properties.put("contentType", Map.of(
            "type", "string",
            "title", "Content Type",
            "description", "Response content type",
            "enum", List.of("application/json", "text/plain", "text/html", "application/xml", "text/xml"),
            "default", "application/json"
        ));

        properties.put("bodyMode", Map.of(
            "type", "string",
            "title", "Body Source",
            "description", "How to determine the response body",
            "enum", List.of("auto", "json", "text", "input", "expression"),
            "enumNames", List.of(
                "Auto (input data or configured body)",
                "JSON Object",
                "Plain Text",
                "Forward Input Data",
                "Expression"
            ),
            "default", "auto"
        ));

        properties.put("body", Map.of(
            "type", "object",
            "title", "Response Body (JSON)",
            "description", "JSON response body (for 'json' body mode)"
        ));

        properties.put("bodyText", Map.of(
            "type", "string",
            "title", "Response Body (Text)",
            "description", "Plain text response body (for 'text' body mode)"
        ));

        properties.put("bodyExpression", Map.of(
            "type", "string",
            "title", "Body Expression",
            "description", "Expression to evaluate for response body (for 'expression' body mode)"
        ));

        properties.put("headers", Map.of(
            "type", "object",
            "title", "Custom Headers",
            "description", "Additional response headers as key-value pairs",
            "additionalProperties", Map.of("type", "string")
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object",
                    "description", "Response confirmation with status code and sent flag")
            )
        );
    }
}
