package com.aiinpocket.n3n.ai.rag.retriever;

import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.vectorstore.VectorStore;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 向量存儲檢索器
 *
 * 使用向量存儲進行語義相似度檢索。
 */
@Slf4j
public class VectorStoreRetriever implements Retriever {

    private final VectorStore vectorStore;
    private final int defaultK;
    private final float scoreThreshold;
    private final Map<String, Object> filter;

    @Builder
    public VectorStoreRetriever(VectorStore vectorStore, int defaultK,
                                 float scoreThreshold, Map<String, Object> filter) {
        this.vectorStore = vectorStore;
        this.defaultK = defaultK > 0 ? defaultK : 4;
        this.scoreThreshold = scoreThreshold > 0 ? scoreThreshold : 0.0f;
        this.filter = filter;
    }

    public VectorStoreRetriever(VectorStore vectorStore) {
        this(vectorStore, 4, 0.0f, null);
    }

    @Override
    public List<Document> getRelevantDocuments(String query) {
        return getRelevantDocuments(query, defaultK);
    }

    @Override
    public List<Document> getRelevantDocuments(String query, int k) {
        List<Document> results;

        if (filter != null && !filter.isEmpty()) {
            results = vectorStore.similaritySearch(query, k, filter);
        } else {
            results = vectorStore.similaritySearchWithScore(query, k);
        }

        // 過濾低於閾值的結果
        if (scoreThreshold > 0) {
            results = results.stream()
                    .filter(doc -> doc.getScore() != null && doc.getScore() >= scoreThreshold)
                    .toList();
        }

        log.debug("Retrieved {} relevant documents for query", results.size());
        return results;
    }

    /**
     * 建立帶閾值的檢索器
     */
    public static VectorStoreRetriever withThreshold(VectorStore vectorStore, float threshold) {
        return VectorStoreRetriever.builder()
                .vectorStore(vectorStore)
                .scoreThreshold(threshold)
                .build();
    }

    /**
     * 建立帶過濾器的檢索器
     */
    public static VectorStoreRetriever withFilter(VectorStore vectorStore, Map<String, Object> filter) {
        return VectorStoreRetriever.builder()
                .vectorStore(vectorStore)
                .filter(filter)
                .build();
    }
}
