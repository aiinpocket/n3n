package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base64 編解碼工具
 */
@Component
@Slf4j
public class Base64Tool implements AgentNodeTool {

    @Override
    public String getId() {
        return "base64";
    }

    @Override
    public String getName() {
        return "Base64";
    }

    @Override
    public String getDescription() {
        return """
                Base64 編碼和解碼工具。

                操作類型：
                - encode: 將文字編碼為 Base64
                - decode: 將 Base64 解碼為文字

                參數：
                - text: 要處理的文字
                - operation: 操作類型（encode 或 decode）
                - urlSafe: 是否使用 URL 安全的 Base64（預設 false）
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
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("encode", "decode"),
                                "description", "操作類型",
                                "default", "encode"
                        ),
                        "urlSafe", Map.of(
                                "type", "boolean",
                                "description", "是否使用 URL 安全的 Base64",
                                "default", false
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
                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("文字不能為空");
                }

                String operation = (String) parameters.getOrDefault("operation", "encode");
                boolean urlSafe = Boolean.TRUE.equals(parameters.get("urlSafe"));

                String result;
                if ("encode".equals(operation)) {
                    Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
                    result = encoder.encodeToString(text.getBytes(StandardCharsets.UTF_8));
                } else if ("decode".equals(operation)) {
                    Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
                    result = new String(decoder.decode(text), StandardCharsets.UTF_8);
                } else {
                    return ToolResult.failure("不支援的操作: " + operation);
                }

                String output = String.format("Base64 %s 結果：\n%s",
                        "encode".equals(operation) ? "編碼" : "解碼", result);

                return ToolResult.success(output, Map.of(
                        "operation", operation,
                        "result", result,
                        "urlSafe", urlSafe
                ));

            } catch (IllegalArgumentException e) {
                return ToolResult.failure("Base64 解碼失敗：輸入不是有效的 Base64 字串");
            } catch (Exception e) {
                log.error("Base64 operation failed", e);
                return ToolResult.failure("Base64 操作失敗: " + e.getMessage());
            }
        });
    }

    @Override
    public String getCategory() {
        return "encoding";
    }
}
