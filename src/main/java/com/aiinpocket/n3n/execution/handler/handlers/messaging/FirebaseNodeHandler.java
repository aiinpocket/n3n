package com.aiinpocket.n3n.execution.handler.handlers.messaging;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Firebase 推播節點：透過 FCM HTTP v1 API 發送推播通知。
 *
 * 憑證欄位：serviceAccountJson（Firebase 專案的服務帳戶金鑰 JSON）。
 * token 交換流程與 Google Pub/Sub 節點相同（JWT → OAuth2 access token）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseNodeHandler extends AbstractNodeHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "firebase";
    }

    @Override
    public String getDisplayName() {
        return "Firebase Push";
    }

    @Override
    public String getDescription() {
        return "Send a push notification via Firebase Cloud Messaging (HTTP v1). "
                + "Credential field: serviceAccountJson. 透過 Firebase 發送 App 推播通知。";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public String getIcon() {
        return "notification";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object title = config.get("title");
        Object body = config.get("body");
        if ((title == null || title.toString().isBlank()) && (body == null || body.toString().isBlank())) {
            return ValidationResult.invalid("title", "Notification title or body is required");
        }
        Object token = config.get("deviceToken");
        Object topic = config.get("topic");
        if ((token == null || token.toString().isBlank()) && (topic == null || topic.toString().isBlank())) {
            return ValidationResult.invalid("deviceToken", "deviceToken or topic is required");
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String credentialId = getStringConfig(context, "credentialId", "");
        if (credentialId.isBlank() || context.getCredentialResolver() == null) {
            return NodeExecutionResult.failure(
                    "Firebase credential is required (serviceAccountJson) — 請先在憑證管理建立 Firebase 服務帳戶憑證");
        }

        try {
            Map<String, Object> credential =
                    context.getCredentialResolver().resolve(UUID.fromString(credentialId), context.getUserId());
            Object saObj = credential.get("serviceAccountJson");
            String serviceAccountJson = saObj != null ? saObj.toString() : "";
            if (serviceAccountJson.isBlank()) {
                return NodeExecutionResult.failure("Credential is missing 'serviceAccountJson'");
            }

            Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
            String projectId = String.valueOf(sa.getOrDefault("project_id", ""));
            if (projectId.isBlank()) {
                return NodeExecutionResult.failure("Service account JSON has no project_id");
            }

            String accessToken = exchangeToken(sa);
            if (accessToken == null || accessToken.isBlank()) {
                return NodeExecutionResult.failure("Failed to obtain Firebase access token");
            }

            Map<String, Object> notification = new LinkedHashMap<>();
            String title = getStringConfig(context, "title", "");
            String body = getStringConfig(context, "body", "");
            if (!title.isBlank()) notification.put("title", title);
            if (!body.isBlank()) notification.put("body", body);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("notification", notification);
            String deviceToken = getStringConfig(context, "deviceToken", "");
            String topic = getStringConfig(context, "topic", "");
            if (!deviceToken.isBlank()) {
                message.put("token", deviceToken);
            } else if (!topic.isBlank()) {
                message.put("topic", topic);
            } else {
                return NodeExecutionResult.failure("deviceToken or topic is required — 請填裝置代碼或主題名稱");
            }

            String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
            String payload = objectMapper.writeValueAsString(Map.of("message", message));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("FCM send failed: HTTP {} {}", response.statusCode(), response.body());
                return NodeExecutionResult.failure("Firebase push failed: HTTP " + response.statusCode());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sent", true);
            output.put("statusCode", response.statusCode());
            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            log.warn("Firebase push failed: {}", e.getMessage());
            return NodeExecutionResult.failure("Firebase push failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    /** service account JWT → OAuth2 access token（與 PubSubNodeHandler 相同流程） */
    private String exchangeToken(Map<String, Object> sa) throws Exception {
        String clientEmail = String.valueOf(sa.get("client_email"));
        String privateKeyPem = String.valueOf(sa.get("private_key"));
        String tokenUri = String.valueOf(sa.getOrDefault("token_uri", "https://oauth2.googleapis.com/token"));

        long now = System.currentTimeMillis() / 1000;
        String headerB64 = b64(objectMapper.writeValueAsString(Map.of("alg", "RS256", "typ", "JWT")));
        String claimsB64 = b64(objectMapper.writeValueAsString(Map.of(
                "iss", clientEmail,
                "scope", "https://www.googleapis.com/auth/firebase.messaging",
                "aud", tokenUri,
                "iat", now,
                "exp", now + 3600
        )));
        String signatureInput = headerB64 + "." + claimsB64;

        String keyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyContent)));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        String jwt = signatureInput + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());

        String body = "grant_type=" + java.net.URLEncoder.encode(
                "urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + java.net.URLEncoder.encode(jwt, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Object token = tokenResponse.get("access_token");
        return token != null ? token.toString() : null;
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("credentialId", Map.of(
                "type", "string",
                "format", "credential",
                "title", "Credential",
                "description", "Firebase service account credential (serviceAccountJson). Firebase 服務帳戶憑證"
        ));
        properties.put("title", Map.of("type", "string", "title", "Title", "description", "推播標題"));
        properties.put("body", Map.of(
                "type", "string",
                "format", "textarea",
                "title", "Body",
                "description", "Supports {{expressions}}. 推播內容"
        ));
        properties.put("deviceToken", Map.of(
                "type", "string",
                "title", "Device Token",
                "description", "FCM device registration token. 目標裝置代碼"
        ));
        properties.put("topic", Map.of(
                "type", "string",
                "title", "Topic",
                "description", "FCM topic; used when device token is empty. 主題名稱（廣播用）"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", java.util.List.of("credentialId")
        );
    }
}
