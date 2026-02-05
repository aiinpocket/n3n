package com.aiinpocket.n3n.ai.rag;

import com.aiinpocket.n3n.ai.rag.chain.RetrievalQAChain;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.document.DocumentLoader;
import com.aiinpocket.n3n.ai.rag.document.loaders.TextLoader;
import com.aiinpocket.n3n.ai.rag.retriever.VectorStoreRetriever;
import com.aiinpocket.n3n.ai.rag.splitter.RecursiveCharacterSplitter;
import com.aiinpocket.n3n.ai.rag.splitter.TextSplitter;
import com.aiinpocket.n3n.ai.rag.vectorstore.InMemoryVectorStore;
import com.aiinpocket.n3n.ai.rag.vectorstore.VectorStore;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final AiService aiService;
    private final TextLoader textLoader;
    private final RecursiveCharacterSplitter textSplitter;
    private final InMemoryVectorStore defaultVectorStore;

    // 命名的向量存儲（用於多個知識庫）
    private final Map<String, VectorStore> namedStores = new ConcurrentHashMap<>();

    /**
     * 索引文檔
     *
     * @param content 文檔內容
     * @param metadata 元資料
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexDocument(String content, Map<String, Object> metadata) {
        return indexDocument(content, metadata, null);
    }

    /**
     * 索引文檔到指定的存儲
     *
     * @param content 文檔內容
     * @param metadata 元資料
     * @param storeName 存儲名稱（null 使用預設）
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexDocument(String content, Map<String, Object> metadata, String storeName) {
        Document doc = Document.of(content, metadata != null ? metadata : new HashMap<>());
        List<Document> chunks = textSplitter.split(doc);

        VectorStore store = getStore(storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed document into {} chunks in store '{}'", ids.size(),
                storeName != null ? storeName : "default");
        return ids;
    }

    /**
     * 索引文字檔案
     *
     * @param filePath 檔案路徑
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexFile(String filePath) {
        return indexFile(filePath, null);
    }

    /**
     * 索引文字檔案到指定的存儲
     *
     * @param filePath 檔案路徑
     * @param storeName 存儲名稱
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexFile(String filePath, String storeName) {
        List<Document> documents = textLoader.load(filePath);
        List<Document> chunks = textSplitter.splitDocuments(documents);

        VectorStore store = getStore(storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed file {} into {} chunks in store '{}'",
                filePath, ids.size(), storeName != null ? storeName : "default");
        return ids;
    }

    /**
     * 索引輸入流
     *
     * @param inputStream 輸入流
     * @param sourceName 來源名稱
     * @param storeName 存儲名稱
     * @return 索引的文檔 ID 列表
     */
    public List<String> indexStream(InputStream inputStream, String sourceName, String storeName) {
        List<Document> documents = textLoader.load(inputStream, sourceName);
        List<Document> chunks = textSplitter.splitDocuments(documents);

        VectorStore store = getStore(storeName);
        List<String> ids = store.addDocuments(chunks);

        log.info("Indexed stream {} into {} chunks in store '{}'",
                sourceName, ids.size(), storeName != null ? storeName : "default");
        return ids;
    }

    /**
     * 語義搜尋
     *
     * @param query 查詢文字
     * @param topK 返回數量
     * @return 相關文檔列表
     */
    public List<Document> search(String query, int topK) {
        return search(query, topK, null);
    }

    /**
     * 語義搜尋（指定存儲）
     *
     * @param query 查詢文字
     * @param topK 返回數量
     * @param storeName 存儲名稱
     * @return 相關文檔列表
     */
    public List<Document> search(String query, int topK, String storeName) {
        VectorStore store = getStore(storeName);
        return store.similaritySearchWithScore(query, topK);
    }

    /**
     * RAG 問答
     *
     * @param question 問題
     * @return 答案
     */
    public String ask(String question) {
        return ask(question, null);
    }

    /**
     * RAG 問答（指定存儲）
     *
     * @param question 問題
     * @param storeName 存儲名稱
     * @return 答案
     */
    public String ask(String question, String storeName) {
        VectorStore store = getStore(storeName);
        VectorStoreRetriever retriever = new VectorStoreRetriever(store);
        RetrievalQAChain chain = RetrievalQAChain.simple(retriever, aiService);

        return chain.run(question);
    }

    /**
     * RAG 問答（帶來源）
     *
     * @param question 問題
     * @param storeName 存儲名稱
     * @return 問答結果
     */
    public RetrievalQAChain.QAResult askWithSources(String question, String storeName) {
        VectorStore store = getStore(storeName);
        VectorStoreRetriever retriever = new VectorStoreRetriever(store);
        RetrievalQAChain chain = RetrievalQAChain.builder()
                .retriever(retriever)
                .aiService(aiService)
                .returnSourceDocuments(true)
                .build();

        return chain.query(question);
    }

    /**
     * 建立命名的向量存儲
     *
     * @param name 存儲名稱
     * @return 向量存儲
     */
    public VectorStore createStore(String name) {
        InMemoryVectorStore store = new InMemoryVectorStore(aiService);
        namedStores.put(name, store);
        log.info("Created vector store: {}", name);
        return store;
    }

    /**
     * 刪除命名的向量存儲
     *
     * @param name 存儲名稱
     */
    public void deleteStore(String name) {
        VectorStore store = namedStores.remove(name);
        if (store != null) {
            store.deleteAll();
            log.info("Deleted vector store: {}", name);
        }
    }

    /**
     * 清除存儲中的所有文檔
     *
     * @param storeName 存儲名稱（null 清除預設存儲）
     */
    public void clearStore(String storeName) {
        VectorStore store = getStore(storeName);
        store.deleteAll();
        log.info("Cleared vector store: {}", storeName != null ? storeName : "default");
    }

    /**
     * 取得向量存儲
     */
    private VectorStore getStore(String name) {
        if (name == null) {
            return defaultVectorStore;
        }
        return namedStores.computeIfAbsent(name, k -> new InMemoryVectorStore(aiService));
    }
}
