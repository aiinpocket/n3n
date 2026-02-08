package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String template rendering tool
 * Supports variable substitution
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    // Supports {{variable}} and ${variable} formats
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}|\\$\\{([\\w.]+)}");

    @Override
    public String getId() {
        return "template";
    }

    @Override
    public String getName() {
        return "Template";
    }

    @Override
    public String getDescription() {
        return """
                String template rendering tool with variable substitution.

                Supported variable formats:
                - {{variable}} - Mustache style
                - ${variable} - Shell style

                Supports nested variable access: {{user.name}}

                Parameters:
                - template: Template string
                - variables: Variables object (JSON string or Map)
                - strict: Strict mode (throws error for undefined variables, default: false)

                Example:
                template: "Hello, {{name}}! You have {{count}} messages."
                variables: {"name": "Alice", "count": 5}
                Result: "Hello, Alice! You have 5 messages."
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "template", Map.of(
                                "type", "string",
                                "description", "Template string"
                        ),
                        "variables", Map.of(
                                "type", "string",
                                "description", "Variables (JSON format)"
                        ),
                        "strict", Map.of(
                                "type", "boolean",
                                "description", "Strict mode",
                                "default", false
                        )
                ),
                "required", List.of("template", "variables")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String template = (String) parameters.get("template");
                Object variablesObj = parameters.get("variables");
                boolean strict = Boolean.TRUE.equals(parameters.get("strict"));

                if (template == null || template.isEmpty()) {
                    return ToolResult.failure("Template cannot be empty");
                }

                // Parse variables
                Map<String, Object> variables;
                if (variablesObj instanceof String) {
                    variables = objectMapper.readValue((String) variablesObj, new TypeReference<>() {});
                } else if (variablesObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) variablesObj;
                    variables = map;
                } else {
                    return ToolResult.failure("Variables must be a JSON string or Map");
                }

                // Render template
                String result = renderTemplate(template, variables, strict);

                return ToolResult.success(
                        "Render result:\n" + result,
                        Map.of(
                                "result", result,
                                "template", template,
                                "variableCount", variables.size()
                        )
                );

            } catch (Exception e) {
                log.error("Template rendering failed", e);
                return ToolResult.failure("Template rendering failed");
            }
        });
    }

    private String renderTemplate(String template, Map<String, Object> variables, boolean strict) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            Object value = resolveVariable(varName, variables);

            if (value == null) {
                if (strict) {
                    throw new IllegalArgumentException("Undefined variable: " + varName);
                }
                value = matcher.group(); // Keep original variable marker
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Object resolveVariable(String path, Map<String, Object> variables) {
        String[] parts = path.split("\\.");
        Object current = variables;

        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
