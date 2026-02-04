package com.aiinpocket.n3n.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * AI 對話串流回應片段
 * 用於 SSE 傳輸
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatStreamChunk {

    /**
     * 片段類型
     */
    private String type;

    /**
     * 文字內容
     */
    private String text;

    /**
     * 結構化資料
     */
    private Map<String, Object> structuredData;

    /**
     * 進度百分比
     */
    private Integer progress;

    /**
     * 當前階段
     */
    private String stage;

    /**
     * 時間戳記
     */
    @Builder.Default
    private String timestamp = Instant.now().toString();

    /**
     * 建立思考中片段
     */
    public static ChatStreamChunk thinking(String message) {
        return ChatStreamChunk.builder()
            .type("thinking")
            .text(message)
            .build();
    }

    /**
     * 建立文字片段
     */
    public static ChatStreamChunk text(String content) {
        return ChatStreamChunk.builder()
            .type("text")
            .text(content)
            .build();
    }

    /**
     * 建立結構化資料片段
     */
    public static ChatStreamChunk structured(Map<String, Object> data) {
        return ChatStreamChunk.builder()
            .type("structured")
            .structuredData(data)
            .build();
    }

    /**
     * 建立進度片段
     */
    public static ChatStreamChunk progress(int percent, String stage) {
        return ChatStreamChunk.builder()
            .type("progress")
            .progress(percent)
            .stage(stage)
            .build();
    }

    /**
     * 建立錯誤片段
     */
    public static ChatStreamChunk error(String message) {
        return ChatStreamChunk.builder()
            .type("error")
            .text(message)
            .build();
    }

    /**
     * 建立完成片段
     */
    public static ChatStreamChunk done() {
        return ChatStreamChunk.builder()
            .type("done")
            .build();
    }
}
