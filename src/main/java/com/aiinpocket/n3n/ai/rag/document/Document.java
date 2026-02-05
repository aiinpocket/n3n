package com.aiinpocket.n3n.ai.rag.document;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 文檔模型
 *
 * 代表可被索引和檢索的文檔或文檔片段。
 * 類似 LangChain 的 Document 類別。
 */
@Data
@Builder
public class Document {

    /**
     * 文檔 ID
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * 文檔內容
     */
    private String content;

    /**
     * 文檔元資料
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 向量嵌入
     */
    private float[] embedding;

    /**
     * 相似度分數（用於搜尋結果）
     */
    private Float score;

    /**
     * 建立簡單文檔
     */
    public static Document of(String content) {
        return Document.builder()
                .content(content)
                .build();
    }

    /**
     * 建立帶元資料的文檔
     */
    public static Document of(String content, Map<String, Object> metadata) {
        return Document.builder()
                .content(content)
                .metadata(new HashMap<>(metadata))
                .build();
    }

    /**
     * 新增元資料
     */
    public Document addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 取得元資料值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        if (value == null) return null;
        return (T) value;
    }

    /**
     * 取得來源資訊
     */
    public String getSource() {
        return getMetadataValue("source", String.class);
    }

    /**
     * 設定來源資訊
     */
    public Document setSource(String source) {
        return addMetadata("source", source);
    }

    /**
     * 估算 token 數量
     */
    public int estimateTokens() {
        if (content == null) return 0;
        return Math.max(1, content.length() / 4);
    }
}
