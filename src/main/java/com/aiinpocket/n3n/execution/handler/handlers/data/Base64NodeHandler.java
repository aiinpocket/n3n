package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handler for Base64 encoding and decoding operations.
 * Supports standard and URL-safe Base64 variants.
 */
@Component
@Slf4j
public class Base64NodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "base64";
    }

    @Override
    public String getDisplayName() {
        return "Base64";
    }

    @Override
    public String getDescription() {
        return "Encode and decode Base64 strings.";
    }

    @Override
    public String getCategory() {
        return "Data Transformation";
    }

    @Override
    public String getIcon() {
        return "code";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "encode");
        boolean urlSafe = getBooleanConfig(context, "urlSafe", false);

        String input = getStringConfig(context, "input", "");
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data == null) data = context.getInputData().get("input");
            if (data == null) data = context.getInputData().get("text");
            if (data != null) input = data.toString();
        }

        if (input.isEmpty()) {
            return NodeExecutionResult.failure("Input data is required for Base64 operation.");
        }

        try {
            String result;
            switch (operation) {
                case "encode":
                    Base64.Encoder encoder = urlSafe
                        ? Base64.getUrlEncoder()
                        : Base64.getEncoder();
                    boolean noPadding = getBooleanConfig(context, "noPadding", false);
                    if (noPadding) encoder = encoder.withoutPadding();
                    result = encoder.encodeToString(input.getBytes(StandardCharsets.UTF_8));
                    break;

                case "decode":
                    Base64.Decoder decoder = urlSafe
                        ? Base64.getUrlDecoder()
                        : Base64.getDecoder();
                    byte[] decoded = decoder.decode(input.trim());
                    result = new String(decoded, StandardCharsets.UTF_8);
                    break;

                case "validate":
                    boolean isValid = isValidBase64(input, urlSafe);
                    return NodeExecutionResult.success(Map.of(
                        "isValid", isValid,
                        "input", input
                    ));

                default:
                    return NodeExecutionResult.failure("Unknown operation: " + operation);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("result", result);
            output.put("operation", operation);
            output.put("inputLength", input.length());
            output.put("outputLength", result.length());

            return NodeExecutionResult.success(output);

        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failure("Base64 " + operation + " failed: " + e.getMessage());
        }
    }

    private boolean isValidBase64(String input, boolean urlSafe) {
        try {
            Base64.Decoder decoder = urlSafe
                ? Base64.getUrlDecoder()
                : Base64.getDecoder();
            decoder.decode(input.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string", "title", "Operation",
            "enum", List.of("encode", "decode", "validate"),
            "default", "encode"
        ));
        properties.put("input", Map.of(
            "type", "string", "title", "Input",
            "description", "The text to encode/decode"
        ));
        properties.put("urlSafe", Map.of(
            "type", "boolean", "title", "URL Safe",
            "description", "Use URL-safe Base64 variant",
            "default", false
        ));
        properties.put("noPadding", Map.of(
            "type", "boolean", "title", "No Padding",
            "description", "Omit padding characters (=) in encoded output",
            "default", false
        ));

        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("operation")
        );
    }
}
