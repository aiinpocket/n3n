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
 * Compression/decompression tool
 * Supports GZIP and DEFLATE formats
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
                Compression/decompression tool, supports GZIP and DEFLATE formats.

                Operations:
                - compress: Compress text
                - decompress: Decompress text

                Formats:
                - gzip: GZIP format (default)
                - deflate: DEFLATE format

                Parameters:
                - data: Text data (plain text for compress, Base64-encoded compressed data for decompress)
                - operation: compress or decompress
                - format: gzip or deflate
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "Data"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("compress", "decompress"),
                                "description", "Operation type",
                                "default", "compress"
                        ),
                        "format", Map.of(
                                "type", "string",
                                "enum", List.of("gzip", "deflate"),
                                "description", "Compression format",
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
                    return ToolResult.failure("Data cannot be empty");
                }

                return switch (operation) {
                    case "compress" -> compress(data, format);
                    case "decompress" -> decompress(data, format);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("Compression operation failed", e);
                return ToolResult.failure("Compression operation failed");
            }
        });
    }

    private ToolResult compress(String text, String format) {
        try {
            byte[] input = text.getBytes(StandardCharsets.UTF_8);

            if (input.length > MAX_SIZE) {
                return ToolResult.failure("Data too large, maximum 10MB");
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
                    String.format("Compression successful\nOriginal size: %d bytes\nCompressed: %d bytes\nCompression ratio: %.1f%%\n\nResult (Base64):\n%s",
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
            return ToolResult.failure("Compression failed");
        }
    }

    private ToolResult decompress(String base64Data, String format) {
        try {
            byte[] compressed = Base64.getDecoder().decode(base64Data);

            if (compressed.length > MAX_SIZE) {
                return ToolResult.failure("Compressed data too large, maximum 10MB");
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
                            return ToolResult.failure("Decompressed data too large, possible zip bomb");
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
                            return ToolResult.failure("Decompressed data too large, possible zip bomb");
                        }
                    }
                }
            }

            String result = baos.toString(StandardCharsets.UTF_8);

            return ToolResult.success(
                    String.format("Decompression successful\nCompressed size: %d bytes\nDecompressed: %d bytes\n\nResult:\n%s",
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
            return ToolResult.failure("Decompression failed");
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
