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
 * 資料驗證工具
 * 支援常見的資料格式驗證
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
                資料驗證工具，支援多種格式驗證：
                - email: 電子郵件格式
                - url: URL 格式
                - phone: 電話號碼
                - ipv4: IPv4 位址
                - uuid: UUID 格式
                - creditCard: 信用卡號（使用 Luhn 演算法）
                - json: JSON 格式
                - number: 數字
                - integer: 整數
                - date: 日期格式（ISO 8601）

                參數：
                - value: 要驗證的值
                - type: 驗證類型
                - required: 是否必填（預設 true）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of(
                                "type", "string",
                                "description", "要驗證的值"
                        ),
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of("email", "url", "phone", "ipv4", "uuid",
                                        "creditCard", "json", "number", "integer", "date"),
                                "description", "驗證類型"
                        ),
                        "required", Map.of(
                                "type", "boolean",
                                "description", "是否必填",
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
                    return ToolResult.failure("驗證類型不能為空");
                }

                // 檢查必填
                if (value == null || value.isEmpty()) {
                    if (required) {
                        return ToolResult.success("驗證失敗：值為空（必填）", Map.of(
                                "valid", false,
                                "error", "值不能為空"
                        ));
                    } else {
                        return ToolResult.success("驗證通過：值為空（非必填）", Map.of(
                                "valid", true
                        ));
                    }
                }

                ValidationResult result = validate(value, type);

                if (result.valid) {
                    return ToolResult.success(
                            String.format("驗證通過：\"%s\" 是有效的 %s", value, type),
                            Map.of("valid", true, "type", type, "value", value)
                    );
                } else {
                    return ToolResult.success(
                            String.format("驗證失敗：\"%s\" 不是有效的 %s\n原因：%s", value, type, result.error),
                            Map.of("valid", false, "type", type, "value", value, "error", result.error)
                    );
                }

            } catch (Exception e) {
                log.error("Validation failed", e);
                return ToolResult.failure("驗證失敗: " + e.getMessage());
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
            default -> new ValidationResult(false, "不支援的驗證類型: " + type);
        };
    }

    private ValidationResult validateEmail(String value) {
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "不符合電子郵件格式");
    }

    private ValidationResult validateUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return new ValidationResult(false, "缺少協定或主機");
            }
            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, "無效的 URL: " + e.getMessage());
        }
    }

    private ValidationResult validatePhone(String value) {
        String cleaned = value.replaceAll("[\\s\\-()]", "");
        if (PHONE_PATTERN.matcher(cleaned).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "不符合電話號碼格式");
    }

    private ValidationResult validateIPv4(String value) {
        if (IPV4_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "不符合 IPv4 格式");
    }

    private ValidationResult validateUUID(String value) {
        if (UUID_PATTERN.matcher(value).matches()) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "不符合 UUID 格式");
    }

    private ValidationResult validateCreditCard(String value) {
        String cleaned = value.replaceAll("[\\s\\-]", "");
        if (!CREDIT_CARD_PATTERN.matcher(cleaned).matches()) {
            return new ValidationResult(false, "卡號長度或格式不正確");
        }

        // Luhn 演算法驗證
        if (luhnCheck(cleaned)) {
            return new ValidationResult(true, null);
        }
        return new ValidationResult(false, "信用卡號校驗失敗（Luhn 演算法）");
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
            return new ValidationResult(false, "無效的 JSON: " + e.getMessage());
        }
    }

    private ValidationResult validateNumber(String value) {
        try {
            Double.parseDouble(value);
            return new ValidationResult(true, null);
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "不是有效的數字");
        }
    }

    private ValidationResult validateInteger(String value) {
        try {
            Long.parseLong(value);
            return new ValidationResult(true, null);
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "不是有效的整數");
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
                return new ValidationResult(false, "不符合 ISO 8601 日期格式");
            }
        }
    }

    private record ValidationResult(boolean valid, String error) {}

    @Override
    public String getCategory() {
        return "validation";
    }
}
