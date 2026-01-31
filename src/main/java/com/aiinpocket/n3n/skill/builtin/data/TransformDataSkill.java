package com.aiinpocket.n3n.skill.builtin.data;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill to transform data using JSONPath mappings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransformDataSkill implements BuiltinSkill {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "transform_data";
    }

    @Override
    public String getDisplayName() {
        return "Transform Data";
    }

    @Override
    public String getDescription() {
        return "Transform data by mapping fields using JSONPath expressions";
    }

    @Override
    public String getCategory() {
        return "data";
    }

    @Override
    public String getIcon() {
        return "swap";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "source", Map.of(
                    "type", "object",
                    "description", "Source data to transform"
                ),
                "mapping", Map.of(
                    "type", "object",
                    "description", "Mapping of output field names to JSONPath expressions",
                    "additionalProperties", Map.of("type", "string")
                )
            ),
            "required", List.of("source", "mapping")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "data", Map.of("type", "object", "description", "Transformed data")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        Object source = input.get("source");
        @SuppressWarnings("unchecked")
        Map<String, String> mapping = (Map<String, String>) input.get("mapping");

        if (source == null) {
            return SkillResult.failure("MISSING_SOURCE", "Source data is required");
        }

        if (mapping == null || mapping.isEmpty()) {
            return SkillResult.failure("MISSING_MAPPING", "Mapping is required");
        }

        try {
            String sourceJson = objectMapper.writeValueAsString(source);
            Map<String, Object> result = new HashMap<>();

            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String outputField = entry.getKey();
                String jsonPath = entry.getValue();

                try {
                    Object value = JsonPath.read(sourceJson, jsonPath);
                    result.put(outputField, value);
                } catch (Exception e) {
                    log.warn("Failed to extract {} using path {}: {}", outputField, jsonPath, e.getMessage());
                    result.put(outputField, null);
                }
            }

            return SkillResult.success(Map.of("data", result));

        } catch (Exception e) {
            log.error("Failed to transform data: {}", e.getMessage());
            return SkillResult.failure("TRANSFORM_ERROR", "Failed to transform data: " + e.getMessage());
        }
    }
}
