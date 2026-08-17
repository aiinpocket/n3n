package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Salesforce 節點：建立 / 更新 / 查詢 Salesforce 物件（Lead、Contact、Opportunity…）。
 *
 * 憑證欄位：instanceUrl（例如 https://yourorg.my.salesforce.com）、accessToken。
 * 也支援 clientId/clientSecret/username/password 的 OAuth password flow（選填）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalesforceNodeHandler extends AbstractNodeHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final String API_VERSION = "v59.0";

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "salesforce";
    }

    @Override
    public String getDisplayName() {
        return "Salesforce";
    }

    @Override
    public String getDescription() {
        return "Create, update or query Salesforce records (Lead, Contact, Opportunity, ...). "
                + "Credential fields: instanceUrl + accessToken. 操作 Salesforce CRM 資料。";
    }

    @Override
    public String getCategory() {
        return "Integrations";
    }

    @Override
    public String getIcon() {
        return "cloud";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        String operation = String.valueOf(config.getOrDefault("operation", "create"));
        if ("query".equals(operation)) {
            Object soql = config.get("query");
            if (soql == null || soql.toString().isBlank()) {
                return ValidationResult.invalid("query", "SOQL query is required for query operation");
            }
            return ValidationResult.valid();
        }
        Object sobject = config.get("sobject");
        if (sobject == null || sobject.toString().isBlank()) {
            return ValidationResult.invalid("sobject", "Object type is required (e.g. Lead)");
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String credentialId = getStringConfig(context, "credentialId", "");
        if (credentialId.isBlank() || context.getCredentialResolver() == null) {
            return NodeExecutionResult.failure(
                    "Salesforce credential is required — 請先在憑證管理建立 Salesforce 憑證（instanceUrl + accessToken）");
        }

        try {
            Map<String, Object> credential =
                    context.getCredentialResolver().resolve(UUID.fromString(credentialId), context.getUserId());
            String instanceUrl = str(credential.get("instanceUrl"));
            String accessToken = str(credential.get("accessToken"));

            if (accessToken.isBlank()) {
                // OAuth username-password flow（憑證有 clientId/clientSecret/username/password 時）
                Map<String, String> token = passwordFlow(credential);
                instanceUrl = token.getOrDefault("instance_url", instanceUrl);
                accessToken = token.getOrDefault("access_token", "");
            }
            if (instanceUrl.isBlank() || accessToken.isBlank()) {
                return NodeExecutionResult.failure("Credential is missing instanceUrl / accessToken");
            }
            instanceUrl = instanceUrl.replaceAll("/+$", "");

            String operation = getStringConfig(context, "operation", "create");
            return switch (operation) {
                case "create" -> create(context, instanceUrl, accessToken);
                case "update" -> update(context, instanceUrl, accessToken);
                case "query" -> query(context, instanceUrl, accessToken);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            log.warn("Salesforce operation failed: {}", e.getMessage());
            return NodeExecutionResult.failure("Salesforce operation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private Map<String, String> passwordFlow(Map<String, Object> credential) throws Exception {
        String clientId = str(credential.get("clientId"));
        String clientSecret = str(credential.get("clientSecret"));
        String username = str(credential.get("username"));
        String password = str(credential.get("password"));
        if (clientId.isBlank() || username.isBlank()) {
            return Map.of();
        }
        String loginUrl = str(credential.getOrDefault("loginUrl", "https://login.salesforce.com"));
        String body = "grant_type=password"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(loginUrl + "/services/oauth2/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Map<String, String> result = new LinkedHashMap<>();
        parsed.forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldsFromConfig(NodeExecutionContext context) throws Exception {
        Object fields = context.getNodeConfig().get("fields");
        if (fields instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (fields instanceof String s && !s.isBlank()) {
            return objectMapper.readValue(s, new TypeReference<>() {});
        }
        return Map.of();
    }

    private NodeExecutionResult create(NodeExecutionContext context, String instanceUrl, String accessToken)
            throws Exception {
        String sobject = getStringConfig(context, "sobject", "Lead");
        Map<String, Object> fields = fieldsFromConfig(context);
        if (fields.isEmpty()) {
            return NodeExecutionResult.failure(
                    "'fields' is required: JSON of field values, e.g. {\"LastName\":\"王\",\"Company\":\"n3n\"}");
        }

        HttpResponse<String> response = send(
                instanceUrl + "/services/data/" + API_VERSION + "/sobjects/" + sobject,
                "POST", accessToken, objectMapper.writeValueAsString(fields));
        if (response.statusCode() >= 400) {
            return failureFrom(response);
        }
        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        return NodeExecutionResult.success(Map.of(
                "created", true,
                "id", String.valueOf(parsed.getOrDefault("id", "")),
                "sobject", sobject));
    }

    private NodeExecutionResult update(NodeExecutionContext context, String instanceUrl, String accessToken)
            throws Exception {
        String sobject = getStringConfig(context, "sobject", "Lead");
        String recordId = getStringConfig(context, "recordId", "");
        if (recordId.isBlank()) {
            return NodeExecutionResult.failure("recordId is required for update");
        }
        Map<String, Object> fields = fieldsFromConfig(context);
        if (fields.isEmpty()) {
            return NodeExecutionResult.failure("'fields' is required for update");
        }

        HttpResponse<String> response = send(
                instanceUrl + "/services/data/" + API_VERSION + "/sobjects/" + sobject + "/" + recordId,
                "PATCH", accessToken, objectMapper.writeValueAsString(fields));
        if (response.statusCode() >= 400) {
            return failureFrom(response);
        }
        return NodeExecutionResult.success(Map.of("updated", true, "id", recordId, "sobject", sobject));
    }

    private NodeExecutionResult query(NodeExecutionContext context, String instanceUrl, String accessToken)
            throws Exception {
        String soql = getStringConfig(context, "query", "");
        HttpResponse<String> response = send(
                instanceUrl + "/services/data/" + API_VERSION + "/query?q="
                        + URLEncoder.encode(soql, StandardCharsets.UTF_8),
                "GET", accessToken, null);
        if (response.statusCode() >= 400) {
            return failureFrom(response);
        }
        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        return NodeExecutionResult.success(Map.of(
                "records", parsed.getOrDefault("records", List.of()),
                "totalSize", parsed.getOrDefault("totalSize", 0)));
    }

    private HttpResponse<String> send(String url, String method, String accessToken, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json");
        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private NodeExecutionResult failureFrom(HttpResponse<String> response) {
        log.warn("Salesforce API error: HTTP {} {}", response.statusCode(), response.body());
        return NodeExecutionResult.failure("Salesforce API error: HTTP " + response.statusCode());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("credentialId", Map.of(
                "type", "string",
                "format", "credential",
                "title", "Credential",
                "description", "Salesforce credential: instanceUrl + accessToken. Salesforce 憑證"
        ));
        properties.put("operation", Map.of(
                "type", "string",
                "title", "Operation",
                "enum", List.of("create", "update", "query"),
                "default", "create"
        ));
        properties.put("sobject", Map.of(
                "type", "string",
                "title", "Object Type",
                "description", "e.g. Lead, Contact, Opportunity. 要操作的物件類型"
        ));
        properties.put("fields", Map.of(
                "type", "object",
                "title", "Fields",
                "description", "Field values as JSON, e.g. {\"LastName\":\"王\",\"Company\":\"n3n\"}. 欄位值"
        ));
        properties.put("recordId", Map.of("type", "string", "title", "Record ID", "description", "update 用"));
        properties.put("query", Map.of(
                "type", "string",
                "format", "textarea",
                "title", "SOQL Query",
                "description", "query 用，例如 SELECT Id, Name FROM Lead LIMIT 10"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("credentialId")
        );
    }
}
