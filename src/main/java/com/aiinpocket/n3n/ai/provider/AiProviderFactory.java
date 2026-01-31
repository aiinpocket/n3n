package com.aiinpocket.n3n.ai.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI Provider 工廠
 * 管理所有可用的 AI 供應商
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiProviderFactory {

    private final List<AiProvider> providers;
    private final Map<String, AiProvider> providerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AiProvider provider : providers) {
            providerMap.put(provider.getProviderId(), provider);
            log.info("Registered AI provider: {} ({})", provider.getProviderId(), provider.getDisplayName());
        }
    }

    /**
     * 取得指定供應商
     * @param providerId 供應商識別碼
     * @return AI Provider
     * @throws IllegalArgumentException 如果供應商不存在
     */
    public AiProvider getProvider(String providerId) {
        AiProvider provider = providerMap.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown AI provider: " + providerId);
        }
        return provider;
    }

    /**
     * 取得所有可用供應商
     */
    public List<AiProvider> getAllProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * 取得所有供應商 ID
     */
    public Set<String> getProviderIds() {
        return Collections.unmodifiableSet(providerMap.keySet());
    }

    /**
     * 檢查供應商是否存在
     */
    public boolean hasProvider(String providerId) {
        return providerMap.containsKey(providerId);
    }
}
