package com.aiinpocket.n3n.execution.handler;

import java.util.Map;

/**
 * Interface for evaluating expressions like {{ $json.field }} in node configurations.
 */
public interface ExpressionEvaluator {

    /**
     * Evaluate an expression in the given context.
     *
     * Expression syntax:
     * - {{ $json }} - Current node input data
     * - {{ $json.fieldName }} - Specific field from input
     * - {{ $node["nodeName"].json }} - Output from a specific node
     * - {{ $node["nodeName"].json.field }} - Specific field from node output
     * - {{ $env.VARIABLE_NAME }} - Environment variable
     * - {{ $execution.id }} - Current execution ID
     * - {{ $workflow.id }} - Current workflow/flow ID
     * - {{ $now }} - Current timestamp
     *
     * @param expression the expression to evaluate
     * @param context the execution context
     * @return evaluated value
     */
    Object evaluate(String expression, NodeExecutionContext context);

    /**
     * Evaluate all expressions in a string, replacing {{ ... }} patterns.
     *
     * @param template string containing expressions
     * @param context the execution context
     * @return string with all expressions replaced
     */
    String evaluateTemplate(String template, NodeExecutionContext context);

    /**
     * Recursively evaluate all string values in a map that contain expressions.
     *
     * @param config map potentially containing expression strings
     * @param context the execution context
     * @return new map with all expressions evaluated
     */
    Map<String, Object> evaluateConfig(Map<String, Object> config, NodeExecutionContext context);

    /**
     * Check if a string contains any expressions.
     *
     * @param value string to check
     * @return true if contains {{ ... }} pattern
     */
    boolean containsExpression(String value);

    /**
     * Validate expression syntax without evaluating.
     *
     * @param expression the expression to validate
     * @return validation result
     */
    ValidationResult validateExpression(String expression);
}
