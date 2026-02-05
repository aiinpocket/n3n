package com.aiinpocket.n3n.ai.memory.manager;

import com.aiinpocket.n3n.ai.memory.*;
import com.aiinpocket.n3n.ai.memory.impl.RedisMemoryStore;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 預設 Memory 管理器實作
 *
 * 根據配置的 Memory 類型提供不同的記憶管理策略：
 * - BUFFER: 完整對話記錄
 * - WINDOW: 滑動視窗
 * - SUMMARY: 摘要 + 最近訊息
 * - VECTOR: 語義相關訊息
 * - ENTITY: 實體追蹤（待實作）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultMemoryManager implements MemoryManager {

    private final RedisMemoryStore memoryStore;
    private final AiService aiService;

    @Getter
    @Setter
    private MemoryConfig config = MemoryConfig.defaultConfig();

    @Override
    public MemoryMessage addUserMessage(String conversationId, String content) {
        MemoryMessage message = MemoryMessage.userMessage(conversationId, content);
        memoryStore.addMessage(message);
        checkAndTriggerMemoryManagement(conversationId);
        return message;
    }

    @Override
    public MemoryMessage addAssistantMessage(String conversationId, String content) {
        MemoryMessage message = MemoryMessage.assistantMessage(conversationId, content);
        memoryStore.addMessage(message);
        return message;
    }

    @Override
    public MemoryMessage addSystemMessage(String conversationId, String content) {
        MemoryMessage message = MemoryMessage.systemMessage(conversationId, content);
        memoryStore.addMessage(message);
        return message;
    }

    @Override
    public List<MemoryMessage> getContextMessages(String conversationId) {
        return getContextMessages(conversationId, null);
    }

    @Override
    public List<MemoryMessage> getContextMessages(String conversationId, String query) {
        return switch (config.getType()) {
            case BUFFER -> getBufferContext(conversationId);
            case WINDOW -> getWindowContext(conversationId);
            case SUMMARY -> getSummaryContext(conversationId);
            case VECTOR -> getVectorContext(conversationId, query);
            case ENTITY -> getEntityContext(conversationId);
        };
    }

    @Override
    public String formatMessagesAsPrompt(List<MemoryMessage> messages) {
        StringBuilder sb = new StringBuilder();

        for (MemoryMessage message : messages) {
            String role = switch (message.getRole()) {
                case "user" -> "User";
                case "assistant" -> "Assistant";
                case "system" -> "System";
                default -> message.getRole();
            };

            sb.append(role).append(": ").append(message.getContent()).append("\n\n");
        }

        return sb.toString().trim();
    }

    @Override
    public void clearMemory(String conversationId) {
        memoryStore.clearConversation(conversationId);
        log.info("Cleared memory for conversation {}", conversationId);
    }

    @Override
    public String getSummary(String conversationId) {
        return memoryStore.getSummary(conversationId).orElse(null);
    }

    @Override
    public String generateSummary(String conversationId) {
        List<MemoryMessage> messages = memoryStore.getMessages(conversationId);
        if (messages.isEmpty()) {
            return null;
        }

        // 建構摘要 prompt
        String conversationText = messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String summaryPrompt = """
                請將以下對話內容摘要為簡潔的要點，保留關鍵資訊和上下文：

                %s

                請用繁體中文回答，摘要要簡潔但包含所有重要資訊。
                """.formatted(conversationText);

        try {
            // 使用 AI 服務產生摘要
            String summary = aiService.generateText(summaryPrompt,
                    config.getSummaryModel() != null ? config.getSummaryModel() : "gpt-3.5-turbo");

            memoryStore.setSummary(conversationId, summary);
            log.info("Generated summary for conversation {}", conversationId);
            return summary;

        } catch (Exception e) {
            log.error("Failed to generate summary for conversation {}", conversationId, e);
            return null;
        }
    }

    @Override
    public int getTokenUsage(String conversationId) {
        return memoryStore.getTokenCount(conversationId);
    }

    /**
     * Buffer 策略：返回所有訊息（在 maxTokens 限制內）
     */
    private List<MemoryMessage> getBufferContext(String conversationId) {
        List<MemoryMessage> messages = memoryStore.getMessages(conversationId);

        // 過濾系統訊息（如果配置不包含）
        if (!config.isIncludeSystemMessages()) {
            messages = messages.stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .collect(Collectors.toList());
        }

        // 檢查 token 限制
        int totalTokens = messages.stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();

        if (totalTokens <= config.getMaxTokens()) {
            return messages;
        }

        // 超過限制，從最舊的開始移除
        List<MemoryMessage> result = new ArrayList<>();
        int currentTokens = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            MemoryMessage msg = messages.get(i);
            int msgTokens = msg.getTokenCount() != null ? msg.getTokenCount() : 0;

            if (currentTokens + msgTokens <= config.getMaxTokens()) {
                result.add(0, msg);
                currentTokens += msgTokens;
            } else {
                break;
            }
        }

        return result;
    }

    /**
     * Window 策略：返回最近 N 個訊息
     */
    private List<MemoryMessage> getWindowContext(String conversationId) {
        List<MemoryMessage> messages = memoryStore.getRecentMessages(conversationId, config.getWindowSize());

        if (!config.isIncludeSystemMessages()) {
            messages = messages.stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .collect(Collectors.toList());
        }

        return messages;
    }

    /**
     * Summary 策略：摘要 + 最近訊息
     */
    private List<MemoryMessage> getSummaryContext(String conversationId) {
        List<MemoryMessage> result = new ArrayList<>();

        // 取得摘要
        String summary = memoryStore.getSummary(conversationId).orElse(null);
        if (summary != null && !summary.isEmpty()) {
            result.add(MemoryMessage.systemMessage(conversationId,
                    "以下是之前對話的摘要：\n" + summary));
        }

        // 加入最近幾個訊息
        List<MemoryMessage> recentMessages = memoryStore.getRecentMessages(conversationId,
                config.getWindowSize() / 2);

        if (!config.isIncludeSystemMessages()) {
            recentMessages = recentMessages.stream()
                    .filter(m -> !"system".equals(m.getRole()))
                    .collect(Collectors.toList());
        }

        result.addAll(recentMessages);
        return result;
    }

    /**
     * Vector 策略：語義相關訊息
     */
    private List<MemoryMessage> getVectorContext(String conversationId, String query) {
        if (query == null || query.isEmpty()) {
            // 沒有查詢時退回 Window 策略
            return getWindowContext(conversationId);
        }

        try {
            // 取得查詢的嵌入向量
            float[] queryEmbedding = aiService.getEmbedding(query);

            // 搜尋相關訊息
            List<MemoryMessage> relevantMessages = memoryStore.searchSimilar(
                    conversationId,
                    queryEmbedding,
                    config.getVectorTopK(),
                    config.getVectorSimilarityThreshold()
            );

            // 如果找不到相關訊息，退回 Window 策略
            if (relevantMessages.isEmpty()) {
                return getWindowContext(conversationId);
            }

            return relevantMessages;

        } catch (Exception e) {
            log.error("Vector search failed, falling back to window context", e);
            return getWindowContext(conversationId);
        }
    }

    /**
     * Entity 策略：實體追蹤（待實作）
     */
    private List<MemoryMessage> getEntityContext(String conversationId) {
        // 目前退回 Window 策略
        log.warn("Entity memory not yet implemented, using window context");
        return getWindowContext(conversationId);
    }

    /**
     * 檢查並觸發記憶管理
     */
    private void checkAndTriggerMemoryManagement(String conversationId) {
        if (config.getType() != MemoryType.SUMMARY) {
            return;
        }

        int currentTokens = memoryStore.getTokenCount(conversationId);
        if (currentTokens > config.getSummaryThreshold()) {
            log.info("Token threshold exceeded ({}), generating summary for {}",
                    currentTokens, conversationId);
            generateSummary(conversationId);
        }
    }
}
