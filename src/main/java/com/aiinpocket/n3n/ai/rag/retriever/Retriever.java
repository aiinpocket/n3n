package com.aiinpocket.n3n.ai.rag.retriever;

import com.aiinpocket.n3n.ai.rag.document.Document;

import java.util.List;

/**
 * 檢索器介面
 *
 * 定義從知識庫檢索相關文檔的方法。
 * 類似 LangChain 的 BaseRetriever。
 */
public interface Retriever {

    /**
     * 檢索相關文檔
     *
     * @param query 查詢文字
     * @return 相關文檔列表
     */
    List<Document> getRelevantDocuments(String query);

    /**
     * 檢索相關文檔（指定數量）
     *
     * @param query 查詢文字
     * @param k 返回數量
     * @return 相關文檔列表
     */
    List<Document> getRelevantDocuments(String query, int k);
}
