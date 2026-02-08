package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base64 encoding/decoding tool
 */
@Component
@Slf4j
public class Base64Tool implements AgentNodeTool {

    @Override
    public String getId() {
        return "base64";
    }

    @Override
    public String getName() {
        return "Base64";
    }

    @Override
    public String getDescription() {
        return """
                Base64 encoding and decoding tool.

                Operations:
                - encode: Encode text to Base64
                - decode: Decode Base64 to text

                Parameters:
                - text: Text to process
                - operation: Operation type (encode or decode)
                - urlSafe: Whether to use URL-safe Base64 (default false)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to process"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("encode", "decode"),
                                "description", "Operation type",
                                "default", "encode"
                        ),
                        "urlSafe", Map.of(
                                "type", "boolean",
                                "description", "Whether to use URL-safe Base64",
                                "default", false
                        )
                ),
                "required", List.of("text")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");
                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("Text cannot be empty");
                }

                String operation = (String) parameters.getOrDefault("operation", "encode");
                boolean urlSafe = Boolean.TRUE.equals(parameters.get("urlSafe"));

                String result;
                if ("encode".equals(operation)) {
                    Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
                    result = encoder.encodeToString(text.getBytes(StandardCharsets.UTF_8));
                } else if ("decode".equals(operation)) {
                    Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
                    result = new String(decoder.decode(text), StandardCharsets.UTF_8);
                } else {
                    return ToolResult.failure("Unsupported operation: " + operation);
                }

                String output = String.format("Base64 %s result:\n%s",
                        "encode".equals(operation) ? "encode" : "decode", result);

                return ToolResult.success(output, Map.of(
                        "operation", operation,
                        "result", result,
                        "urlSafe", urlSafe
                ));

            } catch (IllegalArgumentException e) {
                return ToolResult.failure("Base64 decoding failed: input is not a valid Base64 string");
            } catch (Exception e) {
                log.error("Base64 operation failed", e);
                return ToolResult.failure("Base64 operation failed");
            }
        });
    }

    @Override
    public String getCategory() {
        return "encoding";
    }
}
