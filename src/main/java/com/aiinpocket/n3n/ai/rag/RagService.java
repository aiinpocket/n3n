package com.aiinpocket.n3n.ai.rag;

import com.aiinpocket.n3n.ai.rag.chain.RetrievalQAChain;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.document.loaders.TextLoader;
import com.aiinpocket.n3n.ai.rag.retriever.VectorStoreRetriever;
import com.aiinpocket.n3n.ai.rag.splitter.RecursiveCharacterSplitter;
import com.aiinpocket.n3n.ai.rag.vectorstore.InMemoryVectorStore;
import com.aiinpocket.n3n.ai.rag.vectorstore.RagVectorStore;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 服務
 *
 * 提供統一的 RAG（Retrieval-Augmented Generation）操作介面。
 * 整合文檔載入、分割、向量化、檢索和問答功能。
 *
 * <p><b>租戶隔離：</b>所有向量存儲皆以呼叫者的 {@code userId} 作為前綴進行隔離。
 * 有效存儲鍵一律由伺服器端推導為 {@code userId + ":" + (storeName 空白 ? "default" : storeName)}，
 * 呼叫者提供的 {@code storeName} 永遠不會單獨作為鍵，因此不同使用者即使使用相同的
 * storeName（包含 null/預設）也不會互相看到對方的文件；同一使用者則可跨自己的多個流程
 * 共用同名的命名存儲。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final AiService aiService;
    private final TextLoader textLoader;
    private final RecursiveCharacterSplitter textSplitter;

    // 命名的向量存儲（用於多個知識庫），鍵一律為 userId 範圍化後的有效鍵
    private final Map<String, RagVectorStore> namedStores = new ConcurrentHashMap<>();

    /**
     * 索引文檔到指定的存儲（以 userId 隔離）
     *
     * @param content  文檔內容
     * @param metadata 元資料
     * @param storeName 存儲名稱（null/空白 使用該使用者的 "default"）
     * @param userId   已解析的使用者 ID（必填，租戶隔離鍵）
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexDocument(String content, Map<String, Object> metadata,
                                      String storeName, String userId) {
        Document doc = Document.of(content, metadata != null ? metadata : new HashMap<>());
        List<Document> chunks = textSplitter.split(doc);

        RagVectorStore store = getStore(userId, storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed document into {} chunks in store '{}'", ids.size(),
                effectiveKey(userId, storeName));
        return ids;
    }

    /**
     * 索引文字檔案到指定的存儲（以 userId 隔離）
     */
    public List<String> indexFile(String filePath, String storeName, String userId) {
        List<Document> documents = textLoader.load(filePath);
        List<Document> chunks = textSplitter.splitDocuments(documents);

        RagVectorStore store = getStore(userId, storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed file {} into {} chunks in store '{}'",
                filePath, ids.size(), effectiveKey(userId, storeName));
        return ids;
    }

    /**
     * 索引輸入流到指定的存儲（以 userId 隔離）
     */
    public List<String> indexStream(InputStream inputStream, String sourceName,
                                    String storeName, String userId) {
        List<Document> documents = textLoader.load(inputStream, sourceName);
        List<Document> chunks = textSplitter.splitDocuments(documents);

        RagVectorStore store = getStore(userId, storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed stream {} into {} chunks in store '{}'",
                sourceName, ids.size(), effectiveKey(userId, storeName));
        return ids;
    }

    /**
     * 語義搜尋（指定存儲，以 userId 隔離）
     *
     * @param query    查詢文字
     * @param topK     返回數量
     * @param storeName 存儲名稱（null/空白 使用該使用者的 "default"）
     * @param userId   已解析的使用者 ID（必填）
     * @return 相關文檔列表
     */
    public List<Document> search(String query, int topK, String storeName, String userId) {
        RagVectorStore store = getStore(userId, storeName);
        return store.similaritySearchWithScore(query, topK);
    }

    /**
     * RAG 問答（指定存儲，以 userId 隔離）
     *
     * @param question  問題
     * @param storeName 存儲名稱（null/空白 使用該使用者的 "default"）
     * @param userId    已解析的使用者 ID（必填）
     * @return 答案
     */
    public String ask(String question, String storeName, String userId) {
        RagVectorStore store = getStore(userId, storeName);
        VectorStoreRetriever retriever = new VectorStoreRetriever(store);
        RetrievalQAChain chain = RetrievalQAChain.simple(retriever, aiService);

        return chain.run(question);
    }

    /**
     * RAG 問答（帶來源，以 userId 隔離）
     */
    public RetrievalQAChain.QAResult askWithSources(String question, String storeName, String userId) {
        RagVectorStore store = getStore(userId, storeName);
        VectorStoreRetriever retriever = new VectorStoreRetriever(store);
        RetrievalQAChain chain = RetrievalQAChain.builder()
                .retriever(retriever)
                .aiService(aiService)
                .returnSourceDocuments(true)
                .build();

        return chain.query(question);
    }

    /**
     * 刪除命名的向量存儲（以 userId 隔離）
     *
     * @param name   存儲名稱
     * @param userId 已解析的使用者 ID（必填）
     */
    public void deleteStore(String name, String userId) {
        String key = effectiveKey(userId, name);
        RagVectorStore store = namedStores.remove(key);
        if (store != null) {
            store.deleteAll();
            log.info("Deleted vector store: {}", key);
        }
    }

    /**
     * 清除存儲中的所有文檔（以 userId 隔離）
     *
     * @param storeName 存儲名稱（null/空白 清除該使用者的 "default"）
     * @param userId    已解析的使用者 ID（必填）
     */
    public void clearStore(String storeName, String userId) {
        RagVectorStore store = getStore(userId, storeName);
        store.deleteAll();
        log.info("Cleared vector store: {}", effectiveKey(userId, storeName));
    }

    /**
     * 取得（必要時建立）以 userId 隔離的向量存儲。
     */
    private RagVectorStore getStore(String userId, String storeName) {
        String key = effectiveKey(userId, storeName);
        return namedStores.computeIfAbsent(key, k -> new InMemoryVectorStore(aiService));
    }

    /**
     * 伺服器端推導有效存儲鍵。呼叫者提供的 storeName 永遠不會單獨作為鍵。
     *
     * @throws IllegalArgumentException 當 userId 缺失時（fail-closed，避免跨租戶洩漏）
     */
    private String effectiveKey(String userId, String storeName) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required for RAG store access");
        }
        String name = (storeName == null || storeName.isBlank()) ? "default" : storeName;
        return userId + ":" + name;
    }
}
