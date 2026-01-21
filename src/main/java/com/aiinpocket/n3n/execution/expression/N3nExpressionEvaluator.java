package com.aiinpocket.n3n.execution.expression;

import com.aiinpocket.n3n.execution.handler.ExpressionEvaluator;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of ExpressionEvaluator for n3n workflow expressions.
 *
 * Supported expressions:
 * - {{ $json }} - Current node input data
 * - {{ $json.fieldName }} - Specific field from input
 * - {{ $node["nodeName"].json }} - Output from a specific node
 * - {{ $node["nodeName"].json.field }} - Specific field from node output
 * - {{ $env.VARIABLE_NAME }} - Environment variable
 * - {{ $execution.id }} - Current execution ID
 * - {{ $workflow.id }} - Current workflow/flow ID
 * - {{ $now }} - Current timestamp (ISO-8601)
 * - {{ $timestamp }} - Current timestamp (milliseconds)
 */
@Component
@Slf4j
public class N3nExpressionEvaluator implements ExpressionEvaluator {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");
    private static final Pattern FIELD_PATH_PATTERN = Pattern.compile("^\\$([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.(.+))?$");
    private static final Pattern NODE_REF_PATTERN = Pattern.compile("^\\$node\\[\"([^\"]+)\"\\]\\.json(?:\\.(.+))?$");
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$env\\.([a-zA-Z_][a-zA-Z0-9_]*)$");

    @Override
    public Object evaluate(String expression, NodeExecutionContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        String trimmed = expression.trim();

        // Remove surrounding {{ }} if present
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 2).trim();
        }

        return evaluateExpression(trimmed, context);
    }

    @Override
    public String evaluateTemplate(String template, NodeExecutionContext context) {
        if (template == null || !containsExpression(template)) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = EXPRESSION_PATTERN.matcher(template);

        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object value = evaluateExpression(expr, context);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @Override
    public Map<String, Object> evaluateConfig(Map<String, Object> config, NodeExecutionContext context) {
        if (config == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            result.put(entry.getKey(), evaluateValue(entry.getValue(), context));
        }
        return result;
    }

    @Override
    public boolean containsExpression(String value) {
        return value != null && EXPRESSION_PATTERN.matcher(value).find();
    }

    @Override
    public ValidationResult validateExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return ValidationResult.valid();
        }

        String trimmed = expression.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 2).trim();
        }

        // Basic validation - check it starts with $ and has valid structure
        if (!trimmed.startsWith("$")) {
            return ValidationResult.invalid("expression", "Expression must start with $");
        }

        // Check for known expression types
        if (trimmed.startsWith("$json") ||
            trimmed.startsWith("$node[") ||
            trimmed.startsWith("$env.") ||
            trimmed.startsWith("$execution.") ||
            trimmed.startsWith("$workflow.") ||
            trimmed.equals("$now") ||
            trimmed.equals("$timestamp") ||
            trimmed.startsWith("$input")) {
            return ValidationResult.valid();
        }

        return ValidationResult.invalid("expression", "Unknown expression type: " + trimmed);
    }

    private Object evaluateExpression(String expr, NodeExecutionContext context) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }

        // Check for $now
        if ("$now".equals(expr)) {
            return Instant.now().toString();
        }

        // Check for $timestamp
        if ("$timestamp".equals(expr)) {
            return System.currentTimeMillis();
        }

        // Check for $env.VARIABLE
        Matcher envMatcher = ENV_PATTERN.matcher(expr);
        if (envMatcher.matches()) {
            String envVar = envMatcher.group(1);
            return System.getenv(envVar);
        }

        // Check for $node["nodeName"].json
        Matcher nodeMatcher = NODE_REF_PATTERN.matcher(expr);
        if (nodeMatcher.matches()) {
            String nodeName = nodeMatcher.group(1);
            String fieldPath = nodeMatcher.group(2);

            Map<String, Object> previousOutputs = context.getPreviousOutputs();
            if (previousOutputs != null && previousOutputs.containsKey(nodeName)) {
                Object nodeOutput = previousOutputs.get(nodeName);
                if (fieldPath != null && nodeOutput instanceof Map) {
                    return getNestedValue((Map<?, ?>) nodeOutput, fieldPath);
                }
                return nodeOutput;
            }
            return null;
        }

        // Check for field path expressions ($json.field, $input.field, etc.)
        Matcher fieldMatcher = FIELD_PATH_PATTERN.matcher(expr);
        if (fieldMatcher.matches()) {
            String variable = fieldMatcher.group(1);
            String fieldPath = fieldMatcher.group(2);

            Object rootValue = getRootValue(variable, context);
            if (fieldPath != null && rootValue instanceof Map) {
                return getNestedValue((Map<?, ?>) rootValue, fieldPath);
            }
            return rootValue;
        }

        log.debug("Unknown expression format: {}", expr);
        return null;
    }

    private Object getRootValue(String variable, NodeExecutionContext context) {
        switch (variable) {
            case "json":
            case "input":
                return context.getInputData();

            case "execution":
                return Map.of(
                    "id", context.getExecutionId().toString(),
                    "nodeId", context.getNodeId()
                );

            case "workflow":
            case "flow":
                return Map.of(
                    "id", context.getFlowId().toString(),
                    "version", context.getFlowVersion()
                );

            case "global":
                return context.getGlobalContext();

            default:
                log.debug("Unknown root variable: ${}", variable);
                return null;
        }
    }

    private Object getNestedValue(Map<?, ?> data, String path) {
        if (path == null || path.isEmpty()) {
            return data;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            // Handle array indexing like items[0]
            if (part.contains("[") && part.endsWith("]")) {
                int bracketStart = part.indexOf('[');
                String key = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.length() - 1);

                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(key);
                } else {
                    return null;
                }

                if (current instanceof List) {
                    try {
                        int index = Integer.parseInt(indexStr);
                        List<?> list = (List<?>) current;
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    @SuppressWarnings("unchecked")
    private Object evaluateValue(Object value, NodeExecutionContext context) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            String strValue = (String) value;
            if (containsExpression(strValue)) {
                // If the entire string is one expression, return the evaluated value directly
                Matcher matcher = EXPRESSION_PATTERN.matcher(strValue);
                if (matcher.matches()) {
                    return evaluate(strValue, context);
                }
                // Otherwise, evaluate as template
                return evaluateTemplate(strValue, context);
            }
            return strValue;
        }

        if (value instanceof Map) {
            return evaluateConfig((Map<String, Object>) value, context);
        }

        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(evaluateValue(item, context));
            }
            return result;
        }

        // Primitives pass through unchanged
        return value;
    }
}
