package com.aiinpocket.n3n.execution.handler.handlers.ai.vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vector store interface.
 * Supports vector storage, search, and deletion operations.
 */
public interface VectorStore {

    /**
     * Upsert a vector
     *
     * @param namespace namespace (e.g., collection name)
     * @param document vector document
     */
    CompletableFuture<Void> upsert(String namespace, VectorDocument document);

    /**
     * Batch upsert vectors
     */
    CompletableFuture<Void> upsertBatch(String namespace, List<VectorDocument> documents);

    /**
     * Similarity search
     *
     * @param namespace namespace
     * @param queryVector query vector
     * @param topK number of results to return
     * @param filter filter conditions (optional)
     */
    CompletableFuture<List<SearchResult>> search(
        String namespace,
        List<Float> queryVector,
        int topK,
        Map<String, Object> filter
    );

    /**
     * Delete a vector
     *
     * @param namespace namespace
     * @param id document ID
     */
    CompletableFuture<Void> delete(String namespace, String id);

    /**
     * Delete all vectors in a namespace
     */
    CompletableFuture<Void> deleteAll(String namespace);

    /**
     * Get vector count
     */
    CompletableFuture<Long> count(String namespace);

    /**
     * Vector document
     */
    record VectorDocument(
        String id,
        List<Float> vector,
        String content,
        Map<String, Object> metadata
    ) {
        public static VectorDocument of(String id, List<Float> vector, String content) {
            return new VectorDocument(id, vector, content, Map.of());
        }

        public static VectorDocument of(String id, List<Float> vector, String content, Map<String, Object> metadata) {
            return new VectorDocument(id, vector, content, metadata);
        }
    }

    /**
     * Search result
     */
    record SearchResult(
        String id,
        float score,
        String content,
        Map<String, Object> metadata
    ) {}
}
