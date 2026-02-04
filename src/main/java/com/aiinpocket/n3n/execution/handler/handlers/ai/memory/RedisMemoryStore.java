package com.aiinpocket.n3n.execution.handler.handlers.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Redis 記憶儲存實作
 * 使用 Redis List 儲存對話歷史
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisMemoryStore implements MemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "memory:session:";
    private static final String SUMMARY_PREFIX = "memory:summary:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    @Override
    public CompletableFuture<Void> store(String sessionId, MemoryEntry entry) {
        return CompletableFuture.runAsync(() -> {
            try {
                String key = KEY_PREFIX + sessionId;
                String json = objectMapper.writeValueAsString(entry);

                redisTemplate.opsForList().rightPush(key, json);
                redisTemplate.expire(key, DEFAULT_TTL);

                log.debug("Stored memory entry for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to store memory: {}", e.getMessage());
                throw new RuntimeException("Failed to store memory", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryEntry>> getHistory(String sessionId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = KEY_PREFIX + sessionId;
                Long size = redisTemplate.opsForList().size(key);
                if (size == null || size == 0) {
                    return List.of();
                }

                // 取得最後 N 筆
                long start = Math.max(0, size - limit);
                List<String> jsonList = redisTemplate.opsForList().range(key, start, -1);
                if (jsonList == null) {
                    return List.of();
                }

                List<MemoryEntry> entries = new ArrayList<>();
                for (String json : jsonList) {
                    entries.add(objectMapper.readValue(json, MemoryEntry.class));
                }

                return entries;
            } catch (Exception e) {
                log.error("Failed to get history: {}", e.getMessage());
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<Void> clear(String sessionId) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + sessionId;
            String summaryKey = SUMMARY_PREFIX + sessionId;
            redisTemplate.delete(List.of(key, summaryKey));
            log.debug("Cleared memory for session: {}", sessionId);
        });
    }

    @Override
    public CompletableFuture<List<MemoryEntry>> search(String sessionId, String query, int limit) {
        // Redis 基本實作：回傳最近的記憶（不支援語意搜尋）
        // 若需語意搜尋，請使用 VectorMemoryStore
        return getHistory(sessionId, limit);
    }

    @Override
    public CompletableFuture<Optional<String>> getSummary(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            String key = SUMMARY_PREFIX + sessionId;
            String summary = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(summary);
        });
    }

    @Override
    public CompletableFuture<Void> saveSummary(String sessionId, String summary) {
        return CompletableFuture.runAsync(() -> {
            String key = SUMMARY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, summary, DEFAULT_TTL);
            log.debug("Saved summary for session: {}", sessionId);
        });
    }
}
