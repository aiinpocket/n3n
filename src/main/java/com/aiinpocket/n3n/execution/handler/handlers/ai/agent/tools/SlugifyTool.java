package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * URL Slug 生成工具
 * 將文字轉換為 URL 友善的 slug
 */
@Component
@Slf4j
public class SlugifyTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "slugify";
    }

    @Override
    public String getName() {
        return "Slugify";
    }

    @Override
    public String getDescription() {
        return """
                將文字轉換為 URL 友善的 slug。

                功能：
                - 移除變音符號
                - 轉換為小寫
                - 將空格和特殊字元替換為連字號
                - 移除連續的連字號
                - 移除開頭和結尾的連字號

                參數：
                - text: 要轉換的文字
                - separator: 分隔符（預設 -）
                - lowercase: 是否轉小寫（預設 true）
                - maxLength: 最大長度（預設 100）

                範例：
                "Hello World!" → "hello-world"
                "Café Résumé" → "cafe-resume"
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "要轉換的文字"
                        ),
                        "separator", Map.of(
                                "type", "string",
                                "description", "分隔符",
                                "default", "-"
                        ),
                        "lowercase", Map.of(
                                "type", "boolean",
                                "description", "是否轉小寫",
                                "default", true
                        ),
                        "maxLength", Map.of(
                                "type", "integer",
                                "description", "最大長度",
                                "default", 100
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
                String separator = (String) parameters.getOrDefault("separator", "-");
                boolean lowercase = !Boolean.FALSE.equals(parameters.get("lowercase"));
                int maxLength = parameters.containsKey("maxLength")
                        ? ((Number) parameters.get("maxLength")).intValue()
                        : 100;

                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("文字不能為空");
                }

                // Security: limit separator length
                if (separator.length() > 3) {
                    return ToolResult.failure("分隔符最長 3 個字元");
                }

                // Limit max length
                maxLength = Math.min(maxLength, 500);

                String slug = slugify(text, separator, lowercase, maxLength);

                return ToolResult.success(
                        String.format("Slug 生成成功\n原文：%s\nSlug：%s", text, slug),
                        Map.of(
                                "slug", slug,
                                "original", text,
                                "separator", separator,
                                "length", slug.length()
                        )
                );

            } catch (Exception e) {
                log.error("Slugify failed", e);
                return ToolResult.failure("Slug 生成失敗: " + e.getMessage());
            }
        });
    }

    private String slugify(String text, String separator, boolean lowercase, int maxLength) {
        // Normalize unicode (remove accents)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Convert to lowercase if needed
        if (lowercase) {
            normalized = normalized.toLowerCase();
        }

        // Replace non-alphanumeric characters with separator
        String slug = normalized.replaceAll("[^a-zA-Z0-9]+", separator);

        // Remove consecutive separators
        String escapedSep = java.util.regex.Pattern.quote(separator);
        slug = slug.replaceAll(escapedSep + "+", separator);

        // Remove leading/trailing separators
        slug = slug.replaceAll("^" + escapedSep + "|" + escapedSep + "$", "");

        // Truncate to max length
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength);
            // Remove trailing separator after truncation
            slug = slug.replaceAll(escapedSep + "$", "");
        }

        return slug;
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
