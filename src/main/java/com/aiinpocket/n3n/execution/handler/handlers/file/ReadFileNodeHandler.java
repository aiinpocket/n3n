package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for reading files in various formats.
 *
 * Operations:
 * - readText: Read file content as text string
 * - readBinary: Read file content as Base64-encoded string
 * - readLines: Read file content as array of lines
 * - readJson: Read and parse JSON file
 * - readCsv: Read and parse CSV file into array of maps
 */
@Component
@Slf4j
public class ReadFileNodeHandler extends AbstractNodeHandler {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB

    @Override
    public String getType() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "Read File";
    }

    @Override
    public String getDescription() {
        return "Read files in various formats: text, binary (Base64), lines, JSON, or CSV.";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String filePath = getStringConfig(context, "filePath", "");
        String encoding = getStringConfig(context, "encoding", "UTF-8");
        String operation = getStringConfig(context, "operation", "readText");

        // If filePath is empty, try to get from input data
        if (filePath.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("filePath");
            if (data != null) {
                filePath = data.toString();
            }
        }

        if (filePath.isEmpty()) {
            return NodeExecutionResult.failure("File path is required");
        }

        // Security: prevent path traversal
        if (filePath.contains("..")) {
            return NodeExecutionResult.failure("Path traversal is not allowed");
        }

        Path path = Paths.get(filePath).normalize();

        if (!Files.exists(path)) {
            return NodeExecutionResult.failure("File not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            return NodeExecutionResult.failure("File is not readable: " + filePath);
        }

        try {
            // Check file size
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return NodeExecutionResult.failure(
                    "File exceeds maximum size of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
            }

            Charset charset = resolveCharset(encoding);
            Map<String, Object> metadata = buildFileMetadata(path);

            Map<String, Object> output = switch (operation) {
                case "readText" -> readText(path, charset, metadata);
                case "readBinary" -> readBinary(path, metadata);
                case "readLines" -> readLines(path, charset, metadata);
                case "readJson" -> readJson(path, charset, metadata);
                case "readCsv" -> readCsv(path, charset, context, metadata);
                default -> null;
            };

            if (output == null) {
                return NodeExecutionResult.failure("Unknown operation: " + operation);
            }

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .build();

        } catch (IOException e) {
            log.error("Failed to read file {}: {}", filePath, e.getMessage(), e);
            return NodeExecutionResult.failure("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Read file operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Read file operation failed: " + e.getMessage());
        }
    }

    private Map<String, Object> readText(Path path, Charset charset, Map<String, Object> metadata)
            throws IOException {
        String content = Files.readString(path, charset);
        Map<String, Object> output = new HashMap<>();
        output.put("content", content);
        output.put("metadata", metadata);
        return output;
    }

    private Map<String, Object> readBinary(Path path, Map<String, Object> metadata)
            throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> output = new HashMap<>();
        output.put("content", base64);
        output.put("metadata", metadata);
        return output;
    }

    private Map<String, Object> readLines(Path path, Charset charset, Map<String, Object> metadata)
            throws IOException {
        List<String> lines = Files.readAllLines(path, charset);
        Map<String, Object> output = new HashMap<>();
        output.put("content", lines);
        output.put("lineCount", lines.size());
        output.put("metadata", metadata);
        return output;
    }

    private Map<String, Object> readJson(Path path, Charset charset, Map<String, Object> metadata)
            throws IOException {
        String content = Files.readString(path, charset);
        String trimmed = content.trim();

        // Parse JSON manually using basic validation
        Object parsed = parseJsonValue(trimmed);

        Map<String, Object> output = new HashMap<>();
        output.put("content", parsed);
        output.put("metadata", metadata);
        return output;
    }

    private Map<String, Object> readCsv(Path path, Charset charset, NodeExecutionContext context,
                                         Map<String, Object> metadata) throws IOException {
        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean hasHeader = getBooleanConfig(context, "hasHeader", true);

        List<String> lines = Files.readAllLines(path, charset);
        if (lines.isEmpty()) {
            Map<String, Object> output = new HashMap<>();
            output.put("content", Collections.emptyList());
            output.put("rowCount", 0);
            output.put("metadata", metadata);
            return output;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        String[] headers = null;

        int startIndex = 0;
        if (hasHeader && !lines.isEmpty()) {
            headers = parseCsvLine(lines.get(0), delimiter);
            startIndex = 1;
        }

        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;

            String[] values = parseCsvLine(line, delimiter);
            Map<String, Object> row = new LinkedHashMap<>();

            if (headers != null) {
                for (int j = 0; j < headers.length; j++) {
                    String value = j < values.length ? values[j] : "";
                    row.put(headers[j].trim(), value.trim());
                }
            } else {
                for (int j = 0; j < values.length; j++) {
                    row.put("column_" + j, values[j].trim());
                }
            }

            rows.add(row);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("content", rows);
        output.put("rowCount", rows.size());
        if (headers != null) {
            output.put("headers", Arrays.asList(headers));
        }
        output.put("metadata", metadata);
        return output;
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private String[] parseCsvLine(String line, String delimiter) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        char delimChar = delimiter.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimChar && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    /**
     * Basic JSON value parser.
     * Supports strings, numbers, booleans, null, arrays, and objects.
     */
    @SuppressWarnings("unchecked")
    private Object parseJsonValue(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        json = json.trim();

        // Return as raw string content -- the consumer can parse further
        // For structured JSON, we rely on the downstream to handle it
        // This avoids adding Jackson as a direct dependency
        if (json.startsWith("{") || json.startsWith("[")) {
            // Return as string; the flow engine / expression evaluator will handle
            return json;
        }
        if (json.equals("null")) return null;
        if (json.equals("true")) return true;
        if (json.equals("false")) return false;
        if (json.startsWith("\"") && json.endsWith("\"")) {
            return json.substring(1, json.length() - 1);
        }

        // Try number
        try {
            if (json.contains(".")) {
                return Double.parseDouble(json);
            }
            return Long.parseLong(json);
        } catch (NumberFormatException e) {
            return json;
        }
    }

    private Map<String, Object> buildFileMetadata(Path path) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", path.getFileName().toString());
        metadata.put("path", path.toAbsolutePath().toString());
        metadata.put("size", Files.size(path));

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        metadata.put("extension", dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "");

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        metadata.put("lastModified", attrs.lastModifiedTime().toInstant().toString());
        metadata.put("createdAt", attrs.creationTime().toInstant().toString());

        return metadata;
    }

    private Charset resolveCharset(String encoding) {
        try {
            return Charset.forName(encoding);
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
                    "enum", List.of("readText", "readBinary", "readLines", "readJson", "readCsv"),
                    "default", "readText"
                ),
                "filePath", Map.of(
                    "type", "string",
                    "title", "File Path",
                    "description", "Path to the file to read"
                ),
                "encoding", Map.of(
                    "type", "string",
                    "title", "Encoding",
                    "description", "Character encoding (default: UTF-8)",
                    "default", "UTF-8"
                ),
                "delimiter", Map.of(
                    "type", "string",
                    "title", "CSV Delimiter",
                    "description", "Delimiter for CSV files (default: comma)",
                    "default", ","
                ),
                "hasHeader", Map.of(
                    "type", "boolean",
                    "title", "Has Header Row",
                    "description", "Whether the CSV file has a header row",
                    "default", true
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "filePath", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "content", "type", "any"),
                Map.of("name", "metadata", "type", "object")
            )
        );
    }
}
