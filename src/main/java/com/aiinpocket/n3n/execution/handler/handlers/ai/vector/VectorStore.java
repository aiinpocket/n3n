package com.aiinpocket.n3n.execution.handler.handlers.ai.vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 向量儲存介面
 * 支援向量的儲存、搜尋、刪除操作
 */
public interface VectorStore {

    /**
     * 插入向量
     *
     * @param namespace 命名空間（如集合名稱）
     * @param document 向量文檔
     */
    CompletableFuture<Void> upsert(String namespace, VectorDocument document);

    /**
     * 批量插入向量
     */
    CompletableFuture<Void> upsertBatch(String namespace, List<VectorDocument> documents);

    /**
     * 相似度搜尋
     *
     * @param namespace 命名空間
     * @param queryVector 查詢向量
     * @param topK 返回數量
     * @param filter 過濾條件（可選）
     */
    CompletableFuture<List<SearchResult>> search(
        String namespace,
        List<Float> queryVector,
        int topK,
        Map<String, Object> filter
    );

    /**
     * 刪除向量
     *
     * @param namespace 命名空間
     * @param id 文檔 ID
     */
    CompletableFuture<Void> delete(String namespace, String id);

    /**
     * 刪除命名空間中所有向量
     */
    CompletableFuture<Void> deleteAll(String namespace);

    /**
     * 取得向量數量
     */
    CompletableFuture<Long> count(String namespace);

    /**
     * 向量文檔
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
     * 搜尋結果
     */
    record SearchResult(
        String id,
        float score,
        String content,
        Map<String, Object> metadata
    ) {}
}
