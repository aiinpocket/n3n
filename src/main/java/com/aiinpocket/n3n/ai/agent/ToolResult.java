package com.aiinpocket.n3n.ai.agent;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

/**
 * 工具執行結果
 */
@Data
@Builder
public class ToolResult {

    /** 工具名稱 */
    private String toolName;

    /** 是否成功 */
    private boolean success;

    /** 結果資料 */
    private Map<String, Object> data;

    /** 錯誤訊息 */
    private String error;

    /** 執行時間戳記 */
    @Builder.Default
    private Instant executedAt = Instant.now();

    /** 執行耗時（毫秒） */
    private Long durationMs;

    /**
     * 建立成功結果
     */
    public static ToolResult success(String toolName, Map<String, Object> data) {
        return ToolResult.builder()
            .toolName(toolName)
            .success(true)
            .data(data)
            .build();
    }

    /**
     * 建立失敗結果
     */
    public static ToolResult failure(String toolName, String error) {
        return ToolResult.builder()
            .toolName(toolName)
            .success(false)
            .error(error)
            .build();
    }
}
