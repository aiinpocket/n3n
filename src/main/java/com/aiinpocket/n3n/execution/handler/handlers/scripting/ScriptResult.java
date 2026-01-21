package com.aiinpocket.n3n.execution.handler.handlers.scripting;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Result of script execution.
 */
@Data
@Builder
public class ScriptResult {

    private boolean success;
    private Object output;
    private Map<String, Object> data;
    private String errorMessage;
    private String errorType;
    private List<String> logs;
    private long executionTimeMs;

    public static ScriptResult success(Object output) {
        return ScriptResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    public static ScriptResult success(Map<String, Object> data) {
        return ScriptResult.builder()
            .success(true)
            .data(data)
            .build();
    }

    public static ScriptResult failure(String errorMessage) {
        return ScriptResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    public static ScriptResult failure(String errorType, String errorMessage) {
        return ScriptResult.builder()
            .success(false)
            .errorType(errorType)
            .errorMessage(errorMessage)
            .build();
    }
}
