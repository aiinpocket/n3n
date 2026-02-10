package com.aiinpocket.n3n.backup.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * Google Cloud Storage 儲存提供者
 * 使用 OkHttp + Service Account JWT 認證（仿 GoogleCloudStorageNodeHandler 模式）
 */
@Slf4j
public class GcsStorageProvider implements CloudStorageProvider {

    private static final String STORAGE_API_BASE = "https://storage.googleapis.com/storage/v1";
    private static final String UPLOAD_API_BASE = "https://storage.googleapis.com/upload/storage/v1";

    private final String serviceAccountJson;
    private final String bucket;
    private final String basePath;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public GcsStorageProvider(String serviceAccountJson, String bucket, String basePath, ObjectMapper objectMapper) {
        this.serviceAccountJson = serviceAccountJson;
        this.bucket = bucket;
        this.basePath = normalizePath(basePath);
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder().build();
    }

    @Override
    public void upload(String filename, byte[] data) throws IOException {
        try {
            String accessToken = getAccessToken();
            String objectName = basePath + filename;
            String encodedName = URLEncoder.encode(objectName, StandardCharsets.UTF_8);

            String url = UPLOAD_API_BASE + "/b/" + bucket + "/o?uploadType=media&name=" + encodedName;

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .post(RequestBody.create(data, MediaType.parse("application/octet-stream")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("GCS upload failed: " + response.code() + " " + response.message());
                }
            }
            log.info("Uploaded backup to GCS: {}/{}", bucket, objectName);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("GCS upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String filename) throws IOException {
        try {
            String accessToken = getAccessToken();
            String objectName = basePath + filename;
            String encodedName = URLEncoder.encode(objectName, StandardCharsets.UTF_8);

            String url = STORAGE_API_BASE + "/b/" + bucket + "/o/" + encodedName + "?alt=media";

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("GCS download failed: " + response.code());
                }
                return response.body().bytes();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("GCS download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<StorageFileInfo> list(String prefix) throws IOException {
        try {
            String accessToken = getAccessToken();
            String fullPrefix = basePath + (prefix != null ? prefix : "");
            String encodedPrefix = URLEncoder.encode(fullPrefix, StandardCharsets.UTF_8);

            String url = STORAGE_API_BASE + "/b/" + bucket + "/o?prefix=" + encodedPrefix + "&maxResults=1000";

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("GCS list failed: " + response.code());
                }
                Map<String, Object> result = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOrDefault("items", List.of());

                return items.stream()
                        .filter(item -> {
                            String name = (String) item.get("name");
                            return name != null && name.startsWith(basePath);
                        })
                        .map(item -> new StorageFileInfo(
                                ((String) item.get("name")).substring(basePath.length()),
                                Long.parseLong(String.valueOf(item.getOrDefault("size", "0"))),
                                (String) item.getOrDefault("updated", "")
                        ))
                        .toList();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("GCS list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        try {
            String accessToken = getAccessToken();
            String objectName = basePath + filename;
            String encodedName = URLEncoder.encode(objectName, StandardCharsets.UTF_8);

            String url = STORAGE_API_BASE + "/b/" + bucket + "/o/" + encodedName;

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .delete()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 404) {
                    throw new IOException("GCS delete failed: " + response.code());
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("GCS delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            String accessToken = getAccessToken();
            String url = STORAGE_API_BASE + "/b/" + bucket;

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("GCS connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderType() {
        return "gcs";
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ========== JWT Auth (from GoogleCloudStorageNodeHandler pattern) ==========

    private String getAccessToken() throws Exception {
        Map<String, Object> sa = objectMapper.readValue(serviceAccountJson, new TypeReference<>() {});
        String clientEmail = (String) sa.get("client_email");
        String privateKey = (String) sa.get("private_key");
        String tokenUri = (String) sa.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "iss", clientEmail,
                "scope", "https://www.googleapis.com/auth/devstorage.full_control",
                "aud", tokenUri,
                "iat", now,
                "exp", now + 3600
        );

        String jwt = createJwt(header, claims, privateKey);

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build();

        Request request = new Request.Builder()
                .url(tokenUri)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body().string(), new TypeReference<>() {});
            return (String) tokenResponse.get("access_token");
        }
    }

    private String createJwt(Map<String, Object> header, Map<String, Object> claims, String privateKeyPem) throws Exception {
        String headerJson = objectMapper.writeValueAsString(header);
        String claimsJson = objectMapper.writeValueAsString(claims);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String claimsBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));

        String signatureInput = headerBase64 + "." + claimsBase64;

        String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyContent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey pk = keyFactory.generatePrivate(keySpec);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pk);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        return signatureInput + "." + signatureBase64;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String p = path.trim();
        if (!p.endsWith("/")) p += "/";
        if (p.startsWith("/")) p = p.substring(1);
        return p;
    }
}
