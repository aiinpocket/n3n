package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UUID 生成工具
 */
@Component
@Slf4j
public class UuidTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "uuid";
    }

    @Override
    public String getName() {
        return "UUID Generator";
    }

    @Override
    public String getDescription() {
        return """
                生成 UUID（通用唯一識別碼）。

                參數：
                - count: 要生成的 UUID 數量（預設 1，最多 100）
                - format: 格式（standard=標準 36 字元, compact=無連字號 32 字元）
                - uppercase: 是否使用大寫（預設 false）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "count", Map.of(
                                "type", "integer",
                                "description", "要生成的數量",
                                "default", 1,
                                "minimum", 1,
                                "maximum", 100
                        ),
                        "format", Map.of(
                                "type", "string",
                                "enum", List.of("standard", "compact"),
                                "description", "輸出格式",
                                "default", "standard"
                        ),
                        "uppercase", Map.of(
                                "type", "boolean",
                                "description", "是否大寫",
                                "default", false
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int count = Math.min(100, Math.max(1,
                        parameters.containsKey("count") ? ((Number) parameters.get("count")).intValue() : 1));
                String format = (String) parameters.getOrDefault("format", "standard");
                boolean uppercase = Boolean.TRUE.equals(parameters.get("uppercase"));

                List<String> uuids = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String uuid = UUID.randomUUID().toString();
                    if ("compact".equals(format)) {
                        uuid = uuid.replace("-", "");
                    }
                    if (uppercase) {
                        uuid = uuid.toUpperCase();
                    }
                    uuids.add(uuid);
                }

                StringBuilder sb = new StringBuilder();
                if (count == 1) {
                    sb.append("生成的 UUID：\n").append(uuids.get(0));
                } else {
                    sb.append(String.format("生成了 %d 個 UUID：\n", count));
                    for (int i = 0; i < uuids.size(); i++) {
                        sb.append(String.format("%d. %s\n", i + 1, uuids.get(i)));
                    }
                }

                return ToolResult.success(sb.toString(), Map.of(
                        "uuids", uuids,
                        "count", count,
                        "format", format
                ));

            } catch (Exception e) {
                log.error("UUID generation failed", e);
                return ToolResult.failure("UUID 生成失敗: " + e.getMessage());
            }
        });
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
