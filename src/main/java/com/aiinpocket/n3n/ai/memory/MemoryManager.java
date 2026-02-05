package com.aiinpocket.n3n.ai.memory;

import java.util.List;

/**
 * Memory 管理器介面
 *
 * 提供高階 Memory 操作，根據配置的 Memory 類型自動處理記憶管理。
 * 類似 LangChain 的 BaseChatMemory。
 */
public interface MemoryManager {

    /**
     * 新增使用者訊息
     *
     * @param conversationId 對話 ID
     * @param content 訊息內容
     * @return 建立的訊息
     */
    MemoryMessage addUserMessage(String conversationId, String content);

    /**
     * 新增 AI 回應訊息
     *
     * @param conversationId 對話 ID
     * @param content 訊息內容
     * @return 建立的訊息
     */
    MemoryMessage addAssistantMessage(String conversationId, String content);

    /**
     * 新增系統訊息
     *
     * @param conversationId 對話 ID
     * @param content 訊息內容
     * @return 建立的訊息
     */
    MemoryMessage addSystemMessage(String conversationId, String content);

    /**
     * 取得對話的上下文訊息
     *
     * 根據 Memory 類型返回適當的上下文：
     * - BUFFER: 所有訊息（在 maxTokens 內）
     * - WINDOW: 最近 N 個訊息
     * - SUMMARY: 摘要 + 最近訊息
     * - VECTOR: 相關訊息
     *
     * @param conversationId 對話 ID
     * @return 上下文訊息列表
     */
    List<MemoryMessage> getContextMessages(String conversationId);

    /**
     * 取得對話的上下文訊息（帶查詢語義）
     *
     * 用於 Vector Memory，根據查詢找相關訊息。
     *
     * @param conversationId 對話 ID
     * @param query 查詢內容
     * @return 上下文訊息列表
     */
    List<MemoryMessage> getContextMessages(String conversationId, String query);

    /**
     * 格式化訊息為 prompt 字串
     *
     * @param messages 訊息列表
     * @return 格式化的 prompt
     */
    String formatMessagesAsPrompt(List<MemoryMessage> messages);

    /**
     * 清除對話記憶
     *
     * @param conversationId 對話 ID
     */
    void clearMemory(String conversationId);

    /**
     * 取得對話的摘要
     *
     * @param conversationId 對話 ID
     * @return 摘要內容（如果有）
     */
    String getSummary(String conversationId);

    /**
     * 強制產生對話摘要
     *
     * @param conversationId 對話 ID
     * @return 產生的摘要
     */
    String generateSummary(String conversationId);

    /**
     * 取得 Memory 配置
     *
     * @return 配置
     */
    MemoryConfig getConfig();

    /**
     * 更新 Memory 配置
     *
     * @param config 新配置
     */
    void setConfig(MemoryConfig config);

    /**
     * 取得對話的 token 使用量
     *
     * @param conversationId 對話 ID
     * @return token 數量
     */
    int getTokenUsage(String conversationId);
}
