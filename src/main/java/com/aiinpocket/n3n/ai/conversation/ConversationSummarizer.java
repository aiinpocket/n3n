package com.aiinpocket.n3n.ai.conversation;

import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Summarizes long conversations to maintain context while reducing token usage.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConversationSummarizer {

    private final SimpleAIProviderRegistry aiProviderRegistry;

    private static final String SUMMARY_SYSTEM_PROMPT = """
        你是一個對話摘要專家。請將以下對話內容濃縮成一段簡潔的摘要，保留：
        1. 主要討論的主題和問題
        2. 重要的決定或結論
        3. 任何待辦事項或後續步驟
        4. 提到的關鍵技術細節或流程

        摘要應該在 200 字以內，使用繁體中文。
        """;

    /**
     * Generate a summary for a list of messages.
     *
     * @param messages List of message maps with role and content
     * @param userId   User ID for AI provider selection
     * @return Summary text
     */
    public String summarize(List<Map<String, Object>> messages, UUID userId) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            StringBuilder conversationText = new StringBuilder();
            for (Map<String, Object> msg : messages) {
                String role = (String) msg.get("role");
                String content = (String) msg.get("content");
                if (role != null && content != null) {
                    String roleLabel = "user".equals(role) ? "使用者" : "助手";
                    conversationText.append(roleLabel).append(": ").append(content).append("\n\n");
                }
            }

            String prompt = "請摘要以下對話：\n\n" + conversationText;

            return aiProviderRegistry.chatWithFailover(
                    prompt,
                    SUMMARY_SYSTEM_PROMPT,
                    500,
                    0.3,
                    userId
            );
        } catch (Exception e) {
            log.error("Failed to summarize conversation", e);
            return "";
        }
    }

    /**
     * Check if a conversation needs summarization based on message count.
     */
    public boolean needsSummarization(int messageCount, int threshold) {
        return messageCount > threshold;
    }
}
