package com.aiinpocket.n3n.ai.conversation;

import com.aiinpocket.n3n.ai.entity.Conversation;
import com.aiinpocket.n3n.ai.repository.ConversationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages AI conversation history with automatic summarization.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationManager {

    private final ConversationRepository conversationRepository;
    private final ConversationSummarizer conversationSummarizer;
    private final ObjectMapper objectMapper;

    /**
     * Maximum messages to keep in full context before summarization.
     */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    /**
     * Number of recent messages to keep after summarization.
     */
    private static final int RECENT_MESSAGES_TO_KEEP = 10;

    /**
     * Create a new conversation.
     */
    @Transactional
    public Conversation createConversation(UUID userId, UUID flowId, String title) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .flowId(flowId)
                .title(title != null ? title : "新對話")
                .messages(new ArrayList<>())
                .messageCount(0)
                .build();

        return conversationRepository.save(conversation);
    }

    /**
     * Add a message to conversation.
     */
    @Transactional
    public Conversation addMessage(UUID conversationId, String role, String content, Map<String, Object> metadata) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        List<Map<String, Object>> messages = conversation.getMessages();
        if (messages == null) {
            messages = new ArrayList<>();
        }

        Map<String, Object> message = new HashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", LocalDateTime.now().toString());
        if (metadata != null) {
            message.put("metadata", metadata);
        }

        messages.add(message);
        conversation.setMessages(messages);
        conversation.setMessageCount(messages.size());
        conversation.setUpdatedAt(LocalDateTime.now());

        // Check if summarization is needed
        if (conversationSummarizer.needsSummarization(messages.size(), MAX_CONTEXT_MESSAGES)) {
            summarizeConversation(conversation);
        }

        return conversationRepository.save(conversation);
    }

    /**
     * Summarize older messages and keep recent ones.
     */
    private void summarizeConversation(Conversation conversation) {
        List<Map<String, Object>> messages = conversation.getMessages();
        if (messages.size() <= RECENT_MESSAGES_TO_KEEP) {
            return;
        }

        try {
            // Get messages to summarize (all except recent ones)
            int splitIndex = messages.size() - RECENT_MESSAGES_TO_KEEP;
            List<Map<String, Object>> toSummarize = messages.subList(0, splitIndex);
            List<Map<String, Object>> toKeep = new ArrayList<>(messages.subList(splitIndex, messages.size()));

            // Generate summary
            String existingSummary = conversation.getSummary();
            List<Map<String, Object>> contentToSummarize = new ArrayList<>();

            // Include existing summary as context if present
            if (existingSummary != null && !existingSummary.isBlank()) {
                Map<String, Object> summaryContext = new HashMap<>();
                summaryContext.put("role", "system");
                summaryContext.put("content", "先前對話摘要：" + existingSummary);
                contentToSummarize.add(summaryContext);
            }

            contentToSummarize.addAll(toSummarize);

            String newSummary = conversationSummarizer.summarize(contentToSummarize, conversation.getUserId());

            if (newSummary != null && !newSummary.isBlank()) {
                conversation.setSummary(newSummary);
                conversation.setMessages(toKeep);
                conversation.setMessageCount(toKeep.size());
                log.info("Summarized conversation {}: {} messages -> {} messages + summary",
                        conversation.getId(), messages.size(), toKeep.size());
            }
        } catch (Exception e) {
            log.error("Failed to summarize conversation {}", conversation.getId(), e);
        }
    }

    /**
     * Get conversation by ID.
     */
    public Optional<Conversation> getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId);
    }

    /**
     * Get user's conversations.
     */
    public List<Conversation> getUserConversations(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * Get conversations for a specific flow.
     */
    public List<Conversation> getFlowConversations(UUID userId, UUID flowId) {
        return conversationRepository.findByUserIdAndFlowIdOrderByUpdatedAtDesc(userId, flowId);
    }

    /**
     * Delete a conversation.
     */
    @Transactional
    public void deleteConversation(UUID conversationId) {
        conversationRepository.deleteById(conversationId);
    }

    /**
     * Export conversation to JSON.
     */
    public String exportConversation(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        try {
            Map<String, Object> export = new HashMap<>();
            export.put("id", conversation.getId().toString());
            export.put("title", conversation.getTitle());
            export.put("flowId", conversation.getFlowId() != null ? conversation.getFlowId().toString() : null);
            export.put("messages", conversation.getMessages());
            export.put("summary", conversation.getSummary());
            export.put("messageCount", conversation.getMessageCount());
            export.put("createdAt", conversation.getCreatedAt().toString());
            export.put("updatedAt", conversation.getUpdatedAt().toString());
            export.put("exportedAt", LocalDateTime.now().toString());
            export.put("version", "1.0");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export conversation", e);
        }
    }

    /**
     * Import conversation from JSON.
     */
    @Transactional
    public Conversation importConversation(UUID userId, String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});

            String title = (String) data.get("title");
            String flowIdStr = (String) data.get("flowId");
            UUID flowId = flowIdStr != null ? UUID.fromString(flowIdStr) : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
            String summary = (String) data.get("summary");

            Conversation conversation = Conversation.builder()
                    .userId(userId)
                    .flowId(flowId)
                    .title(title != null ? title + " (匯入)" : "匯入的對話")
                    .messages(messages != null ? messages : new ArrayList<>())
                    .summary(summary)
                    .messageCount(messages != null ? messages.size() : 0)
                    .build();

            return conversationRepository.save(conversation);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import conversation: " + e.getMessage(), e);
        }
    }

    /**
     * Get context for AI (summary + recent messages).
     */
    public List<Map<String, Object>> getContextForAI(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);

        if (conversation == null) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> context = new ArrayList<>();

        // Add summary as system message if present
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            Map<String, Object> summaryMessage = new HashMap<>();
            summaryMessage.put("role", "system");
            summaryMessage.put("content", "先前對話摘要：\n" + conversation.getSummary());
            context.add(summaryMessage);
        }

        // Add recent messages
        if (conversation.getMessages() != null) {
            context.addAll(conversation.getMessages());
        }

        return context;
    }
}
