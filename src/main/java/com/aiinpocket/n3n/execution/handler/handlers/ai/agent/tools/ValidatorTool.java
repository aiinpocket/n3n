package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Data validation tool
 * Supports common data format validation
 */
@Component
@Slf4j
public class ValidatorTool implements AgentNodeTool {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9]{8,15}$"
    );
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "^[0-9]{13,19}$"
    );

    @Override
    public String getId() {
        return "validator";
    }

    @Override
    public String getName() {
        return "Validator";
    }

    @Override
    public String getDescription() {
        return """
                Data validation tool, supports multiple format validations:
                - email: Email format
                - url: URL format
                - phone: Phone number
                - ipv4: IPv4 address
                - uuid: UUID format
                - creditCard: Credit card number (using Luhn algorithm)
                - json: JSON format
                - number: Number
                - integer: Integer
                - date: Date format (ISO 8601)

                Parameters:
                - value: Value to validate
                - type: Validation type
                - required: Whether the value is required (default true)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of(
                                "type", "string",
                                "description", "Value to validate"
                        ),
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of("email", "url", "phone", "ipv4", "uuid",
                                        "creditCard", "json", "number", "integer", "date"),
                                "description", "Validation type"
                        ),
                        "required", Map.of(
                                "type", "boolean",
                                "description", "Whether the value is required",
                                "default", true
                        )
                ),
                "required", List.of("value", "type")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String value = (String) parameters.get("value");
                String type = (String) parameters.get("type");
                boolean required = !Boolean.FALSE.equals(parameters.get("required"));

                if (type == null || type.isEmpty()) {
                    return ToolResult.failure("Validation type cannot be empty");
                }

                // Check required
                if (value == null || value.isEmpty()) {
                    if (required) {
                        return ToolResult.success("Validation failed: value is empty (required)", Map.of(
                                "valid", false,
                                "error", "Value cannot be empty"
                        ));
                    } else {
                        return ToolResult.success("Validation passed: value is empty (optional)", Map.of(
                                "valid", true
                        ));
                    }
                }

                ValidationResult result = validate(value, type);

                if (result.valid) {
                    return ToolResult.success(
                            String.format("Validation passed: \"%s\" is a valid %s", value, type),
                            Map.of("valid", true, "type", type, "value", value)
                    );
                } else {
                    return ToolResult.success(
                            String.format("Validation failed: \"%s\" is not a valid %s\nReason: %s", value, type, result.error),
                            Map.of("valid", false, "type", type, "value", value, "error", result.error)
                    );
                }

            } catch (Exception e) {
                log.error("Validation failed", e);
                return ToolResult.failure("Validation failed");
            }
        });
    }

    private ValidationResult validate(String value, String type) {
        return switch (type) {
            case "email" -> validateEmail(value);
            case "url" -> validateUrl(value);
            case "phone" -> validatePhone(value);
            case "ipv4" -> validateIPv4(value);
            case "uuid" -> validateUUID(value);
            case "creditCard" -> validateCreditCard(value);
            case "json" -> validateJson(value);
            case "number" -> validateNumber(value);
            case "integer" -> validateInteger(value);
            case "date" -> validateDate(value);
            default -> new ValidationResult(false, "Unsupported validation type: " + type);
        };
    }

    private ValidationResult validateEmail(String value) {
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "Does not match email format");
    }

    private ValidationResult validateUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return new ValidationResult(false, "Missing scheme or host");
            }
            return new ValidationResult(true, null);
        } catch (Exception e) {
            log.debug("URL validation failed: {}", e.getMessage(), e);
            return new ValidationResult(false, "Invalid URL format");
        }
    }

    private ValidationResult validatePhone(String value) {
        String cleaned = value.replaceAll("[\\s\\-()]", "");
        if (PHONE_PATTERN.matcher(cleaned).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "Does not match phone number format");
    }

    private ValidationResult validateIPv4(String value) {
        if (IPV4_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "Does not match IPv4 format");
    }

    private ValidationResult validateUUID(String value) {
        if (UUID_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "Does not match UUID format");
    }

    private ValidationResult validateCreditCard(String value) {
        String cleaned = value.replaceAll("[\\s\\-]", "");
        if (!CREDIT_CARD_PATTERN.matcher(cleaned).matches()) {
            return new ValidationResult(false, "Card number length or format is incorrect");
        }

        // Luhn algorithm validation
        if (luhnCheck(cleaned)) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "Credit card number check failed (Luhn algorithm)");
    }

    private boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private ValidationResult validateJson(String value) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(value);
            return new ValidationResult(true, null);
        } catch (Exception e) {
            log.debug("JSON validation failed: {}", e.getMessage(), e);
            return new ValidationResult(false, "Invalid JSON format");
        }
    }

    private ValidationResult validateNumber(String value) {
        try {
            Double.parseDouble(value);
            return new ValidationResult(true, null);
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Not a valid number");
        }
    }

    private ValidationResult validateInteger(String value) {
        try {
            Long.parseLong(value);
            return new ValidationResult(true, null);
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Not a valid integer");
        }
    }

    private ValidationResult validateDate(String value) {
        try {
            java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(value);
            return new ValidationResult(true, null);
        } catch (Exception e1) {
            try {
                java.time.format.DateTimeFormatter.ISO_DATE.parse(value);
                return new ValidationResult(true, null);
            } catch (Exception e2) {
                return new ValidationResult(false, "Does not match ISO 8601 date format");
            }
        }
    }

    private record ValidationResult(boolean valid, String error) {}

    @Override
    public String getCategory() {
        return "validation";
    }
}
