package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.request.CreateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.request.UpdateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.response.*;
import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.service.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Provider 管理服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProviderService {

    private final AiProviderConfigRepository configRepository;
    private final AiProviderFactory providerFactory;
    private final CredentialService credentialService;

    /**
     * 列出所有可用的 Provider 類型
     */
    public List<ProviderTypeResponse> listProviderTypes() {
        return providerFactory.getAllProviders().stream()
                .map(ProviderTypeResponse::from)
                .toList();
    }

    /**
     * 列出使用者的所有 AI Provider 設定
     */
    public List<AiProviderConfigResponse> listUserConfigs(UUID userId) {
        return configRepository.findByOwnerIdAndIsActiveTrue(userId).stream()
                .map(AiProviderConfigResponse::from)
                .toList();
    }

    /**
     * 取得使用者的預設 AI Provider 設定
     */
    public AiProviderConfigResponse getDefaultConfig(UUID userId) {
        return configRepository.findByOwnerIdAndIsDefaultTrue(userId)
                .map(AiProviderConfigResponse::from)
                .orElse(null);
    }

    /**
     * 取得指定的 AI Provider 設定
     */
    public AiProviderConfigResponse getConfig(UUID configId, UUID userId) {
        return configRepository.findByIdAndOwnerId(configId, userId)
                .map(AiProviderConfigResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));
    }

    /**
     * 建立 AI Provider 設定
     */
    @Transactional
    public AiProviderConfigResponse createConfig(CreateAiProviderRequest request, UUID userId) {
        // 驗證 Provider 類型
        AiProvider provider = providerFactory.getProvider(request.getProvider());

        // 檢查名稱是否重複
        if (configRepository.existsByOwnerIdAndName(userId, request.getName())) {
            throw new IllegalArgumentException("AI Provider with this name already exists");
        }

        // 建立 Credential（如果有 API Key）
        UUID credentialId = null;
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            credentialId = createAiCredential(request.getProvider(), request.getName(), request.getApiKey(), userId);
        } else if (provider.requiresApiKey()) {
            throw new IllegalArgumentException("API Key is required for " + provider.getDisplayName());
        }

        // 如果設為預設，清除其他預設
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            configRepository.clearDefaultForUser(userId);
        }

        // 建立設定
        AiProviderConfig config = AiProviderConfig.builder()
                .ownerId(userId)
                .workspaceId(request.getWorkspaceId())
                .provider(request.getProvider())
                .name(request.getName())
                .description(request.getDescription())
                .credentialId(credentialId)
                .baseUrl(request.getBaseUrl())
                .defaultModel(request.getDefaultModel())
                .settings(request.getSettings() != null ? request.getSettings() : Map.of())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        config = configRepository.save(config);
        log.info("Created AI provider config: id={}, provider={}, user={}",
                config.getId(), config.getProvider(), userId);

        return AiProviderConfigResponse.from(config);
    }

    /**
     * 更新 AI Provider 設定
     */
    @Transactional
    public AiProviderConfigResponse updateConfig(UUID configId, UpdateAiProviderRequest request, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(request.getBaseUrl());
        }
        if (request.getDefaultModel() != null) {
            config.setDefaultModel(request.getDefaultModel());
        }
        if (request.getSettings() != null) {
            config.setSettings(request.getSettings());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }

        // 更新 API Key（如果有提供）
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            // 刪除舊的 credential
            if (config.getCredentialId() != null) {
                try {
                    credentialService.deleteCredential(config.getCredentialId(), userId);
                } catch (Exception e) {
                    log.warn("Failed to delete old credential: {}", config.getCredentialId(), e);
                }
            }
            // 建立新的 credential
            UUID newCredentialId = createAiCredential(config.getProvider(), config.getName(), request.getApiKey(), userId);
            config.setCredentialId(newCredentialId);
        }

        config = configRepository.save(config);
        log.info("Updated AI provider config: id={}", configId);

        return AiProviderConfigResponse.from(config);
    }

    /**
     * 刪除 AI Provider 設定
     */
    @Transactional
    public void deleteConfig(UUID configId, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        // 刪除關聯的 credential
        if (config.getCredentialId() != null) {
            try {
                credentialService.deleteCredential(config.getCredentialId(), userId);
            } catch (Exception e) {
                log.warn("Failed to delete credential: {}", config.getCredentialId(), e);
            }
        }

        configRepository.delete(config);
        log.info("Deleted AI provider config: id={}", configId);
    }

    /**
     * 設為預設
     */
    @Transactional
    public void setAsDefault(UUID configId, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        // 清除其他預設
        configRepository.clearDefaultForUser(userId);

        // 設為預設
        config.setIsDefault(true);
        configRepository.save(config);

        log.info("Set AI provider config as default: id={}", configId);
    }

    /**
     * 測試連線
     */
    public TestConnectionResponse testConnection(UUID configId, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config, userId);

        long startTime = System.currentTimeMillis();
        try {
            boolean success = provider.testConnection(apiKey, config.getBaseUrl()).get();
            long latency = System.currentTimeMillis() - startTime;

            if (success) {
                return TestConnectionResponse.success(latency);
            } else {
                return TestConnectionResponse.failed("連線失敗");
            }
        } catch (Exception e) {
            log.error("Connection test failed for config {}", configId, e);
            return TestConnectionResponse.failed("連線失敗: " + e.getMessage());
        }
    }

    /**
     * 取得可用模型清單
     */
    public List<AiModelResponse> fetchModels(UUID configId, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config, userId);

        try {
            List<AiModel> models = provider.fetchModels(apiKey, config.getBaseUrl()).get();
            return models.stream()
                    .map(AiModelResponse::from)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch models for config {}", configId, e);
            throw new RuntimeException("Failed to fetch models: " + e.getMessage(), e);
        }
    }

    /**
     * 直接用 API Key 取得模型清單（建立設定前使用）
     */
    public List<AiModelResponse> fetchModelsWithKey(String providerId, String apiKey, String baseUrl) {
        AiProvider provider = providerFactory.getProvider(providerId);

        try {
            List<AiModel> models = provider.fetchModels(apiKey, baseUrl).get();
            return models.stream()
                    .map(AiModelResponse::from)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch models for provider {}", providerId, e);
            throw new RuntimeException("Failed to fetch models: " + e.getMessage(), e);
        }
    }

    /**
     * 取得 Provider Settings（用於執行 AI 請求）
     */
    public AiProviderSettings getProviderSettings(UUID configId, UUID userId) {
        AiProviderConfig config = configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config, userId);

        return AiProviderSettings.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : provider.getDefaultBaseUrl())
                .timeoutMs(provider.getDefaultTimeoutMs())
                .build();
    }

    /**
     * 取得 Provider 設定（內部使用）
     */
    public AiProviderConfig getConfigEntity(UUID configId, UUID userId) {
        return configRepository.findByIdAndOwnerId(configId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));
    }

    private UUID createAiCredential(String provider, String configName, String apiKey, UUID userId) {
        String credentialType = "ai_" + provider;
        CreateCredentialRequest credReq = new CreateCredentialRequest();
        credReq.setName(configName + " - API Key");
        credReq.setType(credentialType);
        credReq.setVisibility("private");
        credReq.setData(Map.of("apiKey", apiKey));

        try {
            CredentialResponse cred = credentialService.createCredential(credReq, userId);
            return cred.getId();
        } catch (Exception e) {
            log.error("Failed to create credential for AI provider", e);
            throw new RuntimeException("Failed to save API key: " + e.getMessage(), e);
        }
    }

    private String getDecryptedApiKey(AiProviderConfig config, UUID userId) {
        if (config.getCredentialId() == null) {
            AiProvider provider = providerFactory.getProvider(config.getProvider());
            if (provider.requiresApiKey()) {
                throw new IllegalStateException("No API key configured for " + config.getName());
            }
            return "";
        }

        Map<String, Object> credData = credentialService.getDecryptedData(config.getCredentialId(), userId);
        Object apiKey = credData.get("apiKey");
        return apiKey != null ? apiKey.toString() : "";
    }
}
