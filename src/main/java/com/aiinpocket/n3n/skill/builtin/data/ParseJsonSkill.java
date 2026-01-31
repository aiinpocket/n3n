package com.aiinpocket.n3n.skill.builtin.data;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Skill to parse JSON and extract data using JSONPath.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParseJsonSkill implements BuiltinSkill {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "parse_json";
    }

    @Override
    public String getDisplayName() {
        return "Parse JSON";
    }

    @Override
    public String getDescription() {
        return "Parse JSON string and optionally extract data using JSONPath expression";
    }

    @Override
    public String getCategory() {
        return "data";
    }

    @Override
    public String getIcon() {
        return "code";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "json", Map.of(
                    "type", "string",
                    "description", "JSON string to parse"
                ),
                "jsonPath", Map.of(
                    "type", "string",
                    "description", "Optional JSONPath expression to extract specific data (e.g., $.data.items[0].name)"
                )
            ),
            "required", List.of("json")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "data", Map.of("description", "Parsed JSON data or extracted value")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        Object jsonInput = input.get("json");
        String jsonPath = (String) input.get("jsonPath");

        if (jsonInput == null) {
            return SkillResult.failure("MISSING_JSON", "JSON input is required");
        }

        try {
            // If input is already a Map/List, use it directly
            Object parsedData;
            if (jsonInput instanceof String jsonString) {
                parsedData = objectMapper.readValue(jsonString, Object.class);
            } else {
                parsedData = jsonInput;
            }

            // If JSONPath is provided, extract the specific data
            if (jsonPath != null && !jsonPath.isBlank()) {
                try {
                    String jsonString = objectMapper.writeValueAsString(parsedData);
                    Object extracted = JsonPath.read(jsonString, jsonPath);
                    return SkillResult.success(Map.of("data", extracted));
                } catch (PathNotFoundException e) {
                    return SkillResult.failure("PATH_NOT_FOUND", "JSONPath not found: " + jsonPath);
                }
            }

            return SkillResult.success(Map.of("data", parsedData));

        } catch (Exception e) {
            log.error("Failed to parse JSON: {}", e.getMessage());
            return SkillResult.failure("PARSE_ERROR", "Failed to parse JSON: " + e.getMessage());
        }
    }
}
