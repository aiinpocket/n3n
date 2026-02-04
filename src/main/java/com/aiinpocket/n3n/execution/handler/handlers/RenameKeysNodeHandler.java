package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for rename keys nodes.
 * Renames keys/properties in data objects.
 */
@Component
@Slf4j
public class RenameKeysNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "renameKeys";
    }

    @Override
    public String getDisplayName() {
        return "Rename Keys";
    }

    @Override
    public String getDescription() {
        return "Renames keys/properties in data objects.";
    }

    @Override
    public String getCategory() {
        return "Data Transform";
    }

    @Override
    public String getIcon() {
        return "swap";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        boolean keepUnmapped = getBooleanConfig(context, "keepUnmapped", true);
        boolean deep = getBooleanConfig(context, "deep", false);

        // Get rename mappings from config
        Object mappingsConfig = context.getNodeConfig().get("mappings");
        Map<String, String> mappings = new HashMap<>();

        if (mappingsConfig instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) mappingsConfig).entrySet()) {
                mappings.put(entry.getKey(), entry.getValue().toString());
            }
        } else if (mappingsConfig instanceof List) {
            for (Object item : (List<?>) mappingsConfig) {
                if (item instanceof Map) {
                    Map<String, Object> mapping = (Map<String, Object>) item;
                    String from = (String) mapping.get("from");
                    String to = (String) mapping.get("to");
                    if (from != null && to != null) {
                        mappings.put(from, to);
                    }
                }
            }
        }

        log.debug("Renaming {} keys, keepUnmapped: {}", mappings.size(), keepUnmapped);

        if (inputData == null) {
            return NodeExecutionResult.builder()
                .success(true)
                .output(Map.of())
                .build();
        }

        Map<String, Object> output = renameKeys(inputData, mappings, keepUnmapped, deep);

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renameKeys(Map<String, Object> data, Map<String, String> mappings,
                                           boolean keepUnmapped, boolean deep) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Determine new key name
            String newKey = mappings.getOrDefault(key, keepUnmapped ? key : null);

            if (newKey != null) {
                // Recursively process nested objects if deep mode
                if (deep && value instanceof Map) {
                    value = renameKeys((Map<String, Object>) value, mappings, keepUnmapped, true);
                } else if (deep && value instanceof List) {
                    value = renameInList((List<Object>) value, mappings, keepUnmapped);
                }

                result.put(newKey, value);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> renameInList(List<Object> list, Map<String, String> mappings, boolean keepUnmapped) {
        List<Object> result = new ArrayList<>();

        for (Object item : list) {
            if (item instanceof Map) {
                result.add(renameKeys((Map<String, Object>) item, mappings, keepUnmapped, true));
            } else {
                result.add(item);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "mappings", Map.of(
                    "type", "array",
                    "title", "Key Mappings",
                    "description", "Mappings from old key names to new key names",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "from", Map.of("type", "string", "title", "From"),
                            "to", Map.of("type", "string", "title", "To")
                        )
                    )
                ),
                "keepUnmapped", Map.of(
                    "type", "boolean",
                    "title", "Keep Unmapped Keys",
                    "description", "Keep keys that are not in the mapping",
                    "default", true
                ),
                "deep", Map.of(
                    "type", "boolean",
                    "title", "Deep Rename",
                    "description", "Rename keys in nested objects too",
                    "default", false
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "object", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
