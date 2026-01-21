package com.aiinpocket.n3n.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of node configuration validation.
 */
@Data
@Builder
public class ValidationResult {

    private boolean valid;
    private List<ValidationError> errors;

    public static ValidationResult valid() {
        return ValidationResult.builder()
            .valid(true)
            .errors(new ArrayList<>())
            .build();
    }

    public static ValidationResult invalid(List<ValidationError> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }

    public static ValidationResult invalid(String field, String message) {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(new ValidationError(field, message));
        return invalid(errors);
    }

    @Data
    public static class ValidationError {
        private final String field;
        private final String message;
    }
}
