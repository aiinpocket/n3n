package com.aiinpocket.n3n.ai.module;

import com.aiinpocket.n3n.ai.entity.AiModuleConfig;
import com.aiinpocket.n3n.ai.repository.AiModuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing Simple AI providers used by the modular AI assistant system.
 * Allows users to configure different AI backends for different features.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleAIProviderRegistry {

    private final LlamafileSimpleProvider llamafileProvider;
    private final AiModuleConfigRepository configRepository;

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
}
