package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 串流輸出片段
 * 用於 AI 節點的串流回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {

    /**
     * 片段類型
     * - "text": 文字內容
     * - "thinking": AI 思考過程
     * - "tool_call": 工具調用
     * - "tool_result": 工具執行結果
     * - "progress": 進度更新
     * - "done": 完成
     * - "error": 錯誤
     */
    private String type;

    /**
     * 文字內容（增量）
     */
    private String content;

    /**
     * 工具名稱（tool_call 時使用）
     */
    private String toolName;

    /**
     * 工具輸入 JSON（tool_call 時使用）
     */
    private String toolInput;

    /**
     * 工具調用 ID（tool_call/tool_result 時使用）
     */
    private String toolCallId;

    /**
     * 進度百分比（0-100）
     */
    private Integer progress;

    /**
     * 進度階段描述
     */
    private String stage;

    /**
     * 是否完成
     */
    @Builder.Default
    private boolean done = false;

    /**
     * 元數據
     */
    private Map<String, Object> metadata;

    // ===== 靜態工廠方法 =====

    public static StreamChunk text(String content) {
        return StreamChunk.builder()
            .type("text")
            .content(content)
            .build();
    }

    public static StreamChunk thinking(String content) {
        return StreamChunk.builder()
            .type("thinking")
            .content(content)
            .build();
    }

    public static StreamChunk toolCall(String toolCallId, String toolName, String toolInput) {
        return StreamChunk.builder()
            .type("tool_call")
            .toolCallId(toolCallId)
            .toolName(toolName)
            .toolInput(toolInput)
            .build();
    }

    public static StreamChunk toolResult(String toolCallId, String content) {
        return StreamChunk.builder()
            .type("tool_result")
            .toolCallId(toolCallId)
            .content(content)
            .build();
    }

    public static StreamChunk progress(int progress, String stage) {
        return StreamChunk.builder()
            .type("progress")
            .progress(progress)
            .stage(stage)
            .build();
    }

    public static StreamChunk done() {
        return StreamChunk.builder()
            .type("done")
            .done(true)
            .build();
    }

    public static StreamChunk done(Map<String, Object> metadata) {
        return StreamChunk.builder()
            .type("done")
            .done(true)
            .metadata(metadata)
            .build();
    }

    public static StreamChunk error(String message) {
        return StreamChunk.builder()
            .type("error")
            .content(message)
            .done(true)
            .build();
    }
}
