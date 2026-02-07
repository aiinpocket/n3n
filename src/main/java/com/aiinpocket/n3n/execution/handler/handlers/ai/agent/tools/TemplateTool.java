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
 * 字串模板渲染工具
 * 支援變數替換
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    // 支援 {{variable}} 和 ${variable} 格式
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
                字串模板渲染工具，支援變數替換。

                支援的變數格式：
                - {{variable}} - Mustache 風格
                - ${variable} - Shell 風格

                支援巢狀變數存取：{{user.name}}

                參數：
                - template: 模板字串
                - variables: 變數物件（JSON 格式的字串或 Map）
                - strict: 是否嚴格模式（未定義變數時報錯，預設 false）

                範例：
                template: "Hello, {{name}}! You have {{count}} messages."
                variables: {"name": "Alice", "count": 5}
                結果: "Hello, Alice! You have 5 messages."
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "template", Map.of(
                                "type", "string",
                                "description", "模板字串"
                        ),
                        "variables", Map.of(
                                "type", "string",
                                "description", "變數（JSON 格式）"
                        ),
                        "strict", Map.of(
                                "type", "boolean",
                                "description", "嚴格模式",
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
                    return ToolResult.failure("模板不能為空");
                }

                // 解析變數
                Map<String, Object> variables;
                if (variablesObj instanceof String) {
                    variables = objectMapper.readValue((String) variablesObj, new TypeReference<>() {});
                } else if (variablesObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) variablesObj;
                    variables = map;
                } else {
                    return ToolResult.failure("變數必須是 JSON 字串或 Map");
                }

                // 渲染模板
                String result = renderTemplate(template, variables, strict);

                return ToolResult.success(
                        "渲染結果：\n" + result,
                        Map.of(
                                "result", result,
                                "template", template,
                                "variableCount", variables.size()
                        )
                );

            } catch (Exception e) {
                log.error("Template rendering failed", e);
                return ToolResult.failure("模板渲染失敗: " + e.getMessage());
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
                value = matcher.group(); // 保留原始變數標記
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
