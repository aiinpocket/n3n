package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /**
     * 模型識別碼
     */
    private String model;

    /**
     * 訊息列表
     */
    private List<AiMessage> messages;

    /**
     * 系統提示（會自動加入訊息列表開頭）
     */
    private String systemPrompt;

    /**
     * 溫度（創意度）0.0 - 1.0
     */
    private Double temperature;

    /**
     * 最大輸出 Token 數
     */
    private Integer maxTokens;

    /**
     * 停止序列
     */
    private List<String> stopSequences;

    /**
     * 供應商特定選項
     */
    private Map<String, Object> providerOptions;
}
