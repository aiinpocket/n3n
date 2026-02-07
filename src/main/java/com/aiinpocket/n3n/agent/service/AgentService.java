package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.entity.AgentConversation;
import com.aiinpocket.n3n.agent.entity.AgentConversation.MessageRole;
import com.aiinpocket.n3n.agent.entity.AgentMessage;
import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.exception.AiProviderException;
import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.ai.security.AiRateLimiter;
import com.aiinpocket.n3n.ai.security.PromptSanitizer;
import com.aiinpocket.n3n.credential.service.CredentialService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent 服務
 *
 * 負責與 AI Provider 互動，處理對話、推薦元件、生成流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ConversationService conversationService;
    private final AiProviderConfigRepository configRepository;
    private final AiProviderFactory providerFactory;
    private final CredentialService credentialService;
    private final PromptSanitizer promptSanitizer;
    private final AiRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
        "```(?:json)?\\s*([\\s\\S]*?)```",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 發送訊息並取得 AI 回應
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @param userMessage 使用者訊息
     * @return AI 回應
     */
    public CompletableFuture<AgentResponse> chat(UUID userId, UUID conversationId, String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 驗證並消毒使用者輸入
                String sanitizedMessage = promptSanitizer.sanitize(userMessage);

                // 2. Rate limit 檢查
                rateLimiter.checkRequestLimit(userId);
                int estimatedTokens = estimateTokens(sanitizedMessage);
                rateLimiter.checkTokenLimit(userId, estimatedTokens);

                // 3. 儲存使用者訊息
                conversationService.addUserMessage(userId, conversationId, sanitizedMessage);

                // 4. 取得對話歷史
                AgentConversation conversation = conversationService.getConversation(userId, conversationId);
                List<AiMessage> messages = buildAiMessages(conversation);

                // 5. 取得使用者的 AI 設定
                AiProviderConfig config = getDefaultConfig(userId);
                AiProvider provider = providerFactory.getProvider(config.getProvider());
                AiProviderSettings settings = buildSettings(config, userId);

                // 6. 發送請求給 AI
                long startTime = Instant.now().toEpochMilli();

                AiChatRequest request = AiChatRequest.builder()
                    .model(config.getDefaultModel())
                    .messages(messages)
                    .temperature(getTemperature(config))
                    .maxTokens(4096)
                    .build();

                AiResponse aiResponse = provider.chat(request, settings).join();

                long latencyMs = Instant.now().toEpochMilli() - startTime;

                // 7. 解析 AI 回應
                String content = aiResponse.getContent();
                Map<String, Object> structuredData = extractStructuredData(content);

                // 8. 消毒輸出
                String sanitizedContent = promptSanitizer.sanitizeOutput(content);

                // 9. 儲存 AI 回應
                AiUsage usage = aiResponse.getUsage();
                int tokenCount = usage != null ? usage.getTotalTokens() : 0;

                AgentMessage assistantMessage = conversationService.addAssistantMessage(
                    conversationId,
                    sanitizedContent,
                    structuredData,
                    aiResponse.getModel(),
                    tokenCount,
                    latencyMs
                );

                // 10. 記錄實際 token 使用量
                if (usage != null) {
                    rateLimiter.recordActualTokenUsage(
                        userId,
                        usage.getTotalTokens(),
                        estimatedTokens
                    );
                }

                return AgentResponse.builder()
                    .messageId(assistantMessage.getId())
                    .content(sanitizedContent)
                    .structuredData(structuredData)
                    .model(aiResponse.getModel())
                    .tokenCount(tokenCount)
                    .latencyMs(latencyMs)
                    .build();

            } catch (Exception e) {
                log.error("Error in agent chat: {}", e.getMessage(), e);
                throw new AiProviderException("AI processing failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 串流方式發送訊息並取得 AI 回應
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @param userMessage 使用者訊息
     * @return AI 回應串流
     */
    public Flux<StreamChunk> chatStream(UUID userId, UUID conversationId, String userMessage) {
        try {
            // 1. 驗證並消毒使用者輸入
            String sanitizedMessage = promptSanitizer.sanitize(userMessage);

            // 2. Rate limit 檢查
            rateLimiter.checkRequestLimit(userId);
            int estimatedTokens = estimateTokens(sanitizedMessage);
            rateLimiter.checkTokenLimit(userId, estimatedTokens);

            // 3. 儲存使用者訊息
            conversationService.addUserMessage(userId, conversationId, sanitizedMessage);

            // 4. 取得對話歷史
            AgentConversation conversation = conversationService.getConversation(userId, conversationId);
            List<AiMessage> messages = buildAiMessages(conversation);

            // 5. 取得使用者的 AI 設定
            AiProviderConfig config = getDefaultConfig(userId);
            AiProvider provider = providerFactory.getProvider(config.getProvider());
            AiProviderSettings settings = buildSettings(config, userId);

            // 6. 發送串流請求給 AI
            long startTime = Instant.now().toEpochMilli();
            StringBuilder contentBuilder = new StringBuilder();

            AiChatRequest request = AiChatRequest.builder()
                .model(config.getDefaultModel())
                .messages(messages)
                .temperature(getTemperature(config))
                .maxTokens(4096)
                .build();

            return provider.chatStream(request, settings)
                .map(chunk -> {
                    if (chunk.getDelta() != null) {
                        contentBuilder.append(chunk.getDelta());
                    }
                    return new StreamChunk(chunk.getDelta(), chunk.isDone());
                })
                .doOnComplete(() -> {
                    // 串流完成後儲存完整回應
                    String content = contentBuilder.toString();
                    String sanitizedContent = promptSanitizer.sanitizeOutput(content);
                    Map<String, Object> structuredData = extractStructuredData(content);
                    long latencyMs = Instant.now().toEpochMilli() - startTime;

                    conversationService.addAssistantMessage(
                        conversationId,
                        sanitizedContent,
                        structuredData,
                        config.getDefaultModel(),
                        estimateTokens(content),
                        latencyMs
                    );
                })
                .doOnError(e -> {
                    log.error("Error in stream chat: {}", e.getMessage());
                });

        } catch (Exception e) {
            log.error("Error initializing stream chat: {}", e.getMessage(), e);
            return Flux.error(new AiProviderException("AI 串流處理失敗: " + e.getMessage(), e));
        }
    }

    /**
     * 取得使用者預設的 AI 設定
     */
    private AiProviderConfig getDefaultConfig(UUID userId) {
        return configRepository.findByOwnerIdAndIsDefaultTrue(userId)
            .or(() -> configRepository.findByOwnerIdAndIsActiveTrue(userId)
                .stream().findFirst())
            .orElseThrow(() -> new AiProviderException("請先設定 AI Provider"));
    }

    /**
     * 建構 AI Provider Settings
     */
    private AiProviderSettings buildSettings(AiProviderConfig config, UUID userId) {
        String apiKey = null;
        if (config.getCredentialId() != null) {
            Map<String, Object> decryptedData = credentialService.getDecryptedData(
                config.getCredentialId(), userId);
            if (decryptedData != null && decryptedData.containsKey("apiKey")) {
                apiKey = (String) decryptedData.get("apiKey");
            }
        }

        return AiProviderSettings.builder()
            .apiKey(apiKey)
            .baseUrl(config.getBaseUrl())
            .timeoutMs(120000)
            .build();
    }

    /**
     * 從設定中取得 temperature
     */
    private double getTemperature(AiProviderConfig config) {
        if (config.getSettings() != null && config.getSettings().containsKey("temperature")) {
            Object temp = config.getSettings().get("temperature");
            if (temp instanceof Number) {
                return ((Number) temp).doubleValue();
            }
        }
        return 0.7; // 預設值
    }

    /**
     * 建構 AI 請求訊息列表
     */
    private List<AiMessage> buildAiMessages(AgentConversation conversation) {
        List<AgentMessage> messages = conversation.getLastMessages(MAX_CONTEXT_MESSAGES);
        List<AiMessage> aiMessages = new ArrayList<>();

        for (AgentMessage msg : messages) {
            AiMessage aiMessage = switch (msg.getRole()) {
                case SYSTEM -> AiMessage.system(msg.getContent());
                case USER -> AiMessage.user(msg.getContent());
                case ASSISTANT -> AiMessage.assistant(msg.getContent());
            };
            aiMessages.add(aiMessage);
        }

        return aiMessages;
    }

    /**
     * 從 AI 回應中提取結構化資料
     */
    private Map<String, Object> extractStructuredData(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 嘗試找出 JSON 區塊
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
            try {
                return objectMapper.readValue(jsonStr, Map.class);
            } catch (JsonProcessingException e) {
                log.debug("Failed to parse JSON block: {}", e.getMessage());
            }
        }

        // 嘗試直接解析整個內容（如果是純 JSON）
        if (content.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(content, Map.class);
            } catch (JsonProcessingException e) {
                log.debug("Content is not valid JSON");
            }
        }

        return null;
    }

    /**
     * 預估 token 數量
     */
    private int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 粗略估計
        return content.length() / 4;
    }

    /**
     * AI 回應
     */
    @lombok.Builder
    public record AgentResponse(
        UUID messageId,
        String content,
        Map<String, Object> structuredData,
        String model,
        int tokenCount,
        long latencyMs
    ) {
        public boolean hasFlowDefinition() {
            return structuredData != null && structuredData.containsKey("flowDefinition");
        }

        public boolean hasComponentRecommendations() {
            return structuredData != null &&
                (structuredData.containsKey("existingComponents") ||
                 structuredData.containsKey("suggestedNewComponents"));
        }
    }

    /**
     * 串流區塊
     */
    public record StreamChunk(String delta, boolean done) {}
}
