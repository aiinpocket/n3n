package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 隨機數生成工具
 */
@Component
@Slf4j
public class RandomTool implements AgentNodeTool {

    private static final SecureRandom random = new SecureRandom();

    @Override
    public String getId() {
        return "random";
    }

    @Override
    public String getName() {
        return "Random Generator";
    }

    @Override
    public String getDescription() {
        return """
                生成隨機數或隨機字串。

                操作類型：
                - number: 生成隨機整數
                - float: 生成隨機浮點數
                - string: 生成隨機字串
                - password: 生成安全密碼
                - pick: 從列表中隨機選取

                參數：
                - type: 操作類型
                - min: 最小值（用於 number/float）
                - max: 最大值（用於 number/float）
                - length: 字串長度（用於 string/password）
                - count: 生成數量（預設 1）
                - items: 選項列表（用於 pick，逗號分隔）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of("number", "float", "string", "password", "pick"),
                                "description", "隨機類型",
                                "default", "number"
                        ),
                        "min", Map.of(
                                "type", "number",
                                "description", "最小值",
                                "default", 0
                        ),
                        "max", Map.of(
                                "type", "number",
                                "description", "最大值",
                                "default", 100
                        ),
                        "length", Map.of(
                                "type", "integer",
                                "description", "字串長度",
                                "default", 16
                        ),
                        "count", Map.of(
                                "type", "integer",
                                "description", "生成數量",
                                "default", 1
                        ),
                        "items", Map.of(
                                "type", "string",
                                "description", "選項列表（逗號分隔）"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String type = (String) parameters.getOrDefault("type", "number");
                int count = Math.min(100, Math.max(1,
                        parameters.containsKey("count") ? ((Number) parameters.get("count")).intValue() : 1));

                List<Object> results = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    results.add(generateRandom(type, parameters));
                }

                StringBuilder sb = new StringBuilder();
                if (count == 1) {
                    sb.append("生成的隨機值：").append(results.get(0));
                } else {
                    sb.append(String.format("生成了 %d 個隨機值：\n", count));
                    for (int i = 0; i < results.size(); i++) {
                        sb.append(String.format("%d. %s\n", i + 1, results.get(i)));
                    }
                }

                return ToolResult.success(sb.toString(), Map.of(
                        "results", results,
                        "type", type,
                        "count", count
                ));

            } catch (Exception e) {
                log.error("Random generation failed", e);
                return ToolResult.failure("隨機生成失敗: " + e.getMessage());
            }
        });
    }

    private Object generateRandom(String type, Map<String, Object> parameters) {
        return switch (type) {
            case "number" -> {
                int min = parameters.containsKey("min") ? ((Number) parameters.get("min")).intValue() : 0;
                int max = parameters.containsKey("max") ? ((Number) parameters.get("max")).intValue() : 100;
                yield min + random.nextInt(max - min + 1);
            }
            case "float" -> {
                double min = parameters.containsKey("min") ? ((Number) parameters.get("min")).doubleValue() : 0.0;
                double max = parameters.containsKey("max") ? ((Number) parameters.get("max")).doubleValue() : 1.0;
                yield min + random.nextDouble() * (max - min);
            }
            case "string" -> {
                int length = Math.min(256, Math.max(1,
                        parameters.containsKey("length") ? ((Number) parameters.get("length")).intValue() : 16));
                String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                yield sb.toString();
            }
            case "password" -> {
                int length = Math.min(128, Math.max(8,
                        parameters.containsKey("length") ? ((Number) parameters.get("length")).intValue() : 16));
                String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                yield sb.toString();
            }
            case "pick" -> {
                String itemsStr = (String) parameters.get("items");
                if (itemsStr == null || itemsStr.isBlank()) {
                    yield "錯誤：需要提供 items 參數";
                }
                String[] items = itemsStr.split(",");
                yield items[random.nextInt(items.length)].trim();
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
