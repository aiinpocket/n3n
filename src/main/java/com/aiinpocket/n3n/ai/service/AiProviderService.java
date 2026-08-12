package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.request.CreateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.request.UpdateAiProviderRequest;
import com.aiinpocket.n3n.ai.dto.response.*;
import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.backup.event.AiProviderSyncEvent;
import com.aiinpocket.n3n.backup.event.SyncAction;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.service.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI Provider 管理服務。
 *
 * AI Provider API Key 為「平台共用」：由管理員統一設定與管理，
 * 所有成員的 AI 功能共同使用。管理操作（列表/建立/更新/刪除/預設/測試）
 * 皆以平台共用設定為範圍；userId 參數代表「操作者（管理員）」，
 * 僅用於憑證建立歸屬與稽核。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProviderService {

    private final AiProviderConfigRepository configRepository;
    private final AiProviderFactory providerFactory;
    private final CredentialService credentialService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 列出所有可用的 Provider 類型
     */
    @Transactional(readOnly = true)
    public List<ProviderTypeResponse> listProviderTypes() {
        return providerFactory.getAllProviders().stream()
                .map(ProviderTypeResponse::from)
                .toList();
    }

    /**
     * 列出所有平台共用的 AI Provider 設定（管理員視角）
     */
    @Transactional(readOnly = true)
    public List<AiProviderConfigResponse> listUserConfigs(UUID userId) {
        return configRepository.findByIsSharedTrueAndIsActiveTrue().stream()
                .map(AiProviderConfigResponse::from)
                .toList();
    }

    /**
     * 取得平台共用的預設 AI Provider 設定
     */
    @Transactional(readOnly = true)
    public AiProviderConfigResponse getDefaultConfig(UUID userId) {
        return configRepository.findByIsSharedTrueAndIsDefaultTrue()
                .map(AiProviderConfigResponse::from)
                .orElse(null);
    }

    /**
     * 取得指定的平台共用設定
     */
    @Transactional(readOnly = true)
    public AiProviderConfigResponse getConfig(UUID configId, UUID userId) {
        return configRepository.findByIdAndIsSharedTrue(configId)
                .map(AiProviderConfigResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("AI Provider config not found: " + configId));
    }

    /**
     * 建立平台共用的 AI Provider 設定（管理員操作，userId 為建立者）
     */
    @Transactional
    public AiProviderConfigResponse createConfig(CreateAiProviderRequest request, UUID userId) {
        // 驗證 Provider 類型
        AiProvider provider = providerFactory.getProvider(request.getProvider());

        // 檢查名稱是否在平台共用設定中重複
        if (configRepository.existsByIsSharedTrueAndName(request.getName())) {
            throw new IllegalArgumentException("AI Provider with this name already exists");
        }

        // 建立 Credential（如果有 API Key）
        UUID credentialId = null;
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            credentialId = createAiCredential(request.getProvider(), request.getName(), request.getApiKey(), userId);
        } else if (provider.requiresApiKey()) {
            throw new IllegalArgumentException("API Key is required for " + provider.getDisplayName());
        }

        // 如果設為預設，清除其他平台共用預設
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            configRepository.clearDefaultForShared();
        }

        // 建立設定（平台共用；ownerId 記錄建立者）
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
                .isShared(true)
                .build();

        config = configRepository.save(config);
        log.info("Created shared AI provider config: id={}, provider={}, createdBy={}",
                config.getId(), config.getProvider(), userId);
        eventPublisher.publishEvent(new AiProviderSyncEvent(config.getId(), SyncAction.UPSERT, config));

        return AiProviderConfigResponse.from(config);
    }

    /**
     * 更新平台共用的 AI Provider 設定（管理員操作）
     */
    @Transactional
    public AiProviderConfigResponse updateConfig(UUID configId, UpdateAiProviderRequest request, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

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
            // 刪除舊的 credential（憑證屬於原建立者）
            if (config.getCredentialId() != null) {
                try {
                    credentialService.deleteCredential(config.getCredentialId(), config.getOwnerId());
                } catch (Exception e) {
                    log.warn("Failed to delete old credential: {}", config.getCredentialId(), e);
                }
            }
            // 建立新的 credential，並將設定歸屬改為本次操作的管理員，
            // 確保 config.ownerId 與憑證擁有者一致（解密時以 ownerId 為準）
            UUID newCredentialId = createAiCredential(config.getProvider(), config.getName(), request.getApiKey(), userId);
            config.setCredentialId(newCredentialId);
            config.setOwnerId(userId);
        }

        config = configRepository.save(config);
        log.info("Updated shared AI provider config: id={}, by={}", configId, userId);
        eventPublisher.publishEvent(new AiProviderSyncEvent(config.getId(), SyncAction.UPSERT, config));

        return AiProviderConfigResponse.from(config);
    }

    /**
     * 刪除平台共用的 AI Provider 設定（管理員操作）
     */
    @Transactional
    public void deleteConfig(UUID configId, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

        // 刪除關聯的 credential（憑證屬於原建立者）
        if (config.getCredentialId() != null) {
            try {
                credentialService.deleteCredential(config.getCredentialId(), config.getOwnerId());
            } catch (Exception e) {
                log.warn("Failed to delete credential: {}", config.getCredentialId(), e);
            }
        }

        configRepository.delete(config);
        log.info("Deleted shared AI provider config: id={}, by={}", configId, userId);
        eventPublisher.publishEvent(new AiProviderSyncEvent(configId, SyncAction.DELETE, null));
    }

    /**
     * 設為平台預設（平台僅一個預設）
     */
    @Transactional
    public void setAsDefault(UUID configId, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

        // 清除其他平台共用預設
        configRepository.clearDefaultForShared();

        // 設為預設
        config.setIsDefault(true);
        configRepository.save(config);

        log.info("Set shared AI provider config as default: id={}", configId);
    }

    /**
     * 測試連線
     */
    public TestConnectionResponse testConnection(UUID configId, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config);

        long startTime = System.currentTimeMillis();
        try {
            boolean success = provider.testConnection(apiKey, config.getBaseUrl()).get(30, TimeUnit.SECONDS);
            long latency = System.currentTimeMillis() - startTime;

            if (success) {
                return TestConnectionResponse.success(latency);
            } else {
                return TestConnectionResponse.failed("連線失敗");
            }
        } catch (Exception e) {
            log.error("Connection test failed for config {}", configId, e);
            return TestConnectionResponse.failed("連線失敗");
        }
    }

    /**
     * 取得可用模型清單
     */
    @Transactional(readOnly = true)
    public List<AiModelResponse> fetchModels(UUID configId, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config);

        try {
            List<AiModel> models = provider.fetchModels(apiKey, config.getBaseUrl()).get(30, TimeUnit.SECONDS);
            return models.stream()
                    .map(AiModelResponse::from)
                    .toList();
        } catch (TimeoutException e) {
            log.error("Timeout fetching models for config {}", configId);
            throw new RuntimeException("Failed to fetch models: request timed out", e);
        } catch (Exception e) {
            log.error("Failed to fetch models for config {}", configId, e);
            throw new RuntimeException("Failed to fetch models", e);
        }
    }

    /**
     * 直接用 API Key 取得模型清單（建立設定前使用）
     */
    public List<AiModelResponse> fetchModelsWithKey(String providerId, String apiKey, String baseUrl) {
        AiProvider provider = providerFactory.getProvider(providerId);

        try {
            List<AiModel> models = provider.fetchModels(apiKey, baseUrl).get(30, TimeUnit.SECONDS);
            return models.stream()
                    .map(AiModelResponse::from)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch models for provider {}", providerId, e);
            throw new RuntimeException("Failed to fetch models", e);
        }
    }

    /**
     * 取得 Provider Settings（用於執行 AI 請求）
     */
    @Transactional(readOnly = true)
    public AiProviderSettings getProviderSettings(UUID configId, UUID userId) {
        AiProviderConfig config = findSharedConfig(configId);

        AiProvider provider = providerFactory.getProvider(config.getProvider());
        String apiKey = getDecryptedApiKey(config);

        return AiProviderSettings.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl() != null ? config.getBaseUrl() : provider.getDefaultBaseUrl())
                .timeoutMs(provider.getDefaultTimeoutMs())
                .build();
    }

    /**
     * 取得 Provider 設定（內部使用）
     */
    @Transactional(readOnly = true)
    public AiProviderConfig getConfigEntity(UUID configId, UUID userId) {
        return findSharedConfig(configId);
    }

    /**
     * 解析執行 AI 節點/助手時要用的設定：
     * 1. 平台共用預設 → 2. 任一平台共用啟用設定
     * → 3.（相容舊資料）使用者自己的預設 → 4. 使用者自己任一啟用設定
     */
    @Transactional(readOnly = true)
    public Optional<AiProviderConfig> resolveConfigForExecution(UUID userId) {
        Optional<AiProviderConfig> shared = configRepository.findByIsSharedTrueAndIsDefaultTrue()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .or(() -> configRepository.findByIsSharedTrueAndIsActiveTrue().stream().findFirst());
        if (shared.isPresent() || userId == null) {
            return shared;
        }
        // 相容 fallback：平台尚未設定共用金鑰時，沿用使用者自己的舊設定
        return configRepository.findByOwnerIdAndIsDefaultTrue(userId)
                .or(() -> configRepository.findByOwnerIdAndIsActiveTrue(userId).stream().findFirst());
    }

    /**
     * 一般成員的 AI 可用性查詢（不含任何秘密資訊）
     */
    @Transactional(readOnly = true)
    public AiAvailabilityResponse getAvailability(UUID userId) {
        return resolveConfigForExecution(userId)
                .map(config -> new AiAvailabilityResponse(true, config.getProvider(), config.getDefaultModel()))
                .orElseGet(() -> new AiAvailabilityResponse(false, null, null));
    }

    /**
     * AI 可用性回應：僅揭露是否已設定與預設模型，不含金鑰
     */
    public record AiAvailabilityResponse(boolean configured, String provider, String defaultModel) {}

    private AiProviderConfig findSharedConfig(UUID configId) {
        return configRepository.findByIdAndIsSharedTrue(configId)
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
            throw new RuntimeException("Failed to save API key", e);
        }
    }

    /**
     * 以設定的 ownerId（建立者 = 憑證擁有者）解密 API Key，
     * 讓平台共用設定可供所有成員的執行流程使用。
     */
    private String getDecryptedApiKey(AiProviderConfig config) {
        if (config.getCredentialId() == null) {
            AiProvider provider = providerFactory.getProvider(config.getProvider());
            if (provider.requiresApiKey()) {
                throw new IllegalStateException("No API key configured for " + config.getName());
            }
            return "";
        }

        Map<String, Object> credData = credentialService.getDecryptedData(config.getCredentialId(), config.getOwnerId());
        Object apiKey = credData.get("apiKey");
        return apiKey != null ? apiKey.toString() : "";
    }
}
