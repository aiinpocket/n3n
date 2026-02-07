package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Handler for writing files in various formats.
 *
 * Operations:
 * - writeText: Write text content to file
 * - writeBinary: Write Base64-decoded binary content to file
 * - writeJson: Write data as formatted JSON to file
 * - writeCsv: Write list of maps as CSV to file
 * - append: Append text content to existing file
 */
@Component
@Slf4j
public class WriteFileNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "Write File";
    }

    @Override
    public String getDescription() {
        return "Write files in various formats: text, binary, JSON, or CSV. Supports append mode.";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-text";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String filePath = getStringConfig(context, "filePath", "");
        String content = getStringConfig(context, "content", "");
        String encoding = getStringConfig(context, "encoding", "UTF-8");
        String operation = getStringConfig(context, "operation", "writeText");
        boolean createDirectories = getBooleanConfig(context, "createDirectories", true);

        // If filePath/content empty, try from input data
        if (filePath.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("filePath");
            if (data != null) filePath = data.toString();
        }
        if (content.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("content");
            if (data != null) content = data.toString();
        }

        if (filePath.isEmpty()) {
            return NodeExecutionResult.failure("File path is required");
        }

        // Security: prevent path traversal
        if (filePath.contains("..")) {
            return NodeExecutionResult.failure("Path traversal is not allowed");
        }

        Path path = Paths.get(filePath).normalize();

        try {
            // Create parent directories if requested
            if (createDirectories && path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Charset charset = resolveCharset(encoding);

            Map<String, Object> output = switch (operation) {
                case "writeText" -> writeText(path, content, charset);
                case "writeBinary" -> writeBinary(path, content);
                case "writeJson" -> writeJson(path, content, charset, context);
                case "writeCsv" -> writeCsv(path, content, charset, context);
                case "append" -> appendText(path, content, charset);
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
            log.error("Failed to write file {}: {}", filePath, e.getMessage(), e);
            return NodeExecutionResult.failure("Failed to write file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Write file operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Write file operation failed: " + e.getMessage());
        }
    }

    private Map<String, Object> writeText(Path path, String content, Charset charset)
            throws IOException {
        Files.writeString(path, content, charset,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return buildWriteOutput(path);
    }

    private Map<String, Object> writeBinary(Path path, String base64Content) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64Content);
        Files.write(path, bytes,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return buildWriteOutput(path);
    }

    private Map<String, Object> writeJson(Path path, String content, Charset charset,
                                           NodeExecutionContext context) throws IOException {
        boolean prettyPrint = getBooleanConfig(context, "prettyPrint", true);

        // If content is already JSON string, write it; otherwise wrap as string value
        String jsonContent = content.trim();
        if (!jsonContent.startsWith("{") && !jsonContent.startsWith("[")) {
            // Wrap plain text as JSON string
            jsonContent = "\"" + escapeJsonString(jsonContent) + "\"";
        }

        if (prettyPrint) {
            jsonContent = prettyPrintJson(jsonContent);
        }

        Files.writeString(path, jsonContent, charset,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return buildWriteOutput(path);
    }

    private Map<String, Object> writeCsv(Path path, String content, Charset charset,
                                          NodeExecutionContext context) throws IOException {
        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean includeHeader = getBooleanConfig(context, "includeHeader", true);

        // Content is expected to be a JSON array of objects
        // We parse it minimally to extract rows
        String trimmed = content.trim();
        if (!trimmed.startsWith("[")) {
            return writeText(path, content, charset);
        }

        // Parse JSON array of objects for CSV output
        List<Map<String, String>> rows = parseJsonArrayForCsv(trimmed);

        if (rows.isEmpty()) {
            Files.writeString(path, "", charset,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return buildWriteOutput(path);
        }

        // Collect all unique headers preserving order
        LinkedHashSet<String> headerSet = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            headerSet.addAll(row.keySet());
        }
        List<String> headers = new ArrayList<>(headerSet);

        try (BufferedWriter writer = Files.newBufferedWriter(path, charset,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            if (includeHeader) {
                writer.write(String.join(delimiter, headers));
                writer.newLine();
            }

            for (Map<String, String> row : rows) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    String value = row.getOrDefault(header, "");
                    // Quote values containing delimiter, quotes, or newlines
                    if (value.contains(delimiter) || value.contains("\"") || value.contains("\n")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    values.add(value);
                }
                writer.write(String.join(delimiter, values));
                writer.newLine();
            }
        }

        Map<String, Object> output = buildWriteOutput(path);
        output.put("rowCount", rows.size());
        output.put("headers", headers);
        return output;
    }

    private Map<String, Object> appendText(Path path, String content, Charset charset)
            throws IOException {
        Files.writeString(path, content, charset,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return buildWriteOutput(path);
    }

    private Map<String, Object> buildWriteOutput(Path path) throws IOException {
        Map<String, Object> output = new HashMap<>();
        output.put("filePath", path.toAbsolutePath().toString());
        output.put("fileName", path.getFileName().toString());
        output.put("size", Files.size(path));
        return output;
    }

    /**
     * Escape special characters in a JSON string value.
     */
    private String escapeJsonString(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Simple pretty printer for JSON content.
     * Adds indentation and newlines for readability.
     */
    private String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            switch (c) {
                case '{', '[' -> {
                    sb.append(c);
                    indent++;
                    sb.append('\n');
                    sb.append("  ".repeat(indent));
                }
                case '}', ']' -> {
                    indent--;
                    sb.append('\n');
                    sb.append("  ".repeat(indent));
                    sb.append(c);
                }
                case ',' -> {
                    sb.append(c);
                    sb.append('\n');
                    sb.append("  ".repeat(indent));
                }
                case ':' -> sb.append(": ");
                case ' ', '\n', '\r', '\t' -> {
                    // skip whitespace outside strings
                }
                default -> sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Parse a JSON array string into a list of maps for CSV conversion.
     * Simplified parser for flat JSON objects.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseJsonArrayForCsv(String json) {
        List<Map<String, String>> result = new ArrayList<>();

        // Simple state-machine parser for JSON array of flat objects
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return result;
        }

        // Remove outer brackets
        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) return result;

        // Split into objects (handling nested braces)
        List<String> objects = splitJsonArray(inner);

        for (String objStr : objects) {
            objStr = objStr.trim();
            if (!objStr.startsWith("{") || !objStr.endsWith("}")) continue;

            Map<String, String> row = new LinkedHashMap<>();
            String objInner = objStr.substring(1, objStr.length() - 1).trim();
            List<String> pairs = splitJsonObject(objInner);

            for (String pair : pairs) {
                int colonIdx = findJsonColon(pair);
                if (colonIdx < 0) continue;

                String key = pair.substring(0, colonIdx).trim();
                String value = pair.substring(colonIdx + 1).trim();

                // Remove quotes from key
                if (key.startsWith("\"") && key.endsWith("\"")) {
                    key = key.substring(1, key.length() - 1);
                }
                // Remove quotes from value
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.equals("null")) {
                    value = "";
                }

                row.put(key, value);
            }

            if (!row.isEmpty()) {
                result.add(row);
            }
        }

        return result;
    }

    private List<String> splitJsonArray(String inner) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (inString) {
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                items.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) items.add(last);
        return items;
    }

    private List<String> splitJsonObject(String inner) {
        List<String> pairs = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (inString) {
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) pairs.add(last);
        return pairs;
    }

    private int findJsonColon(String pair) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (c == ':' && !inString) {
                return i;
            }
        }
        return -1;
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
                    "enum", List.of("writeText", "writeBinary", "writeJson", "writeCsv", "append"),
                    "default", "writeText"
                ),
                "filePath", Map.of(
                    "type", "string",
                    "title", "File Path",
                    "description", "Path to the file to write"
                ),
                "content", Map.of(
                    "type", "string",
                    "title", "Content",
                    "description", "Content to write to the file"
                ),
                "encoding", Map.of(
                    "type", "string",
                    "title", "Encoding",
                    "description", "Character encoding (default: UTF-8)",
                    "default", "UTF-8"
                ),
                "createDirectories", Map.of(
                    "type", "boolean",
                    "title", "Create Directories",
                    "description", "Create parent directories if they don't exist",
                    "default", true
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "content", "type", "any", "required", false),
                Map.of("name", "filePath", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "filePath", "type", "string"),
                Map.of("name", "size", "type", "number")
            )
        );
    }
}
