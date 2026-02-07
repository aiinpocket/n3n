package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handler for compression and encoding operations.
 *
 * Operations:
 * - gzip: Compress data using GZIP and return as Base64
 * - gunzip: Decompress GZIP data from Base64 input
 * - base64Encode: Encode text to Base64
 * - base64Decode: Decode Base64 to text
 */
@Component
@Slf4j
public class CompressionNodeHandler extends AbstractNodeHandler {

    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_INPUT_SIZE = 100 * 1024 * 1024; // 100 MB

    @Override
    public String getType() {
        return "compression";
    }

    @Override
    public String getDisplayName() {
        return "Compression";
    }

    @Override
    public String getDescription() {
        return "Compress and decompress data using GZIP, or encode/decode Base64.";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-zip";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "gzip");
        String input = getStringConfig(context, "input", "");
        String encoding = getStringConfig(context, "encoding", "UTF-8");

        // If input is empty, try from input data
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data == null) data = context.getInputData().get("input");
            if (data != null) input = data.toString();
        }

        if (input.isEmpty()) {
            return NodeExecutionResult.failure("Input data is required");
        }

        // Size check
        if (input.length() > MAX_INPUT_SIZE) {
            return NodeExecutionResult.failure(
                "Input exceeds maximum size of " + (MAX_INPUT_SIZE / 1024 / 1024) + " MB");
        }

        try {
            Map<String, Object> output = switch (operation) {
                case "gzip" -> gzipCompress(input, encoding);
                case "gunzip" -> gzipDecompress(input, encoding);
                case "base64Encode" -> base64Encode(input, encoding);
                case "base64Decode" -> base64Decode(input, encoding);
                default -> null;
            };

            if (output == null) {
                return NodeExecutionResult.failure("Unknown operation: " + operation);
            }

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for {} operation: {}", operation, e.getMessage());
            return NodeExecutionResult.failure("Invalid input: " + e.getMessage());
        } catch (IOException e) {
            log.error("Compression operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Compression failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Compression operation error: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Compression error: " + e.getMessage());
        }
    }

    /**
     * Compress input text using GZIP and return as Base64.
     */
    private Map<String, Object> gzipCompress(String input, String encoding) throws IOException {
        byte[] inputBytes = input.getBytes(resolveEncoding(encoding));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOs = new GZIPOutputStream(baos)) {
            gzipOs.write(inputBytes);
        }

        byte[] compressed = baos.toByteArray();
        String base64Result = Base64.getEncoder().encodeToString(compressed);

        double ratio = inputBytes.length > 0
            ? (1.0 - (double) compressed.length / inputBytes.length) * 100.0
            : 0.0;

        Map<String, Object> output = new HashMap<>();
        output.put("data", base64Result);
        output.put("originalSize", inputBytes.length);
        output.put("compressedSize", compressed.length);
        output.put("compressionRatio", Math.round(ratio * 100.0) / 100.0);
        return output;
    }

    /**
     * Decompress GZIP data from Base64 input.
     */
    private Map<String, Object> gzipDecompress(String base64Input, String encoding) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(base64Input.trim());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIs = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            long totalRead = 0;
            while ((len = gzipIs.read(buffer)) != -1) {
                totalRead += len;
                if (totalRead > MAX_INPUT_SIZE) {
                    throw new IOException("Decompressed data exceeds maximum size limit");
                }
                baos.write(buffer, 0, len);
            }
        }

        byte[] decompressed = baos.toByteArray();
        String text = new String(decompressed, resolveEncoding(encoding));

        Map<String, Object> output = new HashMap<>();
        output.put("data", text);
        output.put("compressedSize", compressed.length);
        output.put("decompressedSize", decompressed.length);
        return output;
    }

    /**
     * Encode text as Base64.
     */
    private Map<String, Object> base64Encode(String input, String encoding) {
        byte[] inputBytes = input.getBytes(resolveEncoding(encoding));
        String encoded = Base64.getEncoder().encodeToString(inputBytes);

        Map<String, Object> output = new HashMap<>();
        output.put("data", encoded);
        output.put("originalSize", inputBytes.length);
        output.put("encodedSize", encoded.length());
        return output;
    }

    /**
     * Decode Base64 to text.
     */
    private Map<String, Object> base64Decode(String base64Input, String encoding) {
        byte[] decoded = Base64.getDecoder().decode(base64Input.trim());
        String text = new String(decoded, resolveEncoding(encoding));

        Map<String, Object> output = new HashMap<>();
        output.put("data", text);
        output.put("decodedSize", decoded.length);
        return output;
    }

    private java.nio.charset.Charset resolveEncoding(String encoding) {
        try {
            return java.nio.charset.Charset.forName(encoding);
        } catch (Exception e) {
            log.warn("Unknown charset '{}', falling back to UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("gzip", "gunzip", "base64Encode", "base64Decode"),
                    "default", "gzip"
                ),
                "input", Map.of(
                    "type", "string",
                    "title", "Input",
                    "description", "Data to compress, decompress, or encode"
                ),
                "encoding", Map.of(
                    "type", "string",
                    "title", "Encoding",
                    "description", "Character encoding for text operations",
                    "default", "UTF-8"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "data", "type", "any", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "data", "type", "any"),
                Map.of("name", "originalSize", "type", "number"),
                Map.of("name", "compressedSize", "type", "number")
            )
        );
    }
}
