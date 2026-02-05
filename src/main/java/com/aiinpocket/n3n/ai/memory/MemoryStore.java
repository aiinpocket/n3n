package com.aiinpocket.n3n.ai.memory;

import java.util.List;
import java.util.Optional;

/**
 * Memory 存儲介面
 *
 * 定義 Memory 的持久化操作，支援多種後端實作。
 */
public interface MemoryStore {

    /**
     * 新增訊息
     *
     * @param message 訊息
     */
    void addMessage(MemoryMessage message);

    /**
     * 取得對話的所有訊息
     *
     * @param conversationId 對話 ID
     * @return 訊息列表（按時間排序）
     */
    List<MemoryMessage> getMessages(String conversationId);

    /**
     * 取得對話的最近 N 個訊息
     *
     * @param conversationId 對話 ID
     * @param limit 數量限制
     * @return 訊息列表（按時間排序）
     */
    List<MemoryMessage> getRecentMessages(String conversationId, int limit);

    /**
     * 取得對話的訊息（分頁）
     *
     * @param conversationId 對話 ID
     * @param offset 偏移量
     * @param limit 數量限制
     * @return 訊息列表（按時間排序）
     */
    List<MemoryMessage> getMessages(String conversationId, int offset, int limit);

    /**
     * 取得單一訊息
     *
     * @param messageId 訊息 ID
     * @return 訊息
     */
    Optional<MemoryMessage> getMessage(String messageId);

    /**
     * 刪除訊息
     *
     * @param messageId 訊息 ID
     */
    void deleteMessage(String messageId);

    /**
     * 清除對話的所有訊息
     *
     * @param conversationId 對話 ID
     */
    void clearConversation(String conversationId);

    /**
     * 計算對話的總 token 數
     *
     * @param conversationId 對話 ID
     * @return token 數量
     */
    int getTokenCount(String conversationId);

    /**
     * 計算對話的訊息數量
     *
     * @param conversationId 對話 ID
     * @return 訊息數量
     */
    int getMessageCount(String conversationId);

    /**
     * 更新訊息的向量嵌入
     *
     * @param messageId 訊息 ID
     * @param embedding 向量嵌入
     */
    void updateEmbedding(String messageId, float[] embedding);

    /**
     * 向量相似度搜尋
     *
     * @param conversationId 對話 ID
     * @param queryEmbedding 查詢向量
     * @param topK 返回數量
     * @param threshold 相似度閾值
     * @return 相關訊息列表
     */
    List<MemoryMessage> searchSimilar(String conversationId, float[] queryEmbedding,
                                       int topK, float threshold);

    /**
     * 設定對話的摘要
     *
     * @param conversationId 對話 ID
     * @param summary 摘要內容
     */
    void setSummary(String conversationId, String summary);

    /**
     * 取得對話的摘要
     *
     * @param conversationId 對話 ID
     * @return 摘要內容
     */
    Optional<String> getSummary(String conversationId);

    /**
     * 設定對話的 TTL
     *
     * @param conversationId 對話 ID
     * @param ttlSeconds 過期時間（秒）
     */
    void setTtl(String conversationId, long ttlSeconds);
}
