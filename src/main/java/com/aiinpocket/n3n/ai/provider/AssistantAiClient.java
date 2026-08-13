package com.aiinpocket.n3n.ai.provider;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.ai.service.AiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AI 助手統一入口（facade）。
 *
 * 所有「平台助手類」AI 呼叫（流程生成、意圖分析、對話摘要、記憶萃取、
 * 程式碼生成、AI Transform 節點等）都經由此類別，
 * 使用與 AI 節點相同的平台共用 Provider 設定（AI 設定頁面管理）。
 *
 * 設定解析順序沿用 {@link AiProviderService#resolveConfigForExecution(UUID)}：
 * 平台共用預設 → 任一平台共用啟用設定 →（相容舊資料）使用者自己的設定。
 *
 * Failover：主要設定呼叫失敗時，改用「另一個」平台共用啟用設定再試一次；
 * 兩者都失敗才拋出例外。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantAiClient {

    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final double DEFAULT_TEMPERATURE = 0.7;

    /** 單次 AI 呼叫的等待上限（秒） */
    private static final long CHAT_TIMEOUT_SECONDS = 180;

    private static final String NOT_CONFIGURED_MESSAGE =
        "No AI provider configured. Please configure an AI provider in AI Settings (AI 供應商尚未設定)";

    private final AiProviderService aiProviderService;
    private final AiProviderFactory providerFactory;
    private final AiProviderConfigRepository configRepository;

    /**
     * 是否有可用的 AI Provider 設定（不實際發送請求）。
     */
    public boolean isAvailable(UUID userId) {
        try {
            return aiProviderService.resolveConfigForExecution(userId).isPresent();
        } catch (Exception e) {
            log.debug("AI availability check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 解析目前生效設定的模型名稱（例如給 ContextWindowRegistry 查 context window）。
     */
    public Optional<String> resolveActiveModel(UUID userId) {
        try {
            return aiProviderService.resolveConfigForExecution(userId)
                .map(AiProviderConfig::getDefaultModel);
        } catch (Exception e) {
            log.debug("Failed to resolve active AI model for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 以預設參數進行 AI 對話。
     */
    public String chat(String prompt, String systemPrompt, UUID userId) {
        return chat(prompt, systemPrompt, DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE, userId);
    }

    /**
     * 進行 AI 對話（單輪，回傳純文字）。
     *
     * @param prompt       使用者提示詞
     * @param systemPrompt 系統提示詞（可為 null）
     * @param maxTokens    最大輸出 token 數
     * @param temperature  取樣溫度
     * @param userId       使用者 ID（用於相容舊資料的設定解析）
     * @return AI 回應文字
     * @throws IllegalStateException 尚未設定 Provider，或所有 Provider 都失敗
     */
    public String chat(String prompt, String systemPrompt, int maxTokens, double temperature, UUID userId) {
        AiProviderConfig primary = aiProviderService.resolveConfigForExecution(userId)
            .orElseThrow(() -> new IllegalStateException(NOT_CONFIGURED_MESSAGE));

        try {
            return callProvider(primary, prompt, systemPrompt, maxTokens, temperature);
        } catch (Exception primaryFailure) {
            log.warn("AI provider '{}' ({}) failed: {}",
                primary.getName(), primary.getProvider(), primaryFailure.getMessage());

            Optional<AiProviderConfig> fallback = findFallback(primary);
            if (fallback.isEmpty()) {
                throw new IllegalStateException(
                    "AI provider request failed: " + primaryFailure.getMessage(), primaryFailure);
            }

            AiProviderConfig secondary = fallback.get();
            log.info("Failing over to AI provider '{}' ({})", secondary.getName(), secondary.getProvider());
            try {
                return callProvider(secondary, prompt, systemPrompt, maxTokens, temperature);
            } catch (Exception fallbackFailure) {
                log.error("Fallback AI provider '{}' ({}) also failed: {}",
                    secondary.getName(), secondary.getProvider(), fallbackFailure.getMessage());
                throw new IllegalStateException("All AI providers failed", fallbackFailure);
            }
        }
    }

    private String callProvider(AiProviderConfig config, String prompt, String systemPrompt,
                                int maxTokens, double temperature) throws Exception {
        AiProvider provider = providerFactory.getProvider(config.getProvider());
        AiProviderSettings settings = aiProviderService.buildSettingsFor(config);

        AiChatRequest.AiChatRequestBuilder requestBuilder = AiChatRequest.builder()
            .model(config.getDefaultModel())
            .messages(List.of(AiMessage.user(prompt)))
            .maxTokens(maxTokens)
            .temperature(temperature);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBuilder.systemPrompt(systemPrompt);
        }

        AiResponse response = provider.chat(requestBuilder.build(), settings)
            .get(CHAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (response == null || response.getContent() == null) {
            throw new IllegalStateException("AI provider returned an empty response");
        }
        return response.getContent();
    }

    /**
     * 尋找主要設定以外的另一個平台共用啟用設定（failover 一次）。
     */
    private Optional<AiProviderConfig> findFallback(AiProviderConfig primary) {
        try {
            return configRepository.findByIsSharedTrueAndIsActiveTrue().stream()
                .filter(c -> !c.getId().equals(primary.getId()))
                .findFirst();
        } catch (Exception e) {
            log.debug("Failed to look up fallback AI provider: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
