package com.aiinpocket.n3n.ai.memory.impl;

import com.aiinpocket.n3n.ai.memory.MemoryMessage;
import com.aiinpocket.n3n.ai.memory.MemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-Memory 存儲實作
 *
 * 適用於開發和測試環境。資料不會持久化。
 * 生產環境應使用 RedisMemoryStore 或 PostgresMemoryStore。
 */
@Component
@Slf4j
public class InMemoryStore implements MemoryStore {

    // conversationId -> List<Message>
    private final Map<String, List<MemoryMessage>> messages = new ConcurrentHashMap<>();

    // conversationId -> summary
    private final Map<String, String> summaries = new ConcurrentHashMap<>();

    // messageId -> message (for quick lookup)
    private final Map<String, MemoryMessage> messageIndex = new ConcurrentHashMap<>();

    @Override
    public void addMessage(MemoryMessage message) {
        messages.computeIfAbsent(message.getConversationId(), k ->
                Collections.synchronizedList(new ArrayList<>()))
                .add(message);
        messageIndex.put(message.getId(), message);

        log.debug("Added message {} to conversation {}",
                message.getId(), message.getConversationId());
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId) {
        return new ArrayList<>(messages.getOrDefault(conversationId, Collections.emptyList()));
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String conversationId, int limit) {
        List<MemoryMessage> allMessages = messages.getOrDefault(conversationId, Collections.emptyList());
        int size = allMessages.size();
        if (size <= limit) {
            return new ArrayList<>(allMessages);
        }
        return new ArrayList<>(allMessages.subList(size - limit, size));
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId, int offset, int limit) {
        List<MemoryMessage> allMessages = messages.getOrDefault(conversationId, Collections.emptyList());
        int size = allMessages.size();
        if (offset >= size) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, size);
        return new ArrayList<>(allMessages.subList(offset, end));
    }

    @Override
    public Optional<MemoryMessage> getMessage(String messageId) {
        return Optional.ofNullable(messageIndex.get(messageId));
    }

    @Override
    public void deleteMessage(String messageId) {
        MemoryMessage message = messageIndex.remove(messageId);
        if (message != null) {
            List<MemoryMessage> conversationMessages = messages.get(message.getConversationId());
            if (conversationMessages != null) {
                conversationMessages.removeIf(m -> m.getId().equals(messageId));
            }
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        List<MemoryMessage> removed = messages.remove(conversationId);
        if (removed != null) {
            removed.forEach(m -> messageIndex.remove(m.getId()));
        }
        summaries.remove(conversationId);
        log.debug("Cleared conversation {}", conversationId);
    }

    @Override
    public int getTokenCount(String conversationId) {
        return messages.getOrDefault(conversationId, Collections.emptyList())
                .stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();
    }

    @Override
    public int getMessageCount(String conversationId) {
        return messages.getOrDefault(conversationId, Collections.emptyList()).size();
    }

    @Override
    public void updateEmbedding(String messageId, float[] embedding) {
        MemoryMessage message = messageIndex.get(messageId);
        if (message != null) {
            message.setEmbedding(embedding);
        }
    }

    @Override
    public List<MemoryMessage> searchSimilar(String conversationId, float[] queryEmbedding,
                                              int topK, float threshold) {
        List<MemoryMessage> allMessages = messages.getOrDefault(conversationId, Collections.emptyList());

        // 計算餘弦相似度並排序
        return allMessages.stream()
                .filter(m -> m.getEmbedding() != null)
                .map(m -> Map.entry(m, cosineSimilarity(queryEmbedding, m.getEmbedding())))
                .filter(e -> e.getValue() >= threshold)
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public void setSummary(String conversationId, String summary) {
        summaries.put(conversationId, summary);
        log.debug("Set summary for conversation {}", conversationId);
    }

    @Override
    public Optional<String> getSummary(String conversationId) {
        return Optional.ofNullable(summaries.get(conversationId));
    }

    @Override
    public void setTtl(String conversationId, long ttlSeconds) {
        // In-memory 實作不支援 TTL，忽略此操作
        log.debug("TTL not supported in InMemoryStore, ignoring setTtl for {}", conversationId);
    }

    /**
     * 計算餘弦相似度
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /**
     * 清除所有資料（用於測試）
     */
    public void clearAll() {
        messages.clear();
        summaries.clear();
        messageIndex.clear();
        log.info("Cleared all in-memory data");
    }
}
