package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JSON 操作工具
 *
 * 允許 AI Agent 進行 JSON 資料操作：
 * - 解析 JSON 字串
 * - 提取特定路徑的值
 * - 格式化/美化 JSON
 * - 合併 JSON 物件
 * - 驗證 JSON 格式
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "json";
    }

    @Override
    public String getName() {
        return "JSON Tool";
    }

    @Override
    public String getDescription() {
        return """
                JSON 資料操作工具。支援的操作：
                - parse: 解析 JSON 字串
                - get: 使用路徑提取值（如 user.name、items[0].id）
                - format: 美化 JSON（縮排格式化）
                - minify: 壓縮 JSON（移除空白）
                - validate: 驗證 JSON 格式是否正確
                - merge: 合併多個 JSON 物件
                - keys: 取得物件的所有鍵
                - values: 取得物件的所有值
                - count: 計算陣列長度或物件屬性數量
                - filter: 過濾陣列元素

                路徑語法：
                - 使用點號分隔：user.profile.name
                - 陣列索引：items[0]、items[-1]（最後一個）
                - 萬用字元：items[*].id（所有元素的 id）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "get", "format", "minify", "validate", "merge", "keys", "values", "count", "filter"),
                                "description", "操作類型",
                                "default", "parse"
                        ),
                        "json", Map.of(
                                "type", "string",
                                "description", "JSON 字串"
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "JSON 路徑（用於 get 操作）"
                        ),
                        "json2", Map.of(
                                "type", "string",
                                "description", "第二個 JSON（用於 merge 操作）"
                        ),
                        "condition", Map.of(
                                "type", "string",
                                "description", "過濾條件（用於 filter 操作，如 status=active）"
                        )
                ),
                "required", List.of("json")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonStr = (String) parameters.get("json");
                if (jsonStr == null || jsonStr.isBlank()) {
                    return ToolResult.failure("JSON 字串不能為空");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");

                return switch (operation.toLowerCase()) {
                    case "parse" -> handleParse(jsonStr);
                    case "get" -> handleGet(jsonStr, (String) parameters.get("path"));
                    case "format" -> handleFormat(jsonStr);
                    case "minify" -> handleMinify(jsonStr);
                    case "validate" -> handleValidate(jsonStr);
                    case "merge" -> handleMerge(jsonStr, (String) parameters.get("json2"));
                    case "keys" -> handleKeys(jsonStr);
                    case "values" -> handleValues(jsonStr);
                    case "count" -> handleCount(jsonStr);
                    case "filter" -> handleFilter(jsonStr, (String) parameters.get("condition"));
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (JsonProcessingException e) {
                log.error("JSON parsing failed", e);
                return ToolResult.failure("JSON 解析錯誤: " + e.getMessage());
            } catch (Exception e) {
                log.error("JSON operation failed", e);
                return ToolResult.failure("JSON 操作失敗: " + e.getMessage());
            }
        });
    }

    /**
     * 解析 JSON
     */
    private ToolResult handleParse(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        String type = getNodeType(node);

        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("parsed", objectMapper.convertValue(node, Object.class));

        if (node.isObject()) {
            data.put("keys", getObjectKeys(node));
            data.put("property_count", node.size());
        } else if (node.isArray()) {
            data.put("length", node.size());
        }

        return ToolResult.success(
                String.format("JSON 類型: %s\n%s", type, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)),
                data
        );
    }

    /**
     * 使用路徑取值
     */
    private ToolResult handleGet(String jsonStr, String path) throws JsonProcessingException {
        if (path == null || path.isBlank()) {
            return ToolResult.failure("get 操作需要提供 path 參數");
        }

        JsonNode root = objectMapper.readTree(jsonStr);
        JsonNode result = getByPath(root, path);

        if (result == null || result.isMissingNode()) {
            return ToolResult.success(
                    String.format("路徑 '%s' 未找到值", path),
                    Map.of("path", path, "found", false)
            );
        }

        Object value = objectMapper.convertValue(result, Object.class);

        return ToolResult.success(
                String.format("路徑 '%s' 的值: %s", path, result.toString()),
                Map.of(
                        "path", path,
                        "value", value,
                        "type", getNodeType(result),
                        "found", true
                )
        );
    }

    /**
     * 格式化 JSON
     */
    private ToolResult handleFormat(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

        return ToolResult.success(
                formatted,
                Map.of("formatted", formatted)
        );
    }

    /**
     * 壓縮 JSON
     */
    private ToolResult handleMinify(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        ObjectMapper compactMapper = objectMapper.copy();
        compactMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        String minified = compactMapper.writeValueAsString(node);

        return ToolResult.success(
                minified,
                Map.of(
                        "minified", minified,
                        "original_length", jsonStr.length(),
                        "minified_length", minified.length(),
                        "saved", jsonStr.length() - minified.length()
                )
        );
    }

    /**
     * 驗證 JSON
     */
    private ToolResult handleValidate(String jsonStr) {
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            String type = getNodeType(node);

            return ToolResult.success(
                    "JSON 格式有效 (" + type + ")",
                    Map.of("valid", true, "type", type)
            );
        } catch (JsonProcessingException e) {
            return ToolResult.success(
                    "JSON 格式無效: " + e.getOriginalMessage(),
                    Map.of("valid", false, "error", e.getOriginalMessage())
            );
        }
    }

    /**
     * 合併 JSON
     */
    private ToolResult handleMerge(String jsonStr, String json2Str) throws JsonProcessingException {
        if (json2Str == null || json2Str.isBlank()) {
            return ToolResult.failure("merge 操作需要提供 json2 參數");
        }

        JsonNode node1 = objectMapper.readTree(jsonStr);
        JsonNode node2 = objectMapper.readTree(json2Str);

        if (!node1.isObject() || !node2.isObject()) {
            return ToolResult.failure("merge 操作僅支援物件類型的 JSON");
        }

        ObjectNode merged = ((ObjectNode) node1).deepCopy();
        merged.setAll((ObjectNode) node2);

        String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged);

        return ToolResult.success(
                "合併結果:\n" + result,
                Map.of("merged", objectMapper.convertValue(merged, Object.class))
        );
    }

    /**
     * 取得所有鍵
     */
    private ToolResult handleKeys(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isObject()) {
            return ToolResult.failure("keys 操作僅支援物件類型的 JSON");
        }

        List<String> keys = getObjectKeys(node);

        return ToolResult.success(
                "JSON 物件包含 " + keys.size() + " 個鍵: " + String.join(", ", keys),
                Map.of("keys", keys, "count", keys.size())
        );
    }

    /**
     * 取得所有值
     */
    private ToolResult handleValues(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isObject()) {
            return ToolResult.failure("values 操作僅支援物件類型的 JSON");
        }

        List<Object> values = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            values.add(objectMapper.convertValue(entry.getValue(), Object.class));
        });

        return ToolResult.success(
                "JSON 物件包含 " + values.size() + " 個值",
                Map.of("values", values, "count", values.size())
        );
    }

    /**
     * 計算數量
     */
    private ToolResult handleCount(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        int count = node.size();
        String type = node.isArray() ? "陣列元素" : "物件屬性";

        return ToolResult.success(
                String.format("JSON 包含 %d 個%s", count, type),
                Map.of("count", count, "type", getNodeType(node))
        );
    }

    /**
     * 過濾陣列
     */
    private ToolResult handleFilter(String jsonStr, String condition) throws JsonProcessingException {
        if (condition == null || condition.isBlank()) {
            return ToolResult.failure("filter 操作需要提供 condition 參數（如 status=active）");
        }

        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isArray()) {
            return ToolResult.failure("filter 操作僅支援陣列類型的 JSON");
        }

        // 解析條件
        String[] parts = condition.split("=", 2);
        if (parts.length != 2) {
            return ToolResult.failure("條件格式無效，應為 key=value");
        }

        String key = parts[0].trim();
        String value = parts[1].trim();

        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode element : node) {
            if (element.isObject()) {
                JsonNode fieldValue = getByPath(element, key);
                if (fieldValue != null && fieldValue.asText().equals(value)) {
                    filtered.add(element);
                }
            }
        }

        return ToolResult.success(
                String.format("過濾結果: %d 個元素符合條件 '%s'\n%s",
                        filtered.size(), condition,
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filtered)),
                Map.of(
                        "filtered", objectMapper.convertValue(filtered, Object.class),
                        "original_count", node.size(),
                        "filtered_count", filtered.size()
                )
        );
    }

    /**
     * 根據路徑取值
     */
    private JsonNode getByPath(JsonNode node, String path) {
        if (path == null || path.isEmpty()) {
            return node;
        }

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null || current.isMissingNode()) {
                return null;
            }

            // 處理陣列索引 [n] 或 [*]
            if (part.contains("[")) {
                int bracketStart = part.indexOf('[');
                String fieldName = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.length() - 1);

                if (!fieldName.isEmpty()) {
                    current = current.get(fieldName);
                    if (current == null) {
                        return null;
                    }
                }

                if (current.isArray()) {
                    if (indexStr.equals("*")) {
                        // 萬用字元：返回所有元素
                        ArrayNode results = objectMapper.createArrayNode();
                        for (JsonNode element : current) {
                            results.add(element);
                        }
                        current = results;
                    } else {
                        int index = Integer.parseInt(indexStr);
                        if (index < 0) {
                            index = current.size() + index; // 負數索引
                        }
                        current = current.get(index);
                    }
                }
            } else {
                current = current.get(part);
            }
        }

        return current;
    }

    /**
     * 取得節點類型
     */
    private String getNodeType(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }

    /**
     * 取得物件的所有鍵
     */
    private List<String> getObjectKeys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Override
    public String getCategory() {
        return "data";
    }
}
