package com.aiinpocket.n3n.backup.storage;

import java.io.IOException;
import java.util.List;

/**
 * 雲端儲存提供者介面
 */
public interface CloudStorageProvider extends AutoCloseable {

    /**
     * 上傳檔案
     */
    void upload(String filename, byte[] data) throws IOException;

    /**
     * 下載檔案
     */
    byte[] download(String filename) throws IOException;

    /**
     * 列出檔案（支援前綴過濾）
     */
    List<StorageFileInfo> list(String prefix) throws IOException;

    /**
     * 刪除檔案
     */
    void delete(String filename) throws IOException;

    /**
     * 測試連線
     */
    boolean testConnection();

    /**
     * 取得提供者類型
     */
    String getProviderType();

    /**
     * 儲存檔案資訊
     */
    /**
     * 釋放資源（預設無操作）
     */
    @Override
    default void close() {}

    record StorageFileInfo(String filename, long size, String lastModified) {}
}
