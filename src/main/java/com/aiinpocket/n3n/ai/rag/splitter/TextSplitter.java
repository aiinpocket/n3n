package com.aiinpocket.n3n.ai.rag.splitter;

import com.aiinpocket.n3n.ai.rag.document.Document;

import java.util.List;

/**
 * 文字分割器介面
 *
 * 將大型文檔分割成較小的片段以便於處理和檢索。
 */
public interface TextSplitter {

    /**
     * 分割文檔
     *
     * @param document 要分割的文檔
     * @return 分割後的文檔片段列表
     */
    List<Document> split(Document document);

    /**
     * 分割多個文檔
     *
     * @param documents 要分割的文檔列表
     * @return 分割後的文檔片段列表
     */
    default List<Document> splitDocuments(List<Document> documents) {
        return documents.stream()
                .flatMap(doc -> split(doc).stream())
                .toList();
    }

    /**
     * 分割純文字
     *
     * @param text 要分割的文字
     * @return 分割後的文字片段列表
     */
    List<String> splitText(String text);
}
