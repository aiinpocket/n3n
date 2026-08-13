package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.credential.service.CredentialService;
import com.aiinpocket.n3n.site.dto.SiteDeployResponse;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.repository.SiteFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 實驗性：以使用者自己的 Vercel API token 將網站部署到 Vercel。
 * token 來自加密憑證庫（僅擁有者可解密），不落地、不記錄。
 * 使用 Vercel v13 deployments API 的 inline files（base64）模式。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VercelDeployService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final List<String> TOKEN_KEYS = List.of("token", "apiKey", "api_key", "accessToken");

    private final SiteService siteService;
    private final SiteFileRepository siteFileRepository;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;

    @Value("${n3n.sites.vercel-api-base:https://api.vercel.com}")
    private String vercelApiBase;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    /**
     * 部署網站到 Vercel。任何失敗都以 IllegalStateException 帶明確訊息拋出，
     * 由 controller 轉為 4xx/5xx 回應。
     */
    public SiteDeployResponse deployToVercel(UUID siteId, UUID ownerId, UUID credentialId) {
        Site site = siteService.getOwned(siteId, ownerId);
        List<SiteFile> files = siteFileRepository.findBySiteIdOrderByPathAsc(site.getId());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Site has no files to deploy");
        }

        String token = resolveToken(credentialId, ownerId);
        String body = buildDeploymentBody(site, files);

        Request request = new Request.Builder()
                .url(vercelApiBase + "/v13/deployments?skipAutoDetectionConfirmation=1")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("Vercel deploy failed for site {}: HTTP {} {}",
                        siteId, response.code(), truncate(responseBody));
                throw new IllegalStateException(
                        "Vercel API error (HTTP " + response.code() + "): " + extractError(responseBody));
            }
            JsonNode json = objectMapper.readTree(responseBody);
            String url = json.path("url").asText("");
            return SiteDeployResponse.builder()
                    .provider("vercel")
                    .deploymentId(json.path("id").asText(""))
                    .url(url.isBlank() ? "" : "https://" + url)
                    .status(json.path("readyState").asText("QUEUED"))
                    .experimental(true)
                    .build();
        } catch (IOException e) {
            log.warn("Vercel deploy I/O error for site {}", siteId, e);
            throw new IllegalStateException("Failed to reach Vercel API: " + e.getMessage());
        }
    }

    private String resolveToken(UUID credentialId, UUID ownerId) {
        Map<String, Object> data = credentialService.getDecryptedData(credentialId, ownerId);
        for (String key : TOKEN_KEYS) {
            if (data.get(key) instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Credential does not contain a Vercel token (expected one of: " + TOKEN_KEYS + ")");
    }

    private String buildDeploymentBody(Site site, List<SiteFile> files) {
        List<Map<String, Object>> fileEntries = new ArrayList<>(files.size());
        for (SiteFile file : files) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("file", file.getPath());
            entry.put("data", Base64.getEncoder().encodeToString(file.getData()));
            entry.put("encoding", "base64");
            fileEntries.add(entry);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", site.getSlug());
        payload.put("target", "production");
        payload.put("files", fileEntries);
        Map<String, Object> projectSettings = new HashMap<>();
        projectSettings.put("framework", null); // 純靜態網站，不啟用框架建置
        payload.put("projectSettings", projectSettings);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Vercel deployment payload");
        }
    }

    private String extractError(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            String message = json.path("error").path("message").asText("");
            return message.isBlank() ? truncate(responseBody) : message;
        } catch (IOException e) {
            return truncate(responseBody);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
