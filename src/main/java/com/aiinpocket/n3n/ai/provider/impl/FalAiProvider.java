package com.aiinpocket.n3n.ai.provider.impl;

import com.aiinpocket.n3n.ai.provider.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * fal.ai 供應商 — 媒體生成（AI 圖片 / 影片），不支援聊天。
 *
 * <p>在平台 AI 設定輸入 fal.ai 金鑰後，流程中的 falAi 節點（AI Image / Video）
 * 不需個別憑證即可產生圖片與影片；AI 助手也能編排「文字生成影片」類流程。</p>
 */
@Component
@Slf4j
public class FalAiProvider implements AiProvider {

    public static final String PROVIDER_ID = "fal";

    private static final String DEFAULT_BASE_URL = "https://fal.run";
    /** 以佇列狀態端點驗證金鑰：401 表示金鑰無效，其餘（404/422）表示已通過認證。 */
    private static final String AUTH_CHECK_URL =
            "https://queue.fal.run/fal-ai/flux/requests/00000000-0000-0000-0000-000000000000/status";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return "fal.ai (Image / Video)";
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public int getDefaultTimeoutMs() {
        return 300_000;
    }

    @Override
    public boolean supportsChat() {
        return false;
    }

    /**
     * fal.ai 沒有公開的模型列表 API；回傳流程節點支援的媒體生成模型。
     */
    @Override
    public CompletableFuture<List<AiModel>> fetchModels(String apiKey, String baseUrl) {
        List<AiModel> models = List.of(
                mediaModel("fal-ai/flux/schnell", "FLUX Schnell (Image)"),
                mediaModel("fal-ai/flux/dev", "FLUX Dev (Image)"),
                mediaModel("fal-ai/flux-pro/v1.1-ultra", "FLUX Pro 1.1 Ultra (Image)"),
                mediaModel("fal-ai/recraft/v3/text-to-image", "Recraft V3 (Image)"),
                mediaModel("fal-ai/veo3/fast", "Veo 3 Fast (Video)"),
                mediaModel("fal-ai/kling-video/v2.1/standard/text-to-video", "Kling 2.1 (Text to Video)"),
                mediaModel("fal-ai/kling-video/v2.1/standard/image-to-video", "Kling 2.1 (Image to Video)"),
                mediaModel("fal-ai/minimax/hailuo-02/standard/text-to-video", "Hailuo 02 (Text to Video)"),
                mediaModel("fal-ai/wan/v2.2-a14b/text-to-video", "WAN 2.2 (Text to Video)")
        );
        return CompletableFuture.completedFuture(models);
    }

    private AiModel mediaModel(String id, String displayName) {
        return AiModel.builder()
                .id(id)
                .displayName(displayName)
                .providerId(PROVIDER_ID)
                .supportsStreaming(false)
                .capabilities(Map.of("media", true))
                .build();
    }

    @Override
    public CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "fal.ai is a media generation provider and does not support chat"));
    }

    @Override
    public Flux<AiStreamChunk> chatStream(AiChatRequest request, AiProviderSettings settings) {
        return Flux.error(new UnsupportedOperationException(
                "fal.ai is a media generation provider and does not support chat"));
    }

    @Override
    public CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder()
                    .url(AUTH_CHECK_URL)
                    .header("Authorization", "Key " + apiKey)
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                // 401/403 = 金鑰無效；其他狀態（404 請求不存在、422 等）代表已通過認證
                return response.code() != 401 && response.code() != 403;
            } catch (Exception e) {
                log.warn("fal.ai connection test failed: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "apiKey", Map.of(
                                "type", "string",
                                "title", "API Key",
                                "description", "fal.ai API key (https://fal.ai/dashboard/keys)")
                )
        );
    }
}
