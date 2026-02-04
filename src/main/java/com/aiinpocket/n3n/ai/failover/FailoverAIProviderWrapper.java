package com.aiinpocket.n3n.ai.failover;

import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Provider Failover 包裝器
 * 當主要 Provider 失敗時，自動切換到備用 Provider
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailoverAIProviderWrapper {

    private final SimpleAIProviderRegistry providerRegistry;

    /**
     * 每個 Provider 的熔斷器
     */
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * 使用 Failover 機制進行 AI 對話
     *
     * @param prompt 提示詞
     * @param systemPrompt 系統提示詞
     * @param config Failover 設定
     * @param userId 使用者 ID（用於取得使用者配置的 Provider）
     * @return AI 回應
     */
    public String chatWithFailover(String prompt, String systemPrompt, FailoverConfig config, UUID userId) {
        List<ProviderAttempt> attempts = new ArrayList<>();

        for (String providerName : config.getProviderOrder()) {
            CircuitBreaker breaker = getOrCreateCircuitBreaker(providerName, config);

            // 檢查熔斷器狀態
            if (config.isEnableCircuitBreaker() && breaker.isOpen()) {
                log.debug("Circuit breaker open for provider: {}, skipping", providerName);
                attempts.add(new ProviderAttempt(providerName, false, "Circuit breaker open"));
                continue;
            }

            // 解析 Provider
            SimpleAIProvider provider;
            try {
                provider = resolveProvider(providerName, userId);
            } catch (Exception e) {
                log.debug("Failed to resolve provider {}: {}", providerName, e.getMessage());
                attempts.add(new ProviderAttempt(providerName, false, "Provider not found: " + e.getMessage()));
                continue;
            }

            // 檢查 Provider 是否可用
            if (provider == null || !provider.isAvailable()) {
                log.debug("Provider {} is not available", providerName);
                attempts.add(new ProviderAttempt(providerName, false, "Provider not available"));
                continue;
            }

            // 嘗試呼叫 Provider（含重試）
            for (int retry = 0; retry <= config.getMaxRetries(); retry++) {
                try {
                    log.debug("Attempting provider {} (attempt {}/{})", providerName, retry + 1, config.getMaxRetries() + 1);

                    String result = provider.chat(prompt, systemPrompt);

                    // 成功
                    breaker.recordSuccess();
                    log.info("Successfully completed request using provider: {}", providerName);
                    return result;

                } catch (Exception e) {
                    log.warn("Provider {} failed (attempt {}/{}): {}",
                        providerName, retry + 1, config.getMaxRetries() + 1, e.getMessage());

                    if (retry < config.getMaxRetries() && config.getRetryDelayMs() > 0) {
                        try {
                            Thread.sleep(config.getRetryDelayMs());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new AIProviderFailoverException("Interrupted during retry", attempts);
                        }
                    }
                }
            }

            // 所有重試失敗
            breaker.recordFailure();
            attempts.add(new ProviderAttempt(providerName, false, "All retries failed"));
        }

        // 所有 Provider 都失敗
        throw new AIProviderFailoverException("All providers failed", attempts);
    }

    /**
     * 使用 Failover 機制進行 AI 對話（帶有額外參數）
     */
    public String chatWithFailover(String prompt, String systemPrompt, int maxTokens, double temperature,
                                   FailoverConfig config, UUID userId) {
        List<ProviderAttempt> attempts = new ArrayList<>();

        for (String providerName : config.getProviderOrder()) {
            CircuitBreaker breaker = getOrCreateCircuitBreaker(providerName, config);

            if (config.isEnableCircuitBreaker() && breaker.isOpen()) {
                attempts.add(new ProviderAttempt(providerName, false, "Circuit breaker open"));
                continue;
            }

            SimpleAIProvider provider;
            try {
                provider = resolveProvider(providerName, userId);
            } catch (Exception e) {
                attempts.add(new ProviderAttempt(providerName, false, "Provider not found"));
                continue;
            }

            if (provider == null || !provider.isAvailable()) {
                attempts.add(new ProviderAttempt(providerName, false, "Provider not available"));
                continue;
            }

            for (int retry = 0; retry <= config.getMaxRetries(); retry++) {
                try {
                    String result = provider.chat(prompt, systemPrompt, maxTokens, temperature);
                    breaker.recordSuccess();
                    log.info("Successfully completed request using provider: {}", providerName);
                    return result;
                } catch (Exception e) {
                    log.warn("Provider {} failed: {}", providerName, e.getMessage());
                    if (retry < config.getMaxRetries() && config.getRetryDelayMs() > 0) {
                        try {
                            Thread.sleep(config.getRetryDelayMs());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new AIProviderFailoverException("Interrupted during retry", attempts);
                        }
                    }
                }
            }

            breaker.recordFailure();
            attempts.add(new ProviderAttempt(providerName, false, "All retries failed"));
        }

        throw new AIProviderFailoverException("All providers failed", attempts);
    }

    /**
     * 解析 Provider
     */
    private SimpleAIProvider resolveProvider(String name, UUID userId) {
        if ("user_default".equals(name)) {
            return providerRegistry.getProviderForFeature("default", userId);
        }
        return providerRegistry.getProvider(name);
    }

    /**
     * 取得或建立熔斷器
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String providerName, FailoverConfig config) {
        return circuitBreakers.computeIfAbsent(providerName, k ->
            new CircuitBreaker(
                providerName,
                config.getCircuitBreakerThreshold(),
                config.getCircuitBreakerResetMs()
            )
        );
    }

    /**
     * 重置指定 Provider 的熔斷器
     */
    public void resetCircuitBreaker(String providerName) {
        CircuitBreaker breaker = circuitBreakers.get(providerName);
        if (breaker != null) {
            breaker.reset();
            log.info("Circuit breaker reset for provider: {}", providerName);
        }
    }

    /**
     * 重置所有熔斷器
     */
    public void resetAllCircuitBreakers() {
        circuitBreakers.values().forEach(CircuitBreaker::reset);
        log.info("All circuit breakers reset");
    }

    /**
     * 取得熔斷器狀態
     */
    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        Map<String, CircuitBreaker.State> states = new ConcurrentHashMap<>();
        circuitBreakers.forEach((name, breaker) -> states.put(name, breaker.getState()));
        return states;
    }

    /**
     * Provider 嘗試記錄
     */
    public record ProviderAttempt(String providerName, boolean success, String message) {}
}
