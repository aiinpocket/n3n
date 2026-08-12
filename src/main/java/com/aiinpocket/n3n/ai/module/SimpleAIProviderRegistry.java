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

    private final AiModuleConfigRepository configRepository;
    private final FailoverAIProviderWrapper failoverWrapper;

    public SimpleAIProviderRegistry(
            AiModuleConfigRepository configRepository,
            @Lazy FailoverAIProviderWrapper failoverWrapper) {
        this.configRepository = configRepository;
        this.failoverWrapper = failoverWrapper;
    }

    private final Map<String, SimpleAIProvider> staticProviders = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleAIProvider> dynamicProviders = new ConcurrentHashMap<>();

    /**
     * Sentinel provider returned when no AI provider is configured.
     * isAvailable() returns false so callers degrade gracefully;
     * chat() throws a clear error instead of an NPE if invoked anyway.
     */
    private static final SimpleAIProvider UNCONFIGURED_PROVIDER = new SimpleAIProvider() {
        @Override
        public String getName() {
            return "unconfigured";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String chat(String prompt, String systemPrompt, int maxTokens, double temperature) {
            throw new IllegalStateException(
                "No AI provider configured. Please configure an AI provider in AI Settings (AI 供應商尚未設定)");
        }
    };

    @PostConstruct
    public void init() {
        log.info("SimpleAIProviderRegistry initialized ({} static providers)", staticProviders.size());
    }

    /**
     * Get the default provider.
     * Resolves to a platform/user-configured provider if one exists for the "default" feature;
     * otherwise returns an unavailable sentinel (isAvailable() == false) so callers degrade cleanly.
     */
    public SimpleAIProvider getDefaultProvider() {
        return UNCONFIGURED_PROVIDER;
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
        return dynamicProviders.computeIfAbsent(configId, id -> {
            AiModuleConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + id));
            return createProviderFromConfig(config);
        });
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

        // Platform fallback: AI keys are platform-shared (admin-managed).
        // If this user has no config for the feature, reuse the earliest
        // active config anyone (i.e. an admin) created for it.
        Optional<AiModuleConfig> platformConfig = configRepository
            .findByFeatureAndIsActiveTrueOrderByCreatedAtAsc(feature)
            .stream().findFirst();
        if (platformConfig.isPresent()) {
            return getProviderByConfigId(platformConfig.get().getId());
        }

        // No configured provider found — return unavailable sentinel so callers degrade cleanly
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
