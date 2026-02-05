package com.aiinpocket.n3n.ai.memory.impl;

import com.aiinpocket.n3n.ai.embedding.EmbeddingService;
import com.aiinpocket.n3n.ai.memory.MemoryMessage;
import com.aiinpocket.n3n.ai.memory.MemoryStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * PostgreSQL 向量存儲實作
 *
 * 使用 pgvector 擴展進行高效的向量相似度搜尋。
 * 支援餘弦相似度 (cosine) 和歐幾里得距離 (L2)。
 *
 * 配置方式：
 * - n3n.memory.store=postgres
 */
@Service
@ConditionalOnProperty(name = "n3n.memory.store", havingValue = "postgres")
@RequiredArgsConstructor
@Slf4j
public class PostgresVectorStore implements MemoryStore {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Override
    public void addMessage(MemoryMessage message) {
        // 如果沒有嵌入，則自動生成
        float[] embedding = message.getEmbedding();
        if (embedding == null && message.getContent() != null && !message.getContent().isBlank()) {
            try {
                embedding = embeddingService.getEmbedding(message.getContent());
            } catch (Exception e) {
                log.warn("Failed to generate embedding for message: {}", e.getMessage());
            }
        }

        String sql = """
            INSERT INTO conversation_messages (id, conversation_id, role, content, token_count, embedding, metadata, created_at)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::vector, ?::jsonb, ?)
            """;

        jdbcTemplate.update(sql,
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getTokenCount(),
                embedding != null ? arrayToVectorString(embedding) : null,
                toJsonString(message.getMetadata()),
                Timestamp.from(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now())
        );
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId) {
        String sql = """
            SELECT id, conversation_id, role, content, token_count, metadata, created_at
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
            ORDER BY created_at ASC
            """;

        return jdbcTemplate.query(sql, new MemoryMessageRowMapper(), conversationId);
    }

    @Override
    public List<MemoryMessage> getRecentMessages(String conversationId, int limit) {
        String sql = """
            SELECT id, conversation_id, role, content, token_count, metadata, created_at
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ?
            """;

        List<MemoryMessage> messages = jdbcTemplate.query(sql, new MemoryMessageRowMapper(), conversationId, limit);
        // 反轉為時間正序
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public List<MemoryMessage> getMessages(String conversationId, int offset, int limit) {
        String sql = """
            SELECT id, conversation_id, role, content, token_count, metadata, created_at
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
            ORDER BY created_at ASC
            OFFSET ?
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, new MemoryMessageRowMapper(), conversationId, offset, limit);
    }

    @Override
    public Optional<MemoryMessage> getMessage(String messageId) {
        String sql = """
            SELECT id, conversation_id, role, content, token_count, metadata, created_at
            FROM conversation_messages
            WHERE id = ?::uuid
            """;

        List<MemoryMessage> messages = jdbcTemplate.query(sql, new MemoryMessageRowMapper(), messageId);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }

    @Override
    public void deleteMessage(String messageId) {
        String sql = "DELETE FROM conversation_messages WHERE id = ?::uuid";
        jdbcTemplate.update(sql, messageId);
    }

    @Override
    public void clearConversation(String conversationId) {
        String sql = "DELETE FROM conversation_messages WHERE conversation_id = ?::uuid";
        jdbcTemplate.update(sql, conversationId);

        // 也清除摘要
        String deleteSummary = "DELETE FROM conversation_summaries WHERE conversation_id = ?::uuid";
        jdbcTemplate.update(deleteSummary, conversationId);
    }

    @Override
    public int getTokenCount(String conversationId) {
        String sql = """
            SELECT COALESCE(SUM(token_count), 0)
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return count != null ? count : 0;
    }

    @Override
    public int getMessageCount(String conversationId) {
        String sql = """
            SELECT COUNT(*)
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return count != null ? count : 0;
    }

    @Override
    public void updateEmbedding(String messageId, float[] embedding) {
        String sql = """
            UPDATE conversation_messages
            SET embedding = ?::vector
            WHERE id = ?::uuid
            """;

        jdbcTemplate.update(sql, arrayToVectorString(embedding), messageId);
    }

    @Override
    public List<MemoryMessage> searchSimilar(String conversationId, float[] queryEmbedding,
                                              int topK, float threshold) {
        // 使用 pgvector 的餘弦距離運算符 <=>
        // 距離 = 1 - 相似度，所以相似度 = 1 - 距離
        // 過濾條件：1 - distance >= threshold，即 distance <= 1 - threshold
        String sql = """
            SELECT id, conversation_id, role, content, token_count, metadata, created_at,
                   1 - (embedding <=> ?::vector) as similarity
            FROM conversation_messages
            WHERE conversation_id = ?::uuid
              AND embedding IS NOT NULL
              AND 1 - (embedding <=> ?::vector) >= ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

        String vectorStr = arrayToVectorString(queryEmbedding);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            MemoryMessage message = mapRowToMessage(rs);
            // 可以在 metadata 中存儲相似度
            if (message.getMetadata() == null) {
                message.setMetadata(new HashMap<>());
            }
            message.getMetadata().put("similarity", rs.getFloat("similarity"));
            return message;
        }, vectorStr, conversationId, vectorStr, threshold, vectorStr, topK);
    }

    @Override
    public void setSummary(String conversationId, String summary) {
        String sql = """
            INSERT INTO conversation_summaries (conversation_id, summary, messages_summarized, created_at)
            VALUES (?::uuid, ?, 0, NOW())
            ON CONFLICT (conversation_id)
            DO UPDATE SET summary = ?, created_at = NOW()
            """;

        jdbcTemplate.update(sql, conversationId, summary, summary);
    }

    @Override
    public Optional<String> getSummary(String conversationId) {
        String sql = """
            SELECT summary
            FROM conversation_summaries
            WHERE conversation_id = ?::uuid
            """;

        List<String> summaries = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("summary"),
                conversationId);

        return summaries.isEmpty() ? Optional.empty() : Optional.of(summaries.get(0));
    }

    @Override
    public void setTtl(String conversationId, long ttlSeconds) {
        // PostgreSQL 不支援原生 TTL，但可以更新 memory_config 中的 ttl_seconds
        // 實際清理需要透過排程任務執行
        String sql = """
            UPDATE memory_config
            SET ttl_seconds = ?, updated_at = NOW()
            WHERE conversation_id = ?::uuid
            """;

        int updated = jdbcTemplate.update(sql, ttlSeconds, conversationId);
        if (updated == 0) {
            log.debug("No memory_config found for conversation {}, TTL not set", conversationId);
        }
    }

    /**
     * 將 float 陣列轉換為 pgvector 格式的字串
     * 格式: [0.1, 0.2, 0.3, ...]
     */
    private String arrayToVectorString(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonString(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata to JSON", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metadata JSON", e);
            return null;
        }
    }

    private MemoryMessage mapRowToMessage(ResultSet rs) throws SQLException {
        return MemoryMessage.builder()
                .id(rs.getString("id"))
                .conversationId(rs.getString("conversation_id"))
                .role(rs.getString("role"))
                .content(rs.getString("content"))
                .tokenCount(rs.getObject("token_count", Integer.class))
                .metadata(parseJsonMetadata(rs.getString("metadata")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build();
    }

    private class MemoryMessageRowMapper implements RowMapper<MemoryMessage> {
        @Override
        public MemoryMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return mapRowToMessage(rs);
        }
    }
}
