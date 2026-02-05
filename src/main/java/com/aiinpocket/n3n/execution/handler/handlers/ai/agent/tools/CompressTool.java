package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.*;

/**
 * 壓縮/解壓縮工具
 * 支援 GZIP 和 DEFLATE 格式
 */
@Component
@Slf4j
public class CompressTool implements AgentNodeTool {

    private static final int MAX_SIZE = 10_000_000; // 10MB

    @Override
    public String getId() {
        return "compress";
    }

    @Override
    public String getName() {
        return "Compress/Decompress";
    }

    @Override
    public String getDescription() {
        return """
                壓縮/解壓縮工具，支援 GZIP 和 DEFLATE 格式。

                操作類型：
                - compress: 壓縮文字
                - decompress: 解壓縮文字

                格式：
                - gzip: GZIP 格式（預設）
                - deflate: DEFLATE 格式

                參數：
                - data: 文字資料（壓縮時為原文，解壓縮時為 Base64 編碼的壓縮資料）
                - operation: compress 或 decompress
                - format: gzip 或 deflate
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "資料"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("compress", "decompress"),
                                "description", "操作類型",
                                "default", "compress"
                        ),
                        "format", Map.of(
                                "type", "string",
                                "enum", List.of("gzip", "deflate"),
                                "description", "壓縮格式",
                                "default", "gzip"
                        )
                ),
                "required", List.of("data")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String data = (String) parameters.get("data");
                String operation = (String) parameters.getOrDefault("operation", "compress");
                String format = (String) parameters.getOrDefault("format", "gzip");

                if (data == null || data.isEmpty()) {
                    return ToolResult.failure("資料不能為空");
                }

                return switch (operation) {
                    case "compress" -> compress(data, format);
                    case "decompress" -> decompress(data, format);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("Compression operation failed", e);
                return ToolResult.failure("壓縮操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult compress(String text, String format) {
        try {
            byte[] input = text.getBytes(StandardCharsets.UTF_8);

            if (input.length > MAX_SIZE) {
                return ToolResult.failure("資料過大，最大限制 10MB");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if ("deflate".equals(format)) {
                try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
                    dos.write(input);
                }
            } else {
                try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                    gos.write(input);
                }
            }

            byte[] compressed = baos.toByteArray();
            String result = Base64.getEncoder().encodeToString(compressed);

            double ratio = (double) compressed.length / input.length * 100;

            return ToolResult.success(
                    String.format("壓縮成功\n原始大小: %d bytes\n壓縮後: %d bytes\n壓縮率: %.1f%%\n\n結果（Base64）：\n%s",
                            input.length, compressed.length, ratio,
                            result.length() > 500 ? result.substring(0, 500) + "..." : result),
                    Map.of(
                            "compressed", result,
                            "originalSize", input.length,
                            "compressedSize", compressed.length,
                            "ratio", ratio,
                            "format", format
                    )
            );
        } catch (Exception e) {
            return ToolResult.failure("壓縮失敗: " + e.getMessage());
        }
    }

    private ToolResult decompress(String base64Data, String format) {
        try {
            byte[] compressed = Base64.getDecoder().decode(base64Data);

            if (compressed.length > MAX_SIZE) {
                return ToolResult.failure("壓縮資料過大，最大限制 10MB");
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if ("deflate".equals(format)) {
                try (InflaterInputStream iis = new InflaterInputStream(bais)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    int total = 0;
                    while ((len = iis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                        total += len;
                        // Security: limit decompressed size (zip bomb protection)
                        if (total > MAX_SIZE) {
                            return ToolResult.failure("解壓縮後資料過大，可能是 zip bomb");
                        }
                    }
                }
            } else {
                try (GZIPInputStream gis = new GZIPInputStream(bais)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    int total = 0;
                    while ((len = gis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                        total += len;
                        // Security: limit decompressed size (zip bomb protection)
                        if (total > MAX_SIZE) {
                            return ToolResult.failure("解壓縮後資料過大，可能是 zip bomb");
                        }
                    }
                }
            }

            String result = baos.toString(StandardCharsets.UTF_8);

            return ToolResult.success(
                    String.format("解壓縮成功\n壓縮大小: %d bytes\n解壓縮後: %d bytes\n\n結果：\n%s",
                            compressed.length, result.length(),
                            result.length() > 500 ? result.substring(0, 500) + "..." : result),
                    Map.of(
                            "decompressed", result,
                            "compressedSize", compressed.length,
                            "decompressedSize", result.length(),
                            "format", format
                    )
            );
        } catch (Exception e) {
            return ToolResult.failure("解壓縮失敗: " + e.getMessage());
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
