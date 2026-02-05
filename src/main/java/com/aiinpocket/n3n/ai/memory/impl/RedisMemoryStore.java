package com.aiinpocket.n3n.ai.memory.impl;

import com.aiinpocket.n3n.ai.memory.MemoryMessage;
import com.aiinpocket.n3n.ai.memory.MemoryStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Memory 存儲實作
 *
 * 使用 Redis 作為對話記憶的持久化後端，適合生產環境。
 * 支援 TTL、分頁查詢和向量搜尋（需要 Redis 7+ 的向量搜尋模組）。
 *
 * Key 結構：
 * - memory:conv:{conversationId}:messages - Sorted Set (score = timestamp)
 * - memory:conv:{conversationId}:summary - String
 * - memory:msg:{messageId} - Hash
 */
@Component("aiRedisMemoryStore")
@RequiredArgsConstructor
@Slf4j
public class RedisMemoryStore implements MemoryStore {

    private static final String CONV_MESSAGES_KEY = "memory:conv:%s:messages";
    private static final String CONV_SUMMARY_KEY = "memory:conv:%s:summary";
    private static final String MESSAGE_KEY = "memory:msg:%s";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void addMessage(MemoryMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);

            // 儲存訊息內容
            String messageKey = String.format(MESSAGE_KEY, message.getId());
            redisTemplate.opsForValue().set(messageKey, messageJson);

            // 加入對話的訊息列表（使用時間戳作為 score）
            String convMessagesKey = String.format(CONV_MESSAGES_KEY, message.getConversationId());
            double score = message.getCreatedAt().toEpochMilli();
            redisTemplate.opsForZSet().add(convMessagesKey, message.getId(), score);

            log.debug("Added message {} to Redis conversation {}", message.getId(), message.getConversationId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message", e);
            throw new RuntimeException("Failed to store message", e);
        }
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId) {
        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);
        Set<String> messageIds = redisTemplate.opsForZSet().range(convMessagesKey, 0, -1);

        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        return messageIds.stream()
                .map(this::getMessageById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String conversationId, int limit) {
        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);
        Set<String> messageIds = redisTemplate.opsForZSet().reverseRange(convMessagesKey, 0, limit - 1);

        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 反轉順序使最舊的在前面
        List<String> idList = new ArrayList<>(messageIds);
        Collections.reverse(idList);

        return idList.stream()
                .map(this::getMessageById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId, int offset, int limit) {
        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);
        Set<String> messageIds = redisTemplate.opsForZSet().range(convMessagesKey, offset, offset + limit - 1);

        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        return messageIds.stream()
                .map(this::getMessageById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MemoryMessage> getMessage(String messageId) {
        return getMessageById(messageId);
    }

    @Override
    public void deleteMessage(String messageId) {
        Optional<MemoryMessage> messageOpt = getMessageById(messageId);
        if (messageOpt.isPresent()) {
            MemoryMessage message = messageOpt.get();

            // 從對話列表移除
            String convMessagesKey = String.format(CONV_MESSAGES_KEY, message.getConversationId());
            redisTemplate.opsForZSet().remove(convMessagesKey, messageId);

            // 刪除訊息內容
            String messageKey = String.format(MESSAGE_KEY, messageId);
            redisTemplate.delete(messageKey);

            log.debug("Deleted message {} from Redis", messageId);
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);

        // 取得所有訊息 ID
        Set<String> messageIds = redisTemplate.opsForZSet().range(convMessagesKey, 0, -1);
        if (messageIds != null && !messageIds.isEmpty()) {
            // 刪除所有訊息
            List<String> messageKeys = messageIds.stream()
                    .map(id -> String.format(MESSAGE_KEY, id))
                    .toList();
            redisTemplate.delete(messageKeys);
        }

        // 刪除對話列表和摘要
        String convSummaryKey = String.format(CONV_SUMMARY_KEY, conversationId);
        redisTemplate.delete(Arrays.asList(convMessagesKey, convSummaryKey));

        log.debug("Cleared conversation {} from Redis", conversationId);
    }

    @Override
    public int getTokenCount(String conversationId) {
        return getMessages(conversationId).stream()
                .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
                .sum();
    }

    @Override
    public int getMessageCount(String conversationId) {
        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);
        Long size = redisTemplate.opsForZSet().size(convMessagesKey);
        return size != null ? size.intValue() : 0;
    }

    @Override
    public void updateEmbedding(String messageId, float[] embedding) {
        Optional<MemoryMessage> messageOpt = getMessageById(messageId);
        if (messageOpt.isPresent()) {
            MemoryMessage message = messageOpt.get();
            message.setEmbedding(embedding);

            try {
                String messageJson = objectMapper.writeValueAsString(message);
                String messageKey = String.format(MESSAGE_KEY, messageId);
                redisTemplate.opsForValue().set(messageKey, messageJson);
            } catch (JsonProcessingException e) {
                log.error("Failed to update message embedding", e);
            }
        }
    }

    @Override
    public List<MemoryMessage> searchSimilar(String conversationId, float[] queryEmbedding,
                                              int topK, float threshold) {
        // 基本實作：取得所有訊息並在記憶體中計算相似度
        // 生產環境應使用 Redis 的向量搜尋模組 (RediSearch)
        List<MemoryMessage> allMessages = getMessages(conversationId);

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
        String summaryKey = String.format(CONV_SUMMARY_KEY, conversationId);
        redisTemplate.opsForValue().set(summaryKey, summary);
        log.debug("Set summary for conversation {} in Redis", conversationId);
    }

    @Override
    public Optional<String> getSummary(String conversationId) {
        String summaryKey = String.format(CONV_SUMMARY_KEY, conversationId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(summaryKey));
    }

    @Override
    public void setTtl(String conversationId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }

        String convMessagesKey = String.format(CONV_MESSAGES_KEY, conversationId);
        String convSummaryKey = String.format(CONV_SUMMARY_KEY, conversationId);

        redisTemplate.expire(convMessagesKey, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(convSummaryKey, ttlSeconds, TimeUnit.SECONDS);

        // 也設定個別訊息的 TTL
        Set<String> messageIds = redisTemplate.opsForZSet().range(convMessagesKey, 0, -1);
        if (messageIds != null) {
            for (String messageId : messageIds) {
                String messageKey = String.format(MESSAGE_KEY, messageId);
                redisTemplate.expire(messageKey, ttlSeconds, TimeUnit.SECONDS);
            }
        }

        log.debug("Set TTL {} seconds for conversation {}", ttlSeconds, conversationId);
    }

    private Optional<MemoryMessage> getMessageById(String messageId) {
        String messageKey = String.format(MESSAGE_KEY, messageId);
        String messageJson = redisTemplate.opsForValue().get(messageKey);

        if (messageJson == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(messageJson, MemoryMessage.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message {}", messageId, e);
            return Optional.empty();
        }
    }

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
}
