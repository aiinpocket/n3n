package com.aiinpocket.n3n.skill;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Result of skill execution.
 */
@Data
@Builder
public class SkillResult {

    private boolean success;
    private Map<String, Object> data;
    private String errorMessage;
    private String errorCode;

    /**
     * Create a successful result with data.
     */
    public static SkillResult success(Map<String, Object> data) {
        return SkillResult.builder()
            .success(true)
            .data(data)
            .build();
    }

    /**
     * Create a successful result with a single value.
     */
    public static SkillResult success(String key, Object value) {
        return SkillResult.builder()
            .success(true)
            .data(Map.of(key, value))
            .build();
    }

    /**
     * Create a failed result with error message.
     */
    public static SkillResult failure(String errorMessage) {
        return SkillResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Create a failed result with error code and message.
     */
    public static SkillResult failure(String errorCode, String errorMessage) {
        return SkillResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Create a failed result from an exception.
     */
    public static SkillResult failure(Throwable exception) {
        return SkillResult.builder()
            .success(false)
            .errorMessage(exception.getMessage())
            .errorCode(exception.getClass().getSimpleName())
            .build();
    }
}
