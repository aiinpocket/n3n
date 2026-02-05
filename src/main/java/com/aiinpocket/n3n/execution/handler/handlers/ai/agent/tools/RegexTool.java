package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正規表達式工具
 * 支援匹配、提取、替換等操作
 */
@Component
@Slf4j
public class RegexTool implements AgentNodeTool {

    // 安全限制
    private static final int MAX_INPUT_LENGTH = 100000;
    private static final int MAX_MATCHES = 1000;

    @Override
    public String getId() {
        return "regex";
    }

    @Override
    public String getName() {
        return "Regex";
    }

    @Override
    public String getDescription() {
        return """
                正規表達式工具，支援多種操作：
                - match: 檢查文字是否匹配模式
                - find: 找出所有匹配的部分
                - extract: 提取匹配群組
                - replace: 替換匹配的部分
                - split: 根據模式分割文字

                參數：
                - text: 要處理的文字
                - pattern: 正規表達式模式
                - operation: 操作類型
                - replacement: 替換文字（僅用於 replace）
                - flags: 正規表達式旗標（i=忽略大小寫, m=多行, s=dotall）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "要處理的文字"
                        ),
                        "pattern", Map.of(
                                "type", "string",
                                "description", "正規表達式模式"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("match", "find", "extract", "replace", "split"),
                                "description", "操作類型",
                                "default", "find"
                        ),
                        "replacement", Map.of(
                                "type", "string",
                                "description", "替換文字（僅用於 replace 操作）"
                        ),
                        "flags", Map.of(
                                "type", "string",
                                "description", "正規表達式旗標（i, m, s）",
                                "default", ""
                        )
                ),
                "required", List.of("text", "pattern")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");
                String patternStr = (String) parameters.get("pattern");

                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("文字不能為空");
                }
                if (patternStr == null || patternStr.isEmpty()) {
                    return ToolResult.failure("正規表達式模式不能為空");
                }
                if (text.length() > MAX_INPUT_LENGTH) {
                    return ToolResult.failure("輸入文字過長（最大 " + MAX_INPUT_LENGTH + " 字元）");
                }

                String operation = (String) parameters.getOrDefault("operation", "find");
                String replacement = (String) parameters.get("replacement");
                String flags = (String) parameters.getOrDefault("flags", "");

                // 編譯正規表達式
                int patternFlags = 0;
                if (flags.contains("i")) patternFlags |= Pattern.CASE_INSENSITIVE;
                if (flags.contains("m")) patternFlags |= Pattern.MULTILINE;
                if (flags.contains("s")) patternFlags |= Pattern.DOTALL;

                Pattern pattern;
                try {
                    pattern = Pattern.compile(patternStr, patternFlags);
                } catch (PatternSyntaxException e) {
                    return ToolResult.failure("無效的正規表達式: " + e.getMessage());
                }

                return switch (operation) {
                    case "match" -> executeMatch(text, pattern);
                    case "find" -> executeFind(text, pattern);
                    case "extract" -> executeExtract(text, pattern);
                    case "replace" -> executeReplace(text, pattern, replacement);
                    case "split" -> executeSplit(text, pattern);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("Regex operation failed", e);
                return ToolResult.failure("正規表達式操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult executeMatch(String text, Pattern pattern) {
        boolean matches = pattern.matcher(text).matches();
        return ToolResult.success(
                matches ? "文字完全匹配模式" : "文字不匹配模式",
                Map.of("matches", matches)
        );
    }

    private ToolResult executeFind(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> matches = new ArrayList<>();
        int count = 0;

        while (matcher.find() && count < MAX_MATCHES) {
            matches.add(Map.of(
                    "match", matcher.group(),
                    "start", matcher.start(),
                    "end", matcher.end()
            ));
            count++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("找到 %d 個匹配：\n", matches.size()));
        for (int i = 0; i < Math.min(matches.size(), 10); i++) {
            sb.append(String.format("%d. \"%s\" (位置 %d-%d)\n",
                    i + 1, matches.get(i).get("match"),
                    matches.get(i).get("start"), matches.get(i).get("end")));
        }
        if (matches.size() > 10) {
            sb.append(String.format("... 還有 %d 個匹配\n", matches.size() - 10));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", matches.size(),
                "matches", matches
        ));
    }

    private ToolResult executeExtract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> groups = new ArrayList<>();

        while (matcher.find() && groups.size() < MAX_MATCHES) {
            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("full", matcher.group());
            for (int i = 1; i <= matcher.groupCount(); i++) {
                groupMap.put("group" + i, matcher.group(i));
            }
            groups.add(groupMap);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("提取到 %d 個匹配群組：\n", groups.size()));
        for (int i = 0; i < Math.min(groups.size(), 5); i++) {
            sb.append(String.format("%d. %s\n", i + 1, groups.get(i)));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", groups.size(),
                "groups", groups
        ));
    }

    private ToolResult executeReplace(String text, Pattern pattern, String replacement) {
        if (replacement == null) {
            return ToolResult.failure("replace 操作需要提供 replacement 參數");
        }
        String result = pattern.matcher(text).replaceAll(replacement);
        return ToolResult.success(
                "替換結果：\n" + result,
                Map.of("result", result, "original", text)
        );
    }

    private ToolResult executeSplit(String text, Pattern pattern) {
        String[] parts = pattern.split(text);
        List<String> partsList = List.of(parts);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("分割成 %d 個部分：\n", parts.length));
        for (int i = 0; i < Math.min(parts.length, 10); i++) {
            sb.append(String.format("%d. \"%s\"\n", i + 1, parts[i]));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", parts.length,
                "parts", partsList
        ));
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
