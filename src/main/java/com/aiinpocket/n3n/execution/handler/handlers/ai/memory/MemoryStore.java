package com.aiinpocket.n3n.execution.handler.handlers.ai.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Conversation memory store interface.
 * Supports multiple storage backends (Redis, PostgreSQL, etc.)
 */
public interface MemoryStore {

    /**
     * Store a memory entry
     *
     * @param sessionId session ID
     * @param entry memory entry
     */
    CompletableFuture<Void> store(String sessionId, MemoryEntry entry);

    /**
     * Get all memories for a session
     *
     * @param sessionId session ID
     * @param limit maximum number of entries
     */
    CompletableFuture<List<MemoryEntry>> getHistory(String sessionId, int limit);

    /**
     * Clear session memory
     *
     * @param sessionId session ID
     */
    CompletableFuture<Void> clear(String sessionId);

    /**
     * Search for related memories (semantic search, if supported)
     *
     * @param sessionId session ID
     * @param query search query
     * @param limit maximum number of entries
     */
    CompletableFuture<List<MemoryEntry>> search(String sessionId, String query, int limit);

    /**
     * Get session summary
     *
     * @param sessionId session ID
     */
    CompletableFuture<Optional<String>> getSummary(String sessionId);

    /**
     * Save session summary
     *
     * @param sessionId session ID
     * @param summary summary content
     */
    CompletableFuture<Void> saveSummary(String sessionId, String summary);

    /**
     * Memory entry
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
