package com.aiinpocket.n3n.backup.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Cloud Function 閘道儲存提供者（default provider）
 * 透過 HTTP 呼叫 Cloud Function 代理 GCS 操作，fingerprint 即身份認證
 */
@Slf4j
public class N3nCloudProvider implements CloudStorageProvider {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    private final String gatewayUrl;
    private final String fingerprint;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public N3nCloudProvider(String gatewayUrl, String fingerprint, ObjectMapper objectMapper) {
        this.gatewayUrl = gatewayUrl.endsWith("/")
                ? gatewayUrl.substring(0, gatewayUrl.length() - 1)
                : gatewayUrl;
        this.fingerprint = fingerprint;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void upload(String filename, byte[] data) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("filename", filename);
        body.put("data", Base64.getEncoder().encodeToString(data));

        Request request = buildPostRequest("/upload", body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gateway upload failed: " + response.code()
                        + " " + extractErrorMessage(response));
            }
        }
    }

    @Override
    public byte[] download(String filename) throws IOException {
        Map<String, String> body = Map.of("filename", filename);

        Request request = buildPostRequest("/download", body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gateway download failed: " + response.code()
                        + " " + extractErrorMessage(response));
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Gateway returned empty response");
            }
            Map<String, Object> result = objectMapper.readValue(
                    responseBody.string(), new TypeReference<>() {});
            String dataBase64 = (String) result.get("data");
            if (dataBase64 == null) {
                throw new IOException("Gateway response missing 'data' field");
            }
            return Base64.getDecoder().decode(dataBase64);
        }
    }

    @Override
    public List<StorageFileInfo> list(String prefix) throws IOException {
        Map<String, String> body = Map.of("prefix", prefix != null ? prefix : "");

        Request request = buildPostRequest("/list", body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gateway list failed: " + response.code()
                        + " " + extractErrorMessage(response));
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return List.of();
            }
            Map<String, Object> result = objectMapper.readValue(
                    responseBody.string(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
            if (files == null) return List.of();

            List<StorageFileInfo> infos = new ArrayList<>();
            for (Map<String, Object> file : files) {
                infos.add(new StorageFileInfo(
                        (String) file.get("filename"),
                        file.get("size") instanceof Number n ? n.longValue() : 0L,
                        (String) file.get("lastModified")
                ));
            }
            return infos;
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        Map<String, String> body = Map.of("filename", filename);

        Request request = buildPostRequest("/delete", body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gateway delete failed: " + response.code()
                        + " " + extractErrorMessage(response));
            }
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(gatewayUrl + "/health")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("Gateway health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderType() {
        return "default";
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ========== Private helpers ==========

    private Request buildPostRequest(String path, Map<String, String> body) throws IOException {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(body);
        return new Request.Builder()
                .url(gatewayUrl + path)
                .header("Authorization", "Bearer " + fingerprint)
                .post(RequestBody.create(jsonBytes, JSON_TYPE))
                .build();
    }

    private String extractErrorMessage(Response response) {
        try {
            ResponseBody body = response.body();
            if (body != null) {
                Map<String, Object> error = objectMapper.readValue(
                        body.string(), new TypeReference<>() {});
                Object msg = error.get("error");
                if (msg != null) return msg.toString();
            }
        } catch (Exception ignored) {
        }
        return response.message();
    }
}
