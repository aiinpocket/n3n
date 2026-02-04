package com.aiinpocket.n3n.ai.module;

import com.aiinpocket.n3n.ai.entity.AiModuleConfig;
import com.aiinpocket.n3n.ai.failover.FailoverAIProviderWrapper;
import com.aiinpocket.n3n.ai.failover.FailoverConfig;
import com.aiinpocket.n3n.ai.repository.AiModuleConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing Simple AI providers used by the modular AI assistant system.
 * Allows users to configure different AI backends for different features.
 */
@Service
@Slf4j
public class SimpleAIProviderRegistry {

    private final LlamafileSimpleProvider llamafileProvider;
    private final AiModuleConfigRepository configRepository;
    private final FailoverAIProviderWrapper failoverWrapper;

    public SimpleAIProviderRegistry(
            LlamafileSimpleProvider llamafileProvider,
            AiModuleConfigRepository configRepository,
            @Lazy FailoverAIProviderWrapper failoverWrapper) {
        this.llamafileProvider = llamafileProvider;
        this.configRepository = configRepository;
        this.failoverWrapper = failoverWrapper;
    }

    private final Map<String, SimpleAIProvider> staticProviders = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleAIProvider> dynamicProviders = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        staticProviders.put("llamafile", llamafileProvider);
        log.info("SimpleAIProviderRegistry initialized with {} static providers", staticProviders.size());
    }

    /**
     * Get the default provider (Llamafile)
     */
    public SimpleAIProvider getDefaultProvider() {
        return llamafileProvider;
    }

    /**
     * Get provider by name
     */
    public SimpleAIProvider getProvider(String name) {
        SimpleAIProvider provider = staticProviders.get(name);
        if (provider != null) {
            return provider;
        }
        throw new IllegalArgumentException("Unknown provider: " + name);
    }

    /**
     * Get provider by user's configuration ID
     */
    public SimpleAIProvider getProviderByConfigId(UUID configId) {
        SimpleAIProvider cached = dynamicProviders.get(configId);
        if (cached != null) {
            return cached;
        }

        AiModuleConfig config = configRepository.findById(configId)
            .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configId));

        SimpleAIProvider provider = createProviderFromConfig(config);
        dynamicProviders.put(configId, provider);
        return provider;
    }

    /**
     * Get the best available provider for a feature
     * @param feature The feature name (e.g., "flowOptimization", "naturalLanguage")
     * @param userId The user ID for personalized config
     */
    public SimpleAIProvider getProviderForFeature(String feature, UUID userId) {
        if (userId != null) {
            // Try to find user's configured provider for this feature
            Optional<AiModuleConfig> config = configRepository
                .findByUserIdAndFeatureAndIsActiveTrue(userId, feature);

            if (config.isPresent()) {
                return getProviderByConfigId(config.get().getId());
            }
        }

        // Fall back to default (Llamafile)
        return getDefaultProvider();
    }

    /**
     * List all available providers
     */
    public List<SimpleAIProvider> listAvailableProviders() {
        return staticProviders.values().stream()
            .filter(SimpleAIProvider::isAvailable)
            .toList();
    }

    /**
     * Clear cached dynamic providers (call when config changes)
     */
    public void clearCache(UUID configId) {
        dynamicProviders.remove(configId);
    }

    private SimpleAIProvider createProviderFromConfig(AiModuleConfig config) {
        return switch (config.getProviderType()) {
            case "openai" -> new OpenAICompatibleSimpleProvider(
                "openai",
                "https://api.openai.com",
                config.getApiKey(),
                config.getModel(),
                config.getTimeoutMs()
            );
            case "ollama" -> new OpenAICompatibleSimpleProvider(
                "ollama",
                config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:11434",
                null,
                config.getModel(),
                config.getTimeoutMs()
            );
            case "gemini" -> new OpenAICompatibleSimpleProvider(
                "gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                config.getApiKey(),
                config.getModel(),
                config.getTimeoutMs()
            );
            case "claude" -> new OpenAICompatibleSimpleProvider(
                "claude",
                "https://api.anthropic.com",
                config.getApiKey(),
                config.getModel(),
                config.getTimeoutMs()
            );
            case "llamafile" -> llamafileProvider;
            default -> throw new IllegalArgumentException("Unsupported provider type: " + config.getProviderType());
        };
    }

    // ==================== Failover Methods ====================

    /**
     * 使用 Failover 機制進行 AI 對話
     * 當主要 Provider 失敗時，自動切換到備用 Provider
     *
     * @param prompt 提示詞
     * @param systemPrompt 系統提示詞
     * @param userId 使用者 ID
     * @return AI 回應
     */
    public String chatWithFailover(String prompt, String systemPrompt, UUID userId) {
        FailoverConfig config = loadUserFailoverConfig(userId);
        return failoverWrapper.chatWithFailover(prompt, systemPrompt, config, userId);
    }

    /**
     * 使用 Failover 機制進行 AI 對話（帶有額外參數）
     */
    public String chatWithFailover(String prompt, String systemPrompt, int maxTokens, double temperature, UUID userId) {
        FailoverConfig config = loadUserFailoverConfig(userId);
        return failoverWrapper.chatWithFailover(prompt, systemPrompt, maxTokens, temperature, config, userId);
    }

    /**
     * 使用自訂 Failover 設定進行 AI 對話
     */
    public String chatWithFailover(String prompt, String systemPrompt, FailoverConfig config, UUID userId) {
        return failoverWrapper.chatWithFailover(prompt, systemPrompt, config, userId);
    }

    /**
     * 載入使用者的 Failover 設定
     */
    private FailoverConfig loadUserFailoverConfig(UUID userId) {
        if (userId != null) {
            // 嘗試從使用者的預設配置中取得 Failover 設定
            Optional<AiModuleConfig> config = configRepository
                .findByUserIdAndFeatureAndIsActiveTrue(userId, "default");

            if (config.isPresent() && config.get().getFailoverConfig() != null) {
                return config.get().getFailoverConfig();
            }
        }

        // 使用預設設定
        return FailoverConfig.defaultConfig();
    }

    /**
     * 重置指定 Provider 的熔斷器
     */
    public void resetCircuitBreaker(String providerName) {
        failoverWrapper.resetCircuitBreaker(providerName);
    }

    /**
     * 重置所有熔斷器
     */
    public void resetAllCircuitBreakers() {
        failoverWrapper.resetAllCircuitBreakers();
    }
}
