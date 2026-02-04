package com.aiinpocket.n3n.ai.agent;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

/**
 * Agent 串流回應片段
 */
@Data
@Builder
public class AgentStreamChunk {

    /** 片段類型 */
    private ChunkType type;

    /** 文字內容 */
    private String text;

    /** 結構化資料 */
    private Map<String, Object> structuredData;

    /** 時間戳記 */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** 進度百分比（可選） */
    private Integer progress;

    /** 當前階段描述 */
    private String stage;

    public enum ChunkType {
        THINKING,    // AI 思考中
        TEXT,        // 文字回應
        STRUCTURED,  // 結構化資料（如流程定義）
        PROGRESS,    // 進度更新
        ERROR,       // 錯誤
        DONE         // 完成
    }

    /**
     * 建立思考中片段
     */
    public static AgentStreamChunk thinking(String message) {
        return AgentStreamChunk.builder()
            .type(ChunkType.THINKING)
            .text(message)
            .build();
    }

    /**
     * 建立文字片段
     */
    public static AgentStreamChunk text(String content) {
        return AgentStreamChunk.builder()
            .type(ChunkType.TEXT)
            .text(content)
            .build();
    }

    /**
     * 建立結構化資料片段
     */
    public static AgentStreamChunk structured(Map<String, Object> data) {
        return AgentStreamChunk.builder()
            .type(ChunkType.STRUCTURED)
            .structuredData(data)
            .build();
    }

    /**
     * 建立進度片段
     */
    public static AgentStreamChunk progress(int percent, String stage) {
        return AgentStreamChunk.builder()
            .type(ChunkType.PROGRESS)
            .progress(percent)
            .stage(stage)
            .build();
    }

    /**
     * 建立錯誤片段
     */
    public static AgentStreamChunk error(String message) {
        return AgentStreamChunk.builder()
            .type(ChunkType.ERROR)
            .text(message)
            .build();
    }

    /**
     * 建立完成片段
     */
    public static AgentStreamChunk done() {
        return AgentStreamChunk.builder()
            .type(ChunkType.DONE)
            .build();
    }
}
