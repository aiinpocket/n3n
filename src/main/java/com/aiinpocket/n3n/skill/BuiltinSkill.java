package com.aiinpocket.n3n.skill;

import java.util.Map;

/**
 * Interface for built-in skills implemented in Java.
 * Skills are pure code - they execute without AI involvement.
 */
public interface BuiltinSkill {

    /**
     * Unique name for this skill.
     */
    String getName();

    /**
     * Display name shown in UI.
     */
    String getDisplayName();

    /**
     * Description of what this skill does.
     */
    String getDescription();

    /**
     * Category for grouping: 'file', 'web', 'data', 'http', 'notify', 'system'
     */
    String getCategory();

    /**
     * Icon name (optional).
     */
    default String getIcon() {
        return "tool";
    }

    /**
     * JSON Schema defining the input parameters.
     */
    Map<String, Object> getInputSchema();

    /**
     * JSON Schema defining the output structure.
     */
    default Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "data", Map.of("type", "object")
            )
        );
    }

    /**
     * Execute the skill with given input.
     * This is pure code execution - no AI involved.
     */
    SkillResult execute(Map<String, Object> input);
}
