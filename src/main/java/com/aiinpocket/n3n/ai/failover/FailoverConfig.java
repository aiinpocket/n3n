package com.aiinpocket.n3n.ai.failover;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Provider Failover 設定
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailoverConfig {

    /**
     * Provider 優先順序列表
     * 支援的值: "user_default", "llamafile", "openai", "claude", "gemini", "ollama"
     */
    @Builder.Default
    private List<String> providerOrder = List.of("user_default", "llamafile");

    /**
     * 每個 Provider 的最大重試次數
     */
    @Builder.Default
    private int maxRetries = 2;

    /**
     * 重試間隔（毫秒）
     */
    @Builder.Default
    private long retryDelayMs = 1000;

    /**
     * 是否啟用熔斷器
     */
    @Builder.Default
    private boolean enableCircuitBreaker = true;

    /**
     * 熔斷器閾值（連續失敗次數）
     */
    @Builder.Default
    private int circuitBreakerThreshold = 3;

    /**
     * 熔斷器重置時間（毫秒）
     */
    @Builder.Default
    private long circuitBreakerResetMs = 60000;

    /**
     * 建立預設設定
     */
    public static FailoverConfig defaultConfig() {
        return FailoverConfig.builder().build();
    }

    /**
     * 建立僅使用本地 Llamafile 的設定
     */
    public static FailoverConfig localOnly() {
        return FailoverConfig.builder()
            .providerOrder(List.of("llamafile"))
            .enableCircuitBreaker(false)
            .build();
    }
}
