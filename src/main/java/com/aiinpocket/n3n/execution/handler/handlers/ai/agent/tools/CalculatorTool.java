package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Math calculation tool
 *
 * Allows AI Agent to perform math calculations, supporting:
 * - Basic arithmetic operations
 * - Math functions (sqrt, pow, sin, cos, tan, log, etc.)
 * - Percentage calculations
 * - Unit conversions
 */
@Component
@Slf4j
public class CalculatorTool implements AgentNodeTool {

    // Safe math expression pattern - only allows digits, operators, and math functions
    private static final Pattern SAFE_EXPRESSION_PATTERN = Pattern.compile(
            "^([0-9+\\-*/().,%\\s]+|sqrt|pow|sin|cos|tan|asin|acos|atan|log|log10|exp|abs|ceil|floor|round|min|max|PI|E)*$",
            Pattern.CASE_INSENSITIVE
    );

    // Forbidden keywords to prevent code injection
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "java", "class", "import", "new", "void", "return", "if", "for", "while",
            "try", "catch", "throw", "System", "Runtime", "Process", "exec", "eval"
    );

    @Override
    public String getId() {
        return "calculator";
    }

    @Override
    public String getName() {
        return "Calculator";
    }

    @Override
    public String getDescription() {
        return """
                Perform math calculations. Supports:
                - Basic operations: add(+), subtract(-), multiply(*), divide(/), modulo(%), power(^ or **)
                - Math functions: sqrt, pow, abs, round
                - Trigonometric functions: sin, cos, tan, asin, acos, atan (in radians)
                - Logarithmic functions: log (natural), log10 (common)
                - Constants: PI, E

                Examples:
                - "2 + 3 * 4" = 14
                - "sqrt(16)" = 4
                - "pow(2, 10)" = 1024
                - "sin(PI/2)" = 1
                - "100 * 0.15" (calculate 15%) = 15
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "Math expression to evaluate"
                        ),
                        "precision", Map.of(
                                "type", "integer",
                                "description", "Decimal precision (default 10)",
                                "default", 10
                        )
                ),
                "required", List.of("expression")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String expression = (String) parameters.get("expression");
                if (expression == null || expression.isBlank()) {
                    return ToolResult.failure("Expression cannot be empty");
                }

                int precision = parameters.containsKey("precision")
                        ? ((Number) parameters.get("precision")).intValue()
                        : 10;

                // Security check
                String sanitized = sanitizeExpression(expression);
                if (sanitized == null) {
                    return ToolResult.failure("Expression contains disallowed characters or keywords");
                }

                log.debug("Calculating expression: {}", sanitized);

                // Calculate result
                Object result = evaluateExpression(sanitized);

                // Format result
                String formattedResult;
                if (result instanceof Double doubleVal) {
                    if (Double.isNaN(doubleVal) || Double.isInfinite(doubleVal)) {
                        return ToolResult.failure("Invalid calculation result (NaN or Infinity)");
                    }
                    BigDecimal bd = BigDecimal.valueOf(doubleVal)
                            .setScale(precision, RoundingMode.HALF_UP)
                            .stripTrailingZeros();
                    formattedResult = bd.toPlainString();
                } else {
                    formattedResult = String.valueOf(result);
                }

                String output = String.format("%s = %s", expression, formattedResult);

                return ToolResult.success(output, Map.of(
                        "expression", expression,
                        "result", result,
                        "formatted_result", formattedResult
                ));

            } catch (IllegalArgumentException e) {
                log.error("Calculation syntax error", e);
                return ToolResult.failure("Calculation syntax error");
            } catch (Exception e) {
                log.error("Calculation failed", e);
                return ToolResult.failure("Calculation failed");
            }
        });
    }

    /**
     * Sanitize and validate expression
     */
    private String sanitizeExpression(String expression) {
        String normalized = expression.trim()
                .replace("^", "**")    // Support ^ as power operator
                .replace("**", "pow"); // Convert to pow function

        // Check forbidden keywords
        String lowerExpr = normalized.toLowerCase();
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lowerExpr.contains(keyword.toLowerCase())) {
                log.warn("Blocked forbidden keyword in expression: {}", keyword);
                return null;
            }
        }

        // Convert math functions to JavaScript Math object
        normalized = normalized
                .replaceAll("(?i)\\bsqrt\\(", "Math.sqrt(")
                .replaceAll("(?i)\\bpow\\(", "Math.pow(")
                .replaceAll("(?i)\\babs\\(", "Math.abs(")
                .replaceAll("(?i)\\bsin\\(", "Math.sin(")
                .replaceAll("(?i)\\bcos\\(", "Math.cos(")
                .replaceAll("(?i)\\btan\\(", "Math.tan(")
                .replaceAll("(?i)\\basin\\(", "Math.asin(")
                .replaceAll("(?i)\\bacos\\(", "Math.acos(")
                .replaceAll("(?i)\\batan\\(", "Math.atan(")
                .replaceAll("(?i)\\blog\\(", "Math.log(")
                .replaceAll("(?i)\\blog10\\(", "Math.log10(")
                .replaceAll("(?i)\\bexp\\(", "Math.exp(")
                .replaceAll("(?i)\\bceil\\(", "Math.ceil(")
                .replaceAll("(?i)\\bfloor\\(", "Math.floor(")
                .replaceAll("(?i)\\bround\\(", "Math.round(")
                .replaceAll("(?i)\\bmin\\(", "Math.min(")
                .replaceAll("(?i)\\bmax\\(", "Math.max(")
                .replaceAll("(?i)\\bPI\\b", "Math.PI")
                .replaceAll("(?i)\\bE\\b", "Math.E");

        return normalized;
    }

    /**
     * Evaluate expression using a safe pure-Java parser.
     * Security: JavaScript ScriptEngine is disabled to prevent code injection attacks.
     */
    private Object evaluateExpression(String expression) {
        // Use pure-Java safe parser, no JavaScript engine
        return evaluateSimple(expression);
    }

    /**
     * Simple calculation (fallback when no JavaScript engine is available)
     */
    private double evaluateSimple(String expression) {
        // Use recursive descent parser for simple expressions
        return new ExpressionParser(expression).parse();
    }

    /**
     * Simple expression parser
     */
    private static class ExpressionParser {
        private final String expression;
        private int pos = 0;

        public ExpressionParser(String expression) {
            this.expression = expression.replaceAll("\\s+", "");
        }

        public double parse() {
            double result = parseAddSubtract();
            if (pos < expression.length()) {
                throw new IllegalArgumentException("Unexpected character: " + expression.charAt(pos));
            }
            return result;
        }

        private double parseAddSubtract() {
            double result = parseMultiplyDivide();
            while (pos < expression.length()) {
                char op = expression.charAt(pos);
                if (op == '+') {
                    pos++;
                    result += parseMultiplyDivide();
                } else if (op == '-') {
                    pos++;
                    result -= parseMultiplyDivide();
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseMultiplyDivide() {
            double result = parseUnary();
            while (pos < expression.length()) {
                char op = expression.charAt(pos);
                if (op == '*') {
                    pos++;
                    result *= parseUnary();
                } else if (op == '/') {
                    pos++;
                    result /= parseUnary();
                } else if (op == '%') {
                    pos++;
                    result %= parseUnary();
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseUnary() {
            if (pos < expression.length() && expression.charAt(pos) == '-') {
                pos++;
                return -parsePrimary();
            }
            if (pos < expression.length() && expression.charAt(pos) == '+') {
                pos++;
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (pos < expression.length() && expression.charAt(pos) == '(') {
                pos++;
                double result = parseAddSubtract();
                if (pos >= expression.length() || expression.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                pos++;
                return result;
            }

            // Parse function
            if (pos < expression.length() && Character.isLetter(expression.charAt(pos))) {
                return parseFunction();
            }

            return parseNumber();
        }

        private double parseFunction() {
            StringBuilder funcName = new StringBuilder();
            while (pos < expression.length() && Character.isLetter(expression.charAt(pos))) {
                funcName.append(expression.charAt(pos++));
            }

            String name = funcName.toString();

            // Constants
            if (name.equals("Math") && pos < expression.length() && expression.charAt(pos) == '.') {
                pos++;
                return parseMathConstantOrFunction();
            }

            throw new IllegalArgumentException("Unknown function: " + name);
        }

        private double parseMathConstantOrFunction() {
            StringBuilder name = new StringBuilder();
            while (pos < expression.length() && Character.isLetterOrDigit(expression.charAt(pos))) {
                name.append(expression.charAt(pos++));
            }

            String funcName = name.toString();

            // Constants
            if (funcName.equals("PI")) return Math.PI;
            if (funcName.equals("E")) return Math.E;

            // Functions require parentheses
            if (pos >= expression.length() || expression.charAt(pos) != '(') {
                throw new IllegalArgumentException("Expected '(' after " + funcName);
            }
            pos++;

            double arg = parseAddSubtract();

            // Handle two-argument functions
            double arg2 = 0;
            if (funcName.equals("pow") || funcName.equals("min") || funcName.equals("max")) {
                if (pos < expression.length() && expression.charAt(pos) == ',') {
                    pos++;
                    arg2 = parseAddSubtract();
                }
            }

            if (pos >= expression.length() || expression.charAt(pos) != ')') {
                throw new IllegalArgumentException("Missing closing parenthesis for " + funcName);
            }
            pos++;

            return switch (funcName) {
                case "sqrt" -> Math.sqrt(arg);
                case "abs" -> Math.abs(arg);
                case "sin" -> Math.sin(arg);
                case "cos" -> Math.cos(arg);
                case "tan" -> Math.tan(arg);
                case "asin" -> Math.asin(arg);
                case "acos" -> Math.acos(arg);
                case "atan" -> Math.atan(arg);
                case "log" -> Math.log(arg);
                case "log10" -> Math.log10(arg);
                case "exp" -> Math.exp(arg);
                case "ceil" -> Math.ceil(arg);
                case "floor" -> Math.floor(arg);
                case "round" -> Math.round(arg);
                case "pow" -> Math.pow(arg, arg2);
                case "min" -> Math.min(arg, arg2);
                case "max" -> Math.max(arg, arg2);
                default -> throw new IllegalArgumentException("Unknown function: " + funcName);
            };
        }

        private double parseNumber() {
            int startPos = pos;
            while (pos < expression.length() &&
                    (Character.isDigit(expression.charAt(pos)) || expression.charAt(pos) == '.')) {
                pos++;
            }
            if (startPos == pos) {
                throw new IllegalArgumentException("Expected number at position " + pos);
            }
            return Double.parseDouble(expression.substring(startPos, pos));
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
