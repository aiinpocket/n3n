package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 模型資訊
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModel {

    /**
     * 模型識別碼 (e.g., "claude-3-5-sonnet-20241022")
     */
    private String id;

    /**
     * 模型顯示名稱 (e.g., "Claude 3.5 Sonnet")
     */
    private String displayName;

    /**
     * 所屬供應商
     */
    private String providerId;

    /**
     * 上下文窗口大小（Token 數）
     */
    private int contextWindow;

    /**
     * 最大輸出 Token 數
     */
    private int maxOutputTokens;

    /**
     * 是否支援視覺輸入
     */
    private boolean supportsVision;

    /**
     * 是否支援串流
     */
    @Builder.Default
    private boolean supportsStreaming = true;

    /**
     * 其他能力
     */
    private Map<String, Object> capabilities;
}
