package com.aiinpocket.n3n.execution.handler.handlers.ai.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 對話記憶儲存介面
 * 支援多種後端儲存（Redis, PostgreSQL, 等）
 */
public interface MemoryStore {

    /**
     * 儲存記憶項目
     *
     * @param sessionId 會話 ID
     * @param entry 記憶項目
     */
    CompletableFuture<Void> store(String sessionId, MemoryEntry entry);

    /**
     * 取得會話的所有記憶
     *
     * @param sessionId 會話 ID
     * @param limit 最大數量
     */
    CompletableFuture<List<MemoryEntry>> getHistory(String sessionId, int limit);

    /**
     * 清除會話記憶
     *
     * @param sessionId 會話 ID
     */
    CompletableFuture<Void> clear(String sessionId);

    /**
     * 搜尋相關記憶（語意搜尋，如支援）
     *
     * @param sessionId 會話 ID
     * @param query 搜尋查詢
     * @param limit 最大數量
     */
    CompletableFuture<List<MemoryEntry>> search(String sessionId, String query, int limit);

    /**
     * 取得會話摘要
     *
     * @param sessionId 會話 ID
     */
    CompletableFuture<Optional<String>> getSummary(String sessionId);

    /**
     * 儲存會話摘要
     *
     * @param sessionId 會話 ID
     * @param summary 摘要內容
     */
    CompletableFuture<Void> saveSummary(String sessionId, String summary);

    /**
     * 記憶項目
     */
    record MemoryEntry(
        String id,
        String role,       // "user", "assistant", "system"
        String content,
        Map<String, Object> metadata,
        long timestamp
    ) {
        public static MemoryEntry user(String content) {
            return new MemoryEntry(
                java.util.UUID.randomUUID().toString(),
                "user",
                content,
                Map.of(),
                System.currentTimeMillis()
            );
        }

        public static MemoryEntry assistant(String content) {
            return new MemoryEntry(
                java.util.UUID.randomUUID().toString(),
                "assistant",
                content,
                Map.of(),
                System.currentTimeMillis()
            );
        }

        public static MemoryEntry system(String content) {
            return new MemoryEntry(
                java.util.UUID.randomUUID().toString(),
                "system",
                content,
                Map.of(),
                System.currentTimeMillis()
            );
        }
    }
}
