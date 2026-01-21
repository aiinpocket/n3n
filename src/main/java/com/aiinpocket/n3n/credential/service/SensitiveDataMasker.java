package com.aiinpocket.n3n.credential.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感資料遮罩服務
 *
 * 用於在 Log、執行記錄中遮罩敏感資訊，
 * 防止資料庫被盜取後敏感資訊外洩。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SensitiveDataMasker {

    private final ObjectMapper objectMapper;

    // 需要遮罩的欄位名稱（不區分大小寫）
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
        // 密碼相關
        "password", "passwd", "pwd", "pass",
        // Token 相關
        "token", "accesstoken", "access_token", "refreshtoken", "refresh_token",
        "idtoken", "id_token", "bearertoken", "bearer_token", "authtoken", "auth_token",
        // API Key 相關
        "apikey", "api_key", "apikeys", "api_keys",
        "secret", "secretkey", "secret_key", "clientsecret", "client_secret",
        // 憑證相關
        "credential", "credentials", "privatekey", "private_key",
        "certificate", "cert", "ssh_key", "sshkey",
        // 資料庫相關
        "connectionstring", "connection_string", "dbpassword", "db_password",
        // OAuth 相關
        "clientid", "client_id",
        // 其他
        "authorization", "auth", "bearer", "basic",
        "encrypteddata", "encrypted_data", "encryptionkey", "encryption_key"
    );

    // 需要遮罩的 Header 名稱
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization", "x-api-key", "x-auth-token", "cookie",
        "x-access-token", "x-refresh-token", "proxy-authorization"
    );

    // 遮罩顯示
    private static final String MASK = "********";
    private static final String PARTIAL_MASK_TEMPLATE = "%s****%s";

    // 常見的敏感資料模式
    private static final Pattern JWT_PATTERN = Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{20,}$");
    private static final Pattern BEARER_PATTERN = Pattern.compile("^Bearer\\s+.+$", Pattern.CASE_INSENSITIVE);

    /**
     * 遮罩 Map 中的敏感資料
     */
    public Map<String, Object> maskMap(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        try {
            JsonNode node = objectMapper.valueToTree(data);
            JsonNode masked = maskNode(node);
            return objectMapper.convertValue(masked, Map.class);
        } catch (Exception e) {
            log.warn("Failed to mask sensitive data in map", e);
            return data;
        }
    }

    /**
     * 遮罩 JSON 字串中的敏感資料
     */
    public String maskJsonString(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode masked = maskNode(node);
            return objectMapper.writeValueAsString(masked);
        } catch (JsonProcessingException e) {
            // 非 JSON 格式，嘗試遮罩可能的敏感值
            return maskPlainString(json);
        }
    }

    /**
     * 遮罩物件
     */
    public Object maskObject(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof String) {
            return maskPlainString((String) obj);
        }

        if (obj instanceof Map) {
            return maskMap((Map<String, Object>) obj);
        }

        try {
            JsonNode node = objectMapper.valueToTree(obj);
            return objectMapper.treeToValue(maskNode(node), obj.getClass());
        } catch (Exception e) {
            log.warn("Failed to mask object of type {}", obj.getClass().getName());
            return obj;
        }
    }

    private JsonNode maskNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            return maskObjectNode((ObjectNode) node);
        }

        if (node.isArray()) {
            return maskArrayNode((ArrayNode) node);
        }

        return node;
    }

    private ObjectNode maskObjectNode(ObjectNode node) {
        ObjectNode result = objectMapper.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            if (isSensitiveField(fieldName)) {
                // 敏感欄位直接遮罩
                result.put(fieldName, maskValue(fieldValue));
            } else if (fieldValue.isObject()) {
                // 遞迴處理物件
                result.set(fieldName, maskObjectNode((ObjectNode) fieldValue));
            } else if (fieldValue.isArray()) {
                // 遞迴處理陣列
                result.set(fieldName, maskArrayNode((ArrayNode) fieldValue));
            } else if (fieldValue.isTextual()) {
                // 檢查文字值是否為敏感格式
                String text = fieldValue.asText();
                if (isSensitiveValue(text)) {
                    result.put(fieldName, maskString(text));
                } else {
                    result.set(fieldName, fieldValue);
                }
            } else {
                result.set(fieldName, fieldValue);
            }
        }

        return result;
    }

    private ArrayNode maskArrayNode(ArrayNode node) {
        ArrayNode result = objectMapper.createArrayNode();

        for (JsonNode element : node) {
            result.add(maskNode(element));
        }

        return result;
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String normalized = fieldName.toLowerCase().replaceAll("[_-]", "");
        return SENSITIVE_FIELD_NAMES.stream()
            .anyMatch(sensitive -> normalized.contains(sensitive.replaceAll("[_-]", "")));
    }

    private boolean isSensitiveValue(String value) {
        if (value == null || value.length() < 10) {
            return false;
        }

        // JWT Token
        if (JWT_PATTERN.matcher(value).matches()) {
            return true;
        }

        // Bearer Token
        if (BEARER_PATTERN.matcher(value).matches()) {
            return true;
        }

        // 長度超過 30 且符合 API Key 模式
        if (value.length() >= 30 && API_KEY_PATTERN.matcher(value).matches()) {
            return true;
        }

        return false;
    }

    private String maskValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return maskString(node.asText());
        }

        if (node.isObject() || node.isArray()) {
            return MASK;
        }

        return MASK;
    }

    private String maskString(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int length = value.length();

        // 短字串完全遮罩
        if (length <= 8) {
            return MASK;
        }

        // 長字串保留前後各 2 個字元
        String prefix = value.substring(0, 2);
        String suffix = value.substring(length - 2);
        return String.format(PARTIAL_MASK_TEMPLATE, prefix, suffix);
    }

    private String maskPlainString(String value) {
        if (value == null) {
            return null;
        }

        if (isSensitiveValue(value)) {
            return maskString(value);
        }

        return value;
    }

    /**
     * 遮罩 HTTP Headers
     */
    public Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }

        return headers.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> isSensitiveHeader(e.getKey()) ? MASK : e.getValue()
            ));
    }

    private boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase());
    }

    /**
     * 建立安全的 Log 訊息
     */
    public String createSafeLogMessage(String template, Object... args) {
        Object[] maskedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            maskedArgs[i] = maskObject(args[i]);
        }
        return String.format(template, maskedArgs);
    }
}
