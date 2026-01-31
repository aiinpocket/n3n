package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AI 供應商設定（執行時使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderSettings {

    /**
     * API 金鑰
     */
    private String apiKey;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * 超時時間（毫秒）
     */
    @Builder.Default
    private int timeoutMs = 120000;

    /**
     * 最大重試次數
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 額外 HTTP Headers
     */
    private Map<String, String> headers;
}
