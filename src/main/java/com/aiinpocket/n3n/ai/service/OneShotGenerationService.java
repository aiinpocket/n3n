package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.ChatStreamChunk;
import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.service.NodeProbeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 一次性生成：使用者在對話中要的不是流程，而是「現在就給我一個成果」
 * （例如產生一張圖）。像 ChatGPT 一樣即問即生，成果存進作品庫。
 *
 * 與流程編排的分界：有排程／觸發／自動化字眼的是流程；
 * 「幫我生成一張…」這種單次需求走這裡，不建立任何流程。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OneShotGenerationService {

    /** 生成逾時（fal.ai flux/schnell 通常數秒內完成） */
    private static final long GENERATE_TIMEOUT_SECONDS = 120;

    private final AssistantAiClient aiClient;
    private final NodeProbeService nodeProbeService;
    private final AiProviderService aiProviderService;
    private final ArtifactService artifactService;
    private final ObjectMapper objectMapper;

    /** 偵測結果：kind 目前支援 image；prompt 為擷取後的生成描述 */
    public record OneShotRequest(String kind, String prompt) {}

    /** 訊息含媒體生成字眼才進一步用 AI 判斷，避免每則訊息都多一次 AI 呼叫 */
    private static final List<String> MEDIA_HINTS = List.of(
        "圖", "画", "圖片", "照片", "插畫", "海報", "壁紙", "頭像", "梗圖",
        "image", "picture", "photo", "illustration", "poster", "wallpaper", "avatar",
        "画像", "イラスト", "写真"
    );

    /** 這些字眼代表使用者要的是自動化流程，不是一次性生成 */
    private static final List<String> FLOW_HINTS = List.of(
        "流程", "排程", "定時", "每天", "每小時", "每週", "每周", "自動", "觸發", "webhook",
        "workflow", "schedule", "every day", "every hour", "daily", "hourly", "weekly",
        "trigger", "automat", "フロー", "スケジュール", "毎日", "毎時", "自動"
    );

    /**
     * 判斷訊息是否為一次性媒體生成需求。非此類需求回傳 null（走原本的多代理路由）。
     */
    public OneShotRequest detect(String message, UUID userId) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean mentionsMedia = MEDIA_HINTS.stream().anyMatch(lower::contains);
        boolean mentionsFlow = FLOW_HINTS.stream().anyMatch(lower::contains);
        if (!mentionsMedia || mentionsFlow) {
            return null;
        }
        try {
            String prompt = """
                使用者訊息：%s

                判斷這是不是「現在就要一個生成結果」的一次性請求（例如：幫我畫一張圖），
                而不是要建立自動化流程。只回傳 JSON：
                {"oneShot": true|false, "kind": "image"|"none", "prompt": "適合拿去給文生圖模型的英文描述"}
                不確定就回 {"oneShot": false, "kind": "none", "prompt": ""}。
                """.formatted(message.length() > 800 ? message.substring(0, 800) : message);
            String answer = aiClient.chat(prompt, "你是需求分類器，只輸出 JSON。", 300, 0.1, userId,
                com.aiinpocket.n3n.ai.provider.AiTaskType.LIGHT).trim();
            String json = answer.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (Boolean.TRUE.equals(parsed.get("oneShot")) && "image".equals(parsed.get("kind"))
                && parsed.get("prompt") instanceof String p && !p.isBlank()) {
                return new OneShotRequest("image", p);
            }
        } catch (Exception e) {
            log.debug("One-shot detection failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 執行一次性圖片生成：真的呼叫生成模型 → 成果存作品庫（永久，不掛執行紀錄）
     * → 以 structured chunk 回傳 artifact 供前端即時預覽。
     */
    public Flux<ChatStreamChunk> generateStream(UUID userId, OneShotRequest request,
                                                java.util.function.Consumer<String> onAssistantText) {
        Sinks.Many<ChatStreamChunk> sink = Sinks.many().unicast().onBackpressureBuffer();

        Thread.startVirtualThread(() -> {
            try {
                boolean falAvailable = aiProviderService.resolveSharedApiKey("fal").isPresent()
                    || System.getenv("FAL_API_KEY") != null;
                if (!falAvailable) {
                    String text = "我看得出你想直接生成圖片，不過目前還沒設定影像生成服務的金鑰。"
                        + "請管理員到「AI 供應商管理」加入 fal.ai 的共用金鑰後，我就能即時幫你生圖了。";
                    sink.tryEmitNext(ChatStreamChunk.text(text));
                    onAssistantText.accept(text);
                    sink.tryEmitNext(ChatStreamChunk.done());
                    sink.tryEmitComplete();
                    return;
                }

                sink.tryEmitNext(ChatStreamChunk.progress(20, "generating"));
                sink.tryEmitNext(ChatStreamChunk.text("好的，正在為你生成圖片，稍等幾秒…\n"));

                Map<String, Object> config = new HashMap<>();
                config.put("resource", "image");
                config.put("operation", "generate");
                config.put("model", "fal-ai/flux/schnell");
                config.put("prompt", request.prompt());
                config.put("imageSize", "square_hd");
                config.put("numImages", 1);

                NodeProbeService.ProbeResult result = nodeProbeService.probe(
                    userId, "falAi", "one-shot", config, Map.of(), GENERATE_TIMEOUT_SECONDS);

                if (!result.success()) {
                    String text = "生成沒有成功：" + friendly(result.errorMessage())
                        + "\n可以換個描述再試一次。";
                    sink.tryEmitNext(ChatStreamChunk.text(text));
                    onAssistantText.accept(text);
                    sink.tryEmitNext(ChatStreamChunk.done());
                    sink.tryEmitComplete();
                    return;
                }

                // 生成的 artifact 掛在臨時 probeId 下會被孤兒清理回收，
                // 轉為對話產物（executionId=null）永久保存在作品庫
                List<Map<String, Object>> artifacts = claimArtifacts(userId, result.output());

                sink.tryEmitNext(ChatStreamChunk.progress(90, "saving"));
                String text = artifacts.isEmpty()
                    ? "圖片生成完成，但存入作品庫時出了點問題，請再試一次。"
                    : "完成！圖片已生成並存到你的作品庫，隨時可以到「作品庫」查看或下載。";
                sink.tryEmitNext(ChatStreamChunk.text(text));
                onAssistantText.accept(text);

                if (!artifacts.isEmpty()) {
                    sink.tryEmitNext(ChatStreamChunk.structured(Map.of(
                        "action", "artifact_generated",
                        "artifacts", artifacts)));
                }
                sink.tryEmitNext(ChatStreamChunk.done());
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.error("One-shot generation failed", e);
                sink.tryEmitNext(ChatStreamChunk.text("生成過程發生問題，請稍後再試一次。"));
                sink.tryEmitNext(ChatStreamChunk.done());
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    /** 把 probe 產出的 artifacts 轉為對話產物（脫離臨時 execution，永久保存）。 */
    private List<Map<String, Object>> claimArtifacts(UUID userId, Map<String, Object> output) {
        List<Map<String, Object>> claimed = new ArrayList<>();
        Object ids = output != null ? output.get("artifactIds") : null;
        if (!(ids instanceof List<?> idList)) {
            return claimed;
        }
        for (Object idObj : idList) {
            try {
                UUID artifactId = UUID.fromString(String.valueOf(idObj));
                Artifact artifact = artifactService.claimForAssistant(userId, artifactId);
                claimed.add(Map.of(
                    "id", artifact.getId().toString(),
                    "filename", artifact.getFilename(),
                    "mimeType", artifact.getMimeType() != null ? artifact.getMimeType() : "image/png",
                    "downloadUrl", ArtifactService.downloadUrl(artifact.getId())));
            } catch (Exception e) {
                log.warn("Failed to claim one-shot artifact {}: {}", idObj, e.getMessage());
            }
        }
        return claimed;
    }

    private String friendly(String error) {
        if (error == null) return "未知原因";
        if (error.toLowerCase(Locale.ROOT).contains("api key")) {
            return "影像生成服務的金鑰無效或未設定";
        }
        return error;
    }
}
