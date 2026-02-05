package com.aiinpocket.n3n.ai.rag.vectorstore;

import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 記憶體向量存儲
 *
 * 適用於開發和測試環境。
 * 生產環境應使用 Redis、PostgreSQL (pgvector) 或專用向量資料庫。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InMemoryVectorStore implements VectorStore {

    private final AiService aiService;
    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    @Override
    public List<String> addDocuments(List<Document> docs) {
        List<String> ids = new ArrayList<>();

        for (Document doc : docs) {
            // 產生嵌入向量
            if (doc.getEmbedding() == null && doc.getContent() != null) {
                float[] embedding = aiService.getEmbedding(doc.getContent());
                doc.setEmbedding(embedding);
            }

            String id = doc.getId();
            documents.put(id, doc);
            ids.add(id);
        }

        log.debug("Added {} documents to in-memory vector store", ids.size());
        return ids;
    }

    @Override
    public List<Document> similaritySearch(String query, int k) {
        return similaritySearchWithScore(query, k);
    }

    @Override
    public List<Document> similaritySearchWithScore(String query, int k) {
        float[] queryEmbedding = aiService.getEmbedding(query);
        return similaritySearchByVector(queryEmbedding, k);
    }

    @Override
    public List<Document> similaritySearch(String query, int k, Map<String, Object> filter) {
        List<Document> results = similaritySearchWithScore(query, k * 2);

        // 應用過濾器
        return results.stream()
                .filter(doc -> matchesFilter(doc, filter))
                .limit(k)
                .collect(Collectors.toList());
    }

    @Override
    public List<Document> similaritySearchByVector(float[] embedding, int k) {
        return documents.values().stream()
                .filter(doc -> doc.getEmbedding() != null)
                .map(doc -> {
                    float score = cosineSimilarity(embedding, doc.getEmbedding());
                    Document result = Document.builder()
                            .id(doc.getId())
                            .content(doc.getContent())
                            .metadata(doc.getMetadata() != null ? new HashMap<>(doc.getMetadata()) : new HashMap<>())
                            .embedding(doc.getEmbedding())
                            .score(score)
                            .build();
                    return result;
                })
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(k)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(List<String> ids) {
        ids.forEach(documents::remove);
        log.debug("Deleted {} documents from in-memory vector store", ids.size());
    }

    @Override
    public void deleteAll() {
        int count = documents.size();
        documents.clear();
        log.info("Cleared all {} documents from in-memory vector store", count);
    }

    @Override
    public String getName() {
        return "in-memory";
    }

    /**
     * 計算餘弦相似度
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /**
     * 檢查文檔是否符合過濾條件
     */
    private boolean matchesFilter(Document doc, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return false;
        }

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object docValue = metadata.get(entry.getKey());
            if (!Objects.equals(docValue, entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 取得文檔數量
     */
    public int size() {
        return documents.size();
    }
}
