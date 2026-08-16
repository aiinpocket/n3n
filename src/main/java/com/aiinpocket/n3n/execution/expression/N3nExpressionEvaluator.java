package com.aiinpocket.n3n.execution.expression;

import com.aiinpocket.n3n.execution.handler.ExpressionEvaluator;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * $now / $today 採用的時區。容器多半跑在 UTC，直接用系統時區會讓
     * 「今天」的日期字串對台北使用者差一天。與排程共用同一個設定鍵。
     */
    @org.springframework.beans.factory.annotation.Value("${n3n.default-timezone:Asia/Taipei}")
    private String defaultTimezone = "Asia/Taipei";

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");
    private static final Pattern FIELD_PATH_PATTERN = Pattern.compile("^\\$([a-zA-Z_][a-zA-Z0-9_]*)(?:\\.(.+))?$");
    // 同時接受雙引號與單引號（AI 生成的流程慣用 $node['名稱']）
    private static final Pattern NODE_REF_PATTERN =
        Pattern.compile("^\\$node\\[[\"']([^\"']+)[\"']\\]\\.(?:json|output)(?:\\.(.+))?$");
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$env\\.([a-zA-Z_][a-zA-Z0-9_]*)$");
    private static final Pattern NOW_FORMAT_PATTERN =
        Pattern.compile("^\\$(?:now|today)\\.format\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)$");

    /**
     * $now.year() / month() / day() / quarter() / hour() / minute()。
     *
     * <p>模型想取「本季」時會寫成 {@code Math.ceil($now.month()/3)} 這種算式，
     * 而本引擎不做算術，整段會解析失敗變空字串。直接提供 quarter() 等現成函式，
     * 模型就有正確工具可用，不必自己算。
     */
    private static final Pattern NOW_PART_PATTERN =
        Pattern.compile("^\\$(?:now|today)\\.(year|month|day|date|quarter|hour|minute|second)\\(\\s*\\)$");

    private static final ObjectMapper TEMPLATE_MAPPER = new ObjectMapper();

    /**
     * 範本插值時 Map/List 序列化為 JSON（而非 Java toString），
     * 讓 {{$json.someObject}} 進到 prompt 的內容是可讀的 JSON。
     */
    private static String stringifyForTemplate(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return TEMPLATE_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value.toString();
    }

    @Override
    public Object evaluate(String expression, NodeExecutionContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        String trimmed = stripLeadingEquals(expression.trim());

        // Remove surrounding {{ }} if present
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 2).trim();
        }

        return evaluateExpression(trimmed, context);
    }

    /**
     * 剝掉 n8n 風格的 {@code =} 前綴（{@code ={{ $json.x }}}）。
     *
     * <p>AI 生成流程時很容易寫成這種形式。不處理的話整個值會被當成字面字串，
     * 不會報錯卻輸出一串 {@code ={{ ... }}}——是最難查的那種靜默錯誤。
     */
    private static String stripLeadingEquals(String value) {
        if (value.length() > 1 && value.charAt(0) == '=' && value.startsWith("={{")) {
            return value.substring(1).trim();
        }
        return value;
    }

    @Override
    public String evaluateTemplate(String template, NodeExecutionContext context) {
        if (template == null) {
            return null;
        }
        // 同樣容忍 ={{ ... }}。只在確實是這個前綴時剝除，不動其餘內容的前後空白
        String normalized = template.startsWith("={{") ? template.substring(1) : template;
        if (!containsExpression(normalized)) {
            return normalized;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = EXPRESSION_PATTERN.matcher(normalized);

        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object value = evaluateExpression(expr, context);
            // 引擎不做算術，模型寫的 {{ Math.ceil($now.month()/3) }} 這類算式會解析不出來。
            // 過去這種情況靜默插入空字串，使用者只看到欄位莫名少了一段；保留原文才看得出
            // 是哪個表達式沒生效，背景驗證的輸出樣本也才抓得到。
            String replacement = value == null && shouldKeepUnresolved(expr)
                ? matcher.group(0)
                : stringifyForTemplate(value);
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
            trimmed.startsWith("$now") ||
            trimmed.startsWith("$today") ||
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

        // 寬容支援 AI 常見寫法 $now.format('YYYY-MM-DD')（n8n/moment 風格 token）
        Matcher nowFormatMatcher = NOW_FORMAT_PATTERN.matcher(expr);
        if (nowFormatMatcher.matches()) {
            return formatNow(nowFormatMatcher.group(1));
        }
        if ("$today".equals(expr)) {
            return formatNow("YYYY-MM-DD");
        }

        Matcher nowPartMatcher = NOW_PART_PATTERN.matcher(expr);
        if (nowPartMatcher.matches()) {
            return nowPart(nowPartMatcher.group(1));
        }

        // Check for $timestamp
        if ("$timestamp".equals(expr)) {
            return System.currentTimeMillis();
        }

        // Check for $env.VARIABLE — restricted to N3N_USER_ prefix for security
        Matcher envMatcher = ENV_PATTERN.matcher(expr);
        if (envMatcher.matches()) {
            String envVar = envMatcher.group(1);
            if (!envVar.startsWith("N3N_USER_")) {
                log.warn("Blocked access to environment variable '{}' — only N3N_USER_* variables are allowed", envVar);
                return null;
            }
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

            // 寬容支援 AI 常見寫法 $node2.output.field / $node2.json.field
            // （previousOutputs 以節點 id 為 key，如 "2"）
            if (variable.startsWith("node") && variable.length() > 4) {
                Object nodeOutput = resolveNodeOutputByRef(variable, context);
                if (nodeOutput != null) {
                    if (fieldPath == null || fieldPath.equals("output") || fieldPath.equals("json")) {
                        return nodeOutput;
                    }
                    String adjusted = fieldPath;
                    if (adjusted.startsWith("output.")) {
                        adjusted = adjusted.substring("output.".length());
                    } else if (adjusted.startsWith("json.")) {
                        adjusted = adjusted.substring("json.".length());
                    }
                    if (nodeOutput instanceof Map) {
                        return getNestedValue((Map<?, ?>) nodeOutput, adjusted);
                    }
                    return null;
                }
            }

            Object rootValue = getRootValue(variable, context);
            if (fieldPath != null && rootValue instanceof Map) {
                return getNestedValue((Map<?, ?>) rootValue, fieldPath);
            }
            return rootValue;
        }

        // 用 warn：這代表流程裡有一段設定實際上沒有生效，維運看得到才查得出來
        log.warn("Unsupported expression, left as-is in output: {}", expr);
        return null;
    }

    /**
     * 以 $node2 / $node_abc 形式解析上游節點輸出：
     * 先試去掉 "node" 前綴的 id（"2"），再試完整名稱（"node2"）。
     */
    private Object resolveNodeOutputByRef(String variable, NodeExecutionContext context) {
        Map<String, Object> previousOutputs = context.getPreviousOutputs();
        if (previousOutputs == null) {
            return null;
        }
        String nodeKey = variable.substring("node".length());
        if (previousOutputs.containsKey(nodeKey)) {
            return previousOutputs.get(nodeKey);
        }
        return previousOutputs.get(variable);
    }

    /**
     * 以 moment/n8n 風格 token 格式化目前時間（台北時區慣用的 YYYY-MM-DD 等）。
     * 僅轉換常見 token；其餘字元原樣保留。
     */
    /**
     * 這個表達式是不是引擎「認得的形式」。
     *
     * <p>用來分辨兩種 null：認得的形式取不到值（例如 {@code $json.notThere}）是正常的，
     * 該插入空字串；完全不認得的形式（例如 {@code Math.ceil(...)}）代表寫法本身不支援，
     * 插空字串只會讓錯誤消失在輸出裡。
     */
    private static boolean isKnownExpressionForm(String expr) {
        if (expr == null || !expr.startsWith("$")) {
            return false;
        }
        return expr.startsWith("$json")
            || expr.startsWith("$node")
            || expr.startsWith("$env.")
            || expr.startsWith("$execution.")
            || expr.startsWith("$workflow.")
            || expr.startsWith("$now")
            || expr.startsWith("$today")
            || expr.equals("$timestamp")
            || expr.startsWith("$input");
    }

    /**
     * 取不到值時該保留原文（而不是插入空字串）的情況。
     *
     * <p>兩種：完全不認得的寫法，以及明確跨節點引用 {@code $node[...]} 卻拿不到資料。
     * 後者幾乎都是接線或欄位名錯了——曾經發生 aiChat 的提示詞寫
     * 「請分析這份財報：{{ $node["2"].json.data }}」，該欄位不存在被換成空字串，
     * 模型收到一句沒有資料的指令便自行寫了一篇通用教學，流程還一路顯示成功，
     * 使用者打開產出才發現是廢話。保留原文至少讓這件事在輸出裡看得見。
     *
     * <p>{@code $json.x} 不在此列：它常被用在可有可無的欄位上，一律保留原文會製造雜訊。
     */
    private static boolean shouldKeepUnresolved(String expr) {
        if (!isKnownExpressionForm(expr)) {
            return true;
        }
        return expr != null && expr.startsWith("$node");
    }

    /** 取目前時間的單一部位；時區與 formatNow 一致，避免同一流程裡兩種日期對不起來。 */
    private Object nowPart(String part) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone());
        return switch (part) {
            case "year" -> now.getYear();
            case "month" -> now.getMonthValue();
            case "day", "date" -> now.getDayOfMonth();
            case "quarter" -> (now.getMonthValue() - 1) / 3 + 1;
            case "hour" -> now.getHour();
            case "minute" -> now.getMinute();
            case "second" -> now.getSecond();
            default -> null;
        };
    }

    private String formatNow(String momentPattern) {
        String javaPattern = momentPattern
            .replace("YYYY", "yyyy")
            .replace("DD", "dd")
            .replace("A", "a");
        try {
            return java.time.ZonedDateTime.now(zone())
                .format(DateTimeFormatter.ofPattern(javaPattern));
        } catch (Exception e) {
            log.warn("Unsupported date format pattern '{}': {}", momentPattern, e.getMessage());
            return Instant.now().toString();
        }
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
            } else if (current instanceof String && ("url".equals(part) || "href".equals(part))) {
                // 寬容處理 AI 生成的 n8n 風格路徑（如 images[0].url）：
                // 值本身已是 URL 字串時，取 .url 直接回傳字串本身
                continue;
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

    /** 設定的時區；設定值無效時退回系統時區，不讓表達式整個壞掉。 */
    private java.time.ZoneId zone() {
        try {
            return java.time.ZoneId.of(defaultTimezone);
        } catch (Exception e) {
            log.warn("Invalid n3n.default-timezone '{}', falling back to system zone", defaultTimezone);
            return java.time.ZoneId.systemDefault();
        }
    }
}
