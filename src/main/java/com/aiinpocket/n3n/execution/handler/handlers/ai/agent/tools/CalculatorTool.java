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
 * 數學計算工具
 *
 * 允許 AI Agent 執行數學計算，支援：
 * - 基本算術運算
 * - 數學函數 (sqrt, pow, sin, cos, tan, log, etc.)
 * - 百分比計算
 * - 單位轉換
 */
@Component
@Slf4j
public class CalculatorTool implements AgentNodeTool {

    // 安全的數學表達式模式 - 只允許數字、運算符和數學函數
    private static final Pattern SAFE_EXPRESSION_PATTERN = Pattern.compile(
            "^([0-9+\\-*/().,%\\s]+|sqrt|pow|sin|cos|tan|asin|acos|atan|log|log10|exp|abs|ceil|floor|round|min|max|PI|E)*$",
            Pattern.CASE_INSENSITIVE
    );

    // 禁止的關鍵字，防止代碼注入
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
                執行數學計算。支援：
                - 基本運算：加(+)、減(-)、乘(*)、除(/)、餘數(%)、次方(^或**)
                - 數學函數：sqrt(開根號)、pow(次方)、abs(絕對值)、round(四捨五入)
                - 三角函數：sin、cos、tan、asin、acos、atan（使用弧度）
                - 對數函數：log(自然對數)、log10(常用對數)
                - 常數：PI、E

                範例：
                - "2 + 3 * 4" = 14
                - "sqrt(16)" = 4
                - "pow(2, 10)" = 1024
                - "sin(PI/2)" = 1
                - "100 * 0.15" (計算 15%) = 15
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "要計算的數學表達式"
                        ),
                        "precision", Map.of(
                                "type", "integer",
                                "description", "小數位數精度（預設 10）",
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
                    return ToolResult.failure("表達式不能為空");
                }

                int precision = parameters.containsKey("precision")
                        ? ((Number) parameters.get("precision")).intValue()
                        : 10;

                // 安全檢查
                String sanitized = sanitizeExpression(expression);
                if (sanitized == null) {
                    return ToolResult.failure("表達式包含不允許的字符或關鍵字");
                }

                log.debug("Calculating expression: {}", sanitized);

                // 計算結果
                Object result = evaluateExpression(sanitized);

                // 格式化結果
                String formattedResult;
                if (result instanceof Double doubleVal) {
                    if (Double.isNaN(doubleVal) || Double.isInfinite(doubleVal)) {
                        return ToolResult.failure("計算結果無效（NaN 或無限大）");
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
                return ToolResult.failure("計算語法錯誤: " + e.getMessage());
            } catch (Exception e) {
                log.error("Calculation failed", e);
                return ToolResult.failure("計算失敗: " + e.getMessage());
            }
        });
    }

    /**
     * 清理和驗證表達式
     */
    private String sanitizeExpression(String expression) {
        String normalized = expression.trim()
                .replace("^", "**")    // 支援 ^ 作為次方
                .replace("**", "pow"); // 轉換為 pow 函數

        // 檢查禁止的關鍵字
        String lowerExpr = normalized.toLowerCase();
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lowerExpr.contains(keyword.toLowerCase())) {
                log.warn("Blocked forbidden keyword in expression: {}", keyword);
                return null;
            }
        }

        // 轉換數學函數為 JavaScript Math 對象
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
     * 使用安全的純 Java 解析器計算表達式
     * 安全考量：禁用 JavaScript ScriptEngine 以防止代碼注入攻擊
     */
    private Object evaluateExpression(String expression) {
        // 使用純 Java 實現的安全解析器，不使用 JavaScript 引擎
        return evaluateSimple(expression);
    }

    /**
     * 簡單計算（當沒有 JavaScript 引擎時）
     */
    private double evaluateSimple(String expression) {
        // 使用遞迴下降解析器處理簡單表達式
        return new ExpressionParser(expression).parse();
    }

    /**
     * 簡單的表達式解析器
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

            // 解析函數
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

            // 常數
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

            // 常數
            if (funcName.equals("PI")) return Math.PI;
            if (funcName.equals("E")) return Math.E;

            // 函數需要括號
            if (pos >= expression.length() || expression.charAt(pos) != '(') {
                throw new IllegalArgumentException("Expected '(' after " + funcName);
            }
            pos++;

            double arg = parseAddSubtract();

            // 處理雙參數函數
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
