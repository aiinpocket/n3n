package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AWS S3 節點：上傳 / 下載 / 列出 / 刪除 S3（或 R2 等 S3 相容服務）物件。
 *
 * 憑證欄位：accessKey、secretKey、region（選填）、endpoint（選填，R2/MinIO 用）。
 */
@Component
@Slf4j
public class AwsS3NodeHandler extends AbstractNodeHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String getType() {
        return "awsS3";
    }

    @Override
    public String getDisplayName() {
        return "AWS S3";
    }

    @Override
    public String getDescription() {
        return "Upload, download, list or delete objects in S3 (or any S3-compatible storage like R2/MinIO). "
                + "Credential fields: accessKey, secretKey, region, endpoint. 操作 S3 物件儲存。";
    }

    @Override
    public String getCategory() {
        return "Cloud Storage";
    }

    @Override
    public String getIcon() {
        return "cloud-upload";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object bucket = config.get("bucket");
        if (bucket == null || bucket.toString().isBlank()) {
            return ValidationResult.invalid("bucket", "Bucket is required");
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "upload");
        String bucket = getStringConfig(context, "bucket", "").trim();
        String key = getStringConfig(context, "key", "").trim();

        if (bucket.isBlank()) {
            return NodeExecutionResult.failure("Bucket is required — 請填儲存桶名稱");
        }

        String credentialId = getStringConfig(context, "credentialId", "");
        if (credentialId.isBlank() || context.getCredentialResolver() == null) {
            return NodeExecutionResult.failure(
                    "S3 credential is required (accessKey + secretKey) — 請先在憑證管理建立 S3 憑證並在此節點選用");
        }

        try {
            Map<String, Object> credential =
                    context.getCredentialResolver().resolve(UUID.fromString(credentialId), context.getUserId());
            try (S3Client s3 = buildClient(credential)) {
                return switch (operation) {
                    case "upload" -> upload(context, s3, bucket, key);
                    case "download" -> download(s3, bucket, key);
                    case "list" -> list(context, s3, bucket);
                    case "delete" -> delete(s3, bucket, key);
                    default -> NodeExecutionResult.failure("Unknown operation: " + operation);
                };
            }
        } catch (S3Exception e) {
            log.warn("S3 operation {} failed: {}", operation, e.awsErrorDetails().errorMessage());
            return NodeExecutionResult.failure("S3 error: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.warn("S3 operation {} failed: {}", operation, e.getMessage());
            return NodeExecutionResult.failure("S3 operation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private S3Client buildClient(Map<String, Object> credential) {
        String accessKey = str(credential.get("accessKey"));
        String secretKey = str(credential.get("secretKey"));
        String region = str(credential.get("region"));
        String endpoint = str(credential.get("endpoint"));

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region.isBlank() ? "us-east-1" : region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    private NodeExecutionResult upload(NodeExecutionContext context, S3Client s3, String bucket, String key)
            throws Exception {
        if (key.isBlank()) {
            return NodeExecutionResult.failure("Object key is required — 請填上傳後的檔名（含路徑）");
        }

        byte[] data;
        String contentType = getStringConfig(context, "contentType", "");
        String content = getStringConfig(context, "content", "");
        String sourceUrl = getStringConfig(context, "sourceUrl", "");
        if (!sourceUrl.isBlank()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return NodeExecutionResult.failure("Failed to fetch sourceUrl: HTTP " + response.statusCode());
            }
            data = response.body();
            if (contentType.isBlank()) {
                contentType = response.headers().firstValue("Content-Type").orElse("");
            }
        } else if (!content.isBlank()) {
            // base64 內容（data URI 或純 base64）優先，否則當純文字
            String trimmed = content.trim();
            int comma = trimmed.indexOf(',');
            if (trimmed.startsWith("data:") && comma > 0) {
                data = Base64.getDecoder().decode(trimmed.substring(comma + 1));
            } else {
                data = trimmed.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            return NodeExecutionResult.failure(
                    "Provide 'content' (text) or 'sourceUrl' (file to fetch) — 請填要上傳的內容或來源網址");
        }

        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(key);
        if (!contentType.isBlank()) {
            put.contentType(contentType);
        }
        s3.putObject(put.build(), RequestBody.fromBytes(data));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("uploaded", true);
        output.put("bucket", bucket);
        output.put("key", key);
        output.put("size", data.length);
        return NodeExecutionResult.success(output);
    }

    private NodeExecutionResult download(S3Client s3, String bucket, String key) {
        if (key.isBlank()) {
            return NodeExecutionResult.failure("Object key is required");
        }
        byte[] data = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asByteArray();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("bucket", bucket);
        output.put("key", key);
        output.put("size", data.length);
        output.put("contentBase64", Base64.getEncoder().encodeToString(data));
        return NodeExecutionResult.success(output);
    }

    private NodeExecutionResult list(NodeExecutionContext context, S3Client s3, String bucket) {
        String prefix = getStringConfig(context, "prefix", "");
        int maxKeys = getIntConfig(context, "maxResults", 100);
        ListObjectsV2Response response = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(Math.max(1, Math.min(maxKeys, 1000)))
                .build());
        List<Map<String, Object>> objects = new ArrayList<>();
        for (S3Object obj : response.contents()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", obj.key());
            entry.put("size", obj.size());
            entry.put("modifiedAt", obj.lastModified() != null ? obj.lastModified().toString() : null);
            objects.add(entry);
        }
        return NodeExecutionResult.success(Map.of("objects", objects, "count", objects.size()));
    }

    private NodeExecutionResult delete(S3Client s3, String bucket, String key) {
        if (key.isBlank()) {
            return NodeExecutionResult.failure("Object key is required");
        }
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        return NodeExecutionResult.success(Map.of("deleted", true, "bucket", bucket, "key", key));
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
                "description", "S3 credential: accessKey, secretKey, region, endpoint(R2/MinIO). S3 憑證"
        ));
        properties.put("operation", Map.of(
                "type", "string",
                "title", "Operation",
                "enum", List.of("upload", "download", "list", "delete"),
                "default", "upload"
        ));
        properties.put("bucket", Map.of("type", "string", "title", "Bucket", "description", "儲存桶名稱"));
        properties.put("key", Map.of(
                "type", "string",
                "title", "Object Key",
                "description", "Path inside the bucket, e.g. backups/2026-08-17.json. 物件路徑"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "format", "textarea",
                "title", "Content",
                "description", "upload: text or base64 data URI. Supports {{expressions}}. 要上傳的內容"
        ));
        properties.put("sourceUrl", Map.of(
                "type", "string",
                "title", "Source URL",
                "description", "upload: fetch this URL and store the response body. 從網址抓檔上傳"
        ));
        properties.put("prefix", Map.of("type", "string", "title", "Prefix", "description", "list 用的路徑前綴"));
        properties.put("maxResults", Map.of("type", "integer", "title", "Max Results", "default", 100));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("bucket")
        );
    }
}
