package com.aiinpocket.n3n.ai.rag.document;

import java.io.InputStream;
import java.util.List;

/**
 * 文檔載入器介面
 *
 * 定義從不同來源載入文檔的方法。
 */
public interface DocumentLoader {

    /**
     * 從檔案路徑載入文檔
     *
     * @param filePath 檔案路徑
     * @return 文檔列表
     */
    List<Document> load(String filePath);

    /**
     * 從輸入流載入文檔
     *
     * @param inputStream 輸入流
     * @param sourceName 來源名稱
     * @return 文檔列表
     */
    List<Document> load(InputStream inputStream, String sourceName);

    /**
     * 支援的檔案類型
     */
    List<String> getSupportedExtensions();

    /**
     * 檢查是否支援指定的檔案類型
     */
    default boolean supports(String filePath) {
        if (filePath == null) return false;
        String lowerPath = filePath.toLowerCase();
        return getSupportedExtensions().stream()
                .anyMatch(ext -> lowerPath.endsWith("." + ext.toLowerCase()));
    }
}
