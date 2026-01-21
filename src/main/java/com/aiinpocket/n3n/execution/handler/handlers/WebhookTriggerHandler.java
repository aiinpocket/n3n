package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for webhook trigger nodes.
 * These nodes start workflow execution when a webhook is called.
 */
@Component
@Slf4j
public class WebhookTriggerHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "webhookTrigger";
    }

    @Override
    public String getDisplayName() {
        return "Webhook Trigger";
    }

    @Override
    public String getDescription() {
        return "Triggers workflow execution when a webhook URL is called.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "api";
    }

    @Override
    public boolean isTrigger() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();

        // Build webhook context output
        Map<String, Object> output = new HashMap<>();
        output.put("triggeredAt", Instant.now().toString());
        output.put("triggerType", "webhook");

        // Include request data if available
        if (inputData != null) {
            // HTTP method
            if (inputData.containsKey("method")) {
                output.put("method", inputData.get("method"));
            }

            // Request headers
            if (inputData.containsKey("headers")) {
                output.put("headers", inputData.get("headers"));
            }

            // Query parameters
            if (inputData.containsKey("query")) {
                output.put("query", inputData.get("query"));
            }

            // Request body
            if (inputData.containsKey("body")) {
                output.put("body", inputData.get("body"));
            }

            // Path parameters
            if (inputData.containsKey("params")) {
                output.put("params", inputData.get("params"));
            }

            // Full data passthrough
            output.put("data", inputData);
        }

        // Validate signature if configured
        String secret = getStringConfig(context, "secret", "");
        if (!secret.isEmpty() && inputData != null) {
            String signature = getHeaderValue(inputData, "x-webhook-signature");
            String body = inputData.containsKey("rawBody")
                ? inputData.get("rawBody").toString()
                : "";

            if (signature != null && !signature.isEmpty()) {
                boolean valid = validateSignature(body, signature, secret);
                output.put("signatureValid", valid);

                if (!valid && getBooleanConfig(context, "requireSignature", false)) {
                    return NodeExecutionResult.failure("Invalid webhook signature");
                }
            } else if (getBooleanConfig(context, "requireSignature", false)) {
                return NodeExecutionResult.failure("Missing webhook signature");
            }
        }

        return NodeExecutionResult.success(output);
    }

    @SuppressWarnings("unchecked")
    private String getHeaderValue(Map<String, Object> inputData, String headerName) {
        Object headers = inputData.get("headers");
        if (headers instanceof Map) {
            Map<String, Object> headersMap = (Map<String, Object>) headers;
            // Try exact match first
            if (headersMap.containsKey(headerName)) {
                return headersMap.get(headerName).toString();
            }
            // Try case-insensitive match
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)) {
                    return entry.getValue().toString();
                }
            }
        }
        return null;
    }

    private boolean validateSignature(String payload, String signature, String secret) {
        try {
            // Support different signature formats
            String expected;
            if (signature.startsWith("sha256=")) {
                // GitHub style
                expected = "sha256=" + HmacUtils.hmacSha256Hex(secret, payload);
            } else if (signature.startsWith("sha1=")) {
                // Legacy style
                expected = "sha1=" + HmacUtils.hmacSha1Hex(secret, payload);
            } else {
                // Plain HMAC-SHA256
                expected = HmacUtils.hmacSha256Hex(secret, payload);
            }
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.warn("Failed to validate signature: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "title", "Webhook Path",
                    "description", "Custom path for the webhook URL (optional)"
                ),
                "method", Map.of(
                    "type", "string",
                    "title", "HTTP Method",
                    "enum", List.of("GET", "POST", "PUT", "PATCH", "DELETE", "ANY"),
                    "default", "POST",
                    "description", "Allowed HTTP method for the webhook"
                ),
                "secret", Map.of(
                    "type", "string",
                    "title", "Secret",
                    "description", "Secret key for signature validation",
                    "format", "password"
                ),
                "requireSignature", Map.of(
                    "type", "boolean",
                    "title", "Require Signature",
                    "default", false,
                    "description", "Fail if signature is missing or invalid"
                ),
                "responseMode", Map.of(
                    "type", "string",
                    "title", "Response Mode",
                    "enum", List.of("immediate", "lastNode", "custom"),
                    "default", "immediate",
                    "description", "When to respond to the webhook"
                ),
                "responseCode", Map.of(
                    "type", "integer",
                    "title", "Response Code",
                    "default", 200,
                    "description", "HTTP response code for immediate response"
                ),
                "responseBody", Map.of(
                    "type", "object",
                    "title", "Response Body",
                    "description", "Custom response body (for immediate response mode)"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),  // Triggers have no inputs
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
