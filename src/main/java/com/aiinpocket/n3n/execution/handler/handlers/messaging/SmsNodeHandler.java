package com.aiinpocket.n3n.execution.handler.handlers.messaging;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 簡訊節點：透過 Twilio 相容 API 發送 SMS。
 *
 * 憑證欄位：accountSid、authToken、from（發送號碼）。
 * 也接受直接填在節點設定（credentialId 優先）。
 */
@Component
@Slf4j
public class SmsNodeHandler extends AbstractNodeHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String getType() {
        return "sms";
    }

    @Override
    public String getDisplayName() {
        return "SMS";
    }

    @Override
    public String getDescription() {
        return "Send a text message via Twilio-compatible API. Credential fields: accountSid, authToken, from. "
                + "透過 Twilio 發送簡訊；憑證需要 accountSid、authToken 與發送號碼。";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "message";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object to = config.get("to");
        if (to == null || to.toString().isBlank()) {
            return ValidationResult.invalid("to", "Recipient phone number is required");
        }
        Object message = config.get("message");
        if (message == null || message.toString().isBlank()) {
            return ValidationResult.invalid("message", "Message is required");
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String to = getStringConfig(context, "to", "").trim();
        String message = getStringConfig(context, "message", "");

        Map<String, Object> credential = resolveCredential(context);
        String accountSid = firstNonBlank(str(credential.get("accountSid")), getStringConfig(context, "accountSid", ""));
        String authToken = firstNonBlank(str(credential.get("authToken")), getStringConfig(context, "authToken", ""));
        String from = firstNonBlank(str(credential.get("from")), getStringConfig(context, "from", ""));

        if (accountSid.isBlank() || authToken.isBlank()) {
            return NodeExecutionResult.failure(
                    "SMS credential is required (accountSid + authToken) — 請先在憑證管理建立 Twilio 憑證並在此節點選用");
        }
        if (from.isBlank()) {
            return NodeExecutionResult.failure("Sender number 'from' is required — 請填發送號碼（Twilio 提供的號碼）");
        }

        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
            String body = "To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
            String basic = Base64.getEncoder()
                    .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("SMS send failed: HTTP {} {}", response.statusCode(), response.body());
                return NodeExecutionResult.failure("SMS send failed: HTTP " + response.statusCode());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sent", true);
            output.put("to", to);
            output.put("statusCode", response.statusCode());
            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            log.warn("SMS send failed: {}", e.getMessage());
            return NodeExecutionResult.failure("SMS send failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private Map<String, Object> resolveCredential(NodeExecutionContext context) {
        String credentialId = getStringConfig(context, "credentialId", "");
        if (credentialId.isBlank() || context.getCredentialResolver() == null) {
            return Map.of();
        }
        try {
            return context.getCredentialResolver().resolve(UUID.fromString(credentialId), context.getUserId());
        } catch (Exception e) {
            log.warn("Failed to resolve SMS credential: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("credentialId", Map.of(
                "type", "string",
                "format", "credential",
                "title", "Credential",
                "description", "Twilio credential with accountSid / authToken / from. Twilio 憑證"
        ));
        properties.put("to", Map.of(
                "type", "string",
                "title", "To",
                "description", "Recipient phone number in +886912345678 format. 收訊人手機號碼（含國碼）"
        ));
        properties.put("message", Map.of(
                "type", "string",
                "format", "textarea",
                "title", "Message",
                "description", "Supports {{expressions}}. 簡訊內容"
        ));
        properties.put("from", Map.of(
                "type", "string",
                "title", "From",
                "description", "Sender number; can also live in the credential. 發送號碼（也可放在憑證裡）"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", java.util.List.of("to", "message")
        );
    }
}
