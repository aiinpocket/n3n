package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 雜湊計算工具
 * 支援 MD5, SHA-1, SHA-256, SHA-512 等雜湊演算法
 */
@Component
@Slf4j
public class HashTool implements AgentNodeTool {

    private static final List<String> SUPPORTED_ALGORITHMS = List.of(
            "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512"
    );

    @Override
    public String getId() {
        return "hash";
    }

    @Override
    public String getName() {
        return "Hash";
    }

    @Override
    public String getDescription() {
        return """
                計算文字或資料的雜湊值。支援的演算法：
                - MD5: 128 位元雜湊（不建議用於安全用途）
                - SHA-1: 160 位元雜湊（不建議用於安全用途）
                - SHA-256: 256 位元雜湊（推薦）
                - SHA-384: 384 位元雜湊
                - SHA-512: 512 位元雜湊

                參數：
                - text: 要計算雜湊的文字
                - algorithm: 雜湊演算法（預設 SHA-256）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "要計算雜湊的文字"
                        ),
                        "algorithm", Map.of(
                                "type", "string",
                                "enum", SUPPORTED_ALGORITHMS,
                                "description", "雜湊演算法",
                                "default", "SHA-256"
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
                if (text == null) {
                    return ToolResult.failure("文字不能為空");
                }

                String algorithm = (String) parameters.getOrDefault("algorithm", "SHA-256");
                if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
                    return ToolResult.failure("不支援的演算法: " + algorithm);
                }

                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
                String hexHash = HexFormat.of().formatHex(hash);

                String output = String.format("雜湊結果 (%s):\n%s", algorithm, hexHash);

                return ToolResult.success(output, Map.of(
                        "algorithm", algorithm,
                        "hash", hexHash,
                        "length", hexHash.length()
                ));

            } catch (Exception e) {
                log.error("Hash calculation failed", e);
                return ToolResult.failure("雜湊計算失敗: " + e.getMessage());
            }
        });
    }

    @Override
    public String getCategory() {
        return "crypto";
    }
}
