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
        You are a conversation summarization expert. Condense the conversation into a concise summary, preserving:
        1. Main topics and questions discussed
        2. Important decisions or conclusions
        3. Any action items or next steps
        4. Key technical details or flow information mentioned

        Keep the summary under 200 words. Respond in the same language the conversation used.
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
                    String roleLabel = "user".equals(role) ? "User" : "Assistant";
                    conversationText.append(roleLabel).append(": ").append(content).append("\n\n");
                }
            }

            String prompt = "Please summarize the following conversation:\n\n" + conversationText;

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
