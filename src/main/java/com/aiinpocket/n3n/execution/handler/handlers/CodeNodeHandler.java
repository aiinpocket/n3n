package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.JavaScriptEngine;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.ScriptEngine;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.ScriptResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for Code nodes that execute JavaScript code.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CodeNodeHandler extends AbstractNodeHandler {

    private final JavaScriptEngine javaScriptEngine;

    @Override
    public String getType() {
        return "code";
    }

    @Override
    public String getDisplayName() {
        return "Code";
    }

    @Override
    public String getDescription() {
        return "Execute custom JavaScript code to transform data.";
    }

    @Override
    public String getCategory() {
        return "Transform";
    }

    @Override
    public String getIcon() {
        return "code";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String code = getStringConfig(context, "code", "");
        String language = getStringConfig(context, "language", "javascript");
        long timeout = getIntConfig(context, "timeout", 30000);

        if (code.isEmpty()) {
            return NodeExecutionResult.failure("No code provided");
        }

        // Currently only JavaScript is supported
        if (!"javascript".equalsIgnoreCase(language) && !"js".equalsIgnoreCase(language)) {
            return NodeExecutionResult.failure("Unsupported language: " + language + ". Only JavaScript is supported.");
        }

        ScriptEngine engine = javaScriptEngine;
        if (!engine.isAvailable()) {
            return NodeExecutionResult.failure("JavaScript engine is not available");
        }

        // Prepare input data
        Map<String, Object> inputData = context.getInputData();
        if (inputData == null) {
            inputData = new HashMap<>();
        }

        // Add helper context
        Map<String, Object> scriptInput = new HashMap<>(inputData);
        scriptInput.put("$executionId", context.getExecutionId().toString());
        scriptInput.put("$nodeId", context.getNodeId());

        try {
            log.debug("Executing code node {} with language {}, timeout {}ms",
                context.getNodeId(), language, timeout);

            ScriptResult result = engine.execute(code, scriptInput, timeout);

            if (!result.isSuccess()) {
                log.warn("Code execution failed: {} - {}", result.getErrorType(), result.getErrorMessage());
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(result.getErrorMessage())
                    .metadata(Map.of(
                        "errorType", result.getErrorType() != null ? result.getErrorType() : "UNKNOWN",
                        "logs", result.getLogs() != null ? result.getLogs() : List.of()
                    ))
                    .build();
            }

            // Build output
            Map<String, Object> output = new HashMap<>();
            if (result.getData() != null) {
                output.putAll(result.getData());
            } else if (result.getOutput() != null) {
                output.put("result", result.getOutput());
            }

            // Include logs in metadata if present
            Map<String, Object> metadata = new HashMap<>();
            if (result.getLogs() != null && !result.getLogs().isEmpty()) {
                metadata.put("logs", result.getLogs());
            }
            metadata.put("executionTimeMs", result.getExecutionTimeMs());

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .metadata(metadata)
                .build();

        } catch (Exception e) {
            log.error("Code execution error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Code execution error: " + e.getMessage());
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object code = config.get("code");
        if (code == null || code.toString().trim().isEmpty()) {
            return ValidationResult.invalid("code", "Code is required");
        }

        // Validate syntax
        String codeStr = code.toString();
        if (!javaScriptEngine.validateSyntax(codeStr)) {
            return ValidationResult.invalid("code", "Invalid JavaScript syntax");
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("code"),
            "properties", Map.of(
                "code", Map.of(
                    "type", "string",
                    "title", "Code",
                    "description", "JavaScript code to execute. Use $input or $json to access input data.",
                    "format", "code",
                    "language", "javascript"
                ),
                "language", Map.of(
                    "type", "string",
                    "title", "Language",
                    "enum", List.of("javascript"),
                    "default", "javascript",
                    "description", "Programming language (currently only JavaScript is supported)"
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "title", "Timeout (ms)",
                    "default", 30000,
                    "minimum", 1000,
                    "maximum", 300000,
                    "description", "Maximum execution time in milliseconds"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
