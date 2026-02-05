package com.aiinpocket.n3n.ai.memory;

/**
 * Memory 類型枚舉
 *
 * 類似 LangChain 的 Memory 類型，用於不同場景的對話記憶管理。
 */
public enum MemoryType {

    /**
     * Buffer Memory - 完整對話記錄
     * 保存所有對話訊息，適合短對話。
     */
    BUFFER,

    /**
     * Window Memory - 滑動視窗
     * 只保留最近 N 個訊息，控制 token 使用量。
     */
    WINDOW,

    /**
     * Summary Memory - 摘要記憶
     * 使用 LLM 摘要舊對話，節省 token 同時保留上下文。
     */
    SUMMARY,

    /**
     * Vector Memory - 向量語義記憶
     * 將對話存入向量資料庫，語義搜尋相關對話。
     */
    VECTOR,

    /**
     * Entity Memory - 實體記憶
     * 追蹤對話中提到的實體（人、事、物）及其關係。
     */
    ENTITY
}
