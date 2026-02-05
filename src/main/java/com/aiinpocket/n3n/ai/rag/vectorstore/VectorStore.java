package com.aiinpocket.n3n.ai.rag.vectorstore;

import com.aiinpocket.n3n.ai.rag.document.Document;

import java.util.List;
import java.util.Map;

/**
 * 向量存儲介面
 *
 * 提供文檔的向量化存儲和相似度搜尋功能。
 * 類似 LangChain 的 VectorStore。
 */
public interface VectorStore {

    /**
     * 新增文檔到向量存儲
     *
     * @param documents 文檔列表
     * @return 新增的文檔 ID 列表
     */
    List<String> addDocuments(List<Document> documents);

    /**
     * 新增單一文檔
     *
     * @param document 文檔
     * @return 文檔 ID
     */
    default String addDocument(Document document) {
        List<String> ids = addDocuments(List.of(document));
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 相似度搜尋
     *
     * @param query 查詢文字
     * @param k 返回結果數量
     * @return 相似文檔列表（按相似度降序）
     */
    List<Document> similaritySearch(String query, int k);

    /**
     * 相似度搜尋（帶分數）
     *
     * @param query 查詢文字
     * @param k 返回結果數量
     * @return 相似文檔列表（Document.score 包含相似度分數）
     */
    List<Document> similaritySearchWithScore(String query, int k);

    /**
     * 相似度搜尋（帶過濾）
     *
     * @param query 查詢文字
     * @param k 返回結果數量
     * @param filter 元資料過濾條件
     * @return 相似文檔列表
     */
    List<Document> similaritySearch(String query, int k, Map<String, Object> filter);

    /**
     * 向量相似度搜尋
     *
     * @param embedding 查詢向量
     * @param k 返回結果數量
     * @return 相似文檔列表
     */
    List<Document> similaritySearchByVector(float[] embedding, int k);

    /**
     * 刪除文檔
     *
     * @param ids 文檔 ID 列表
     */
    void delete(List<String> ids);

    /**
     * 刪除所有文檔
     */
    void deleteAll();

    /**
     * 取得向量存儲名稱
     */
    String getName();
}
