package com.aiinpocket.n3n.artifact.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Artifact 檔案系統儲存服務。
 *
 * 檔案存放於 {root}/{ownerId}/{artifactId}{sanitized-extension}，
 * 目錄依需求自動建立，並強制大小上限（n3n.artifacts.max-file-size-mb）。
 */
@Service
@Slf4j
public class ArtifactStorageService {

    /** 副檔名僅允許英數字，最長 10 字元（防止 path traversal / 特殊字元）。 */
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-zA-Z0-9]{1,10}$");

    private static final int STREAM_BUFFER_SIZE = 8192;

    private final Path rootPath;
    private final long maxFileSizeBytes;

    public ArtifactStorageService(
            @Value("${n3n.artifacts.storage-path}") String storagePath,
            @Value("${n3n.artifacts.max-file-size-mb}") long maxFileSizeMb) {
        this.rootPath = Paths.get(storagePath).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    /**
     * 依 filename 產生相對儲存路徑："{ownerId}/{artifactId}{ext}"。
     */
    public String buildStoragePath(UUID ownerId, UUID artifactId, String filename) {
        String extension = extractSafeExtension(filename);
        return ownerId + "/" + artifactId + extension;
    }

    /**
     * 寫入 byte[] 內容，回傳實際寫入大小。
     */
    public long store(String storagePath, byte[] data) throws IOException {
        if (data.length > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    "Artifact exceeds max file size of " + (maxFileSizeBytes / (1024 * 1024)) + " MB");
        }
        Path target = resolve(storagePath);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return data.length;
    }

    /**
     * 以串流方式寫入（用於遠端 URL 下載），邊寫邊檢查大小上限。
     */
    public long store(String storagePath, InputStream in) throws IOException {
        Path target = resolve(storagePath);
        Files.createDirectories(target.getParent());
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxFileSizeBytes) {
                    out.close();
                    Files.deleteIfExists(target);
                    throw new IllegalArgumentException(
                            "Artifact exceeds max file size of " + (maxFileSizeBytes / (1024 * 1024)) + " MB");
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return total;
    }

    /**
     * 解析相對儲存路徑為絕對路徑，並驗證仍位於根目錄內。
     */
    public Path resolve(String storagePath) {
        Path resolved = rootPath.resolve(storagePath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException("Invalid artifact storage path");
        }
        return resolved;
    }

    /**
     * 刪除檔案（不存在時僅記 log，不拋錯）。
     */
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            log.warn("Failed to delete artifact file {}: {}", storagePath, e.getMessage());
        }
    }

    /**
     * 清除路徑分隔符與危險字元，防止 path traversal。
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "artifact";
        }
        // 只取最後一段（去除任何路徑成分）
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // 移除控制字元與檔案系統不允許的字元
        name = name.replaceAll("[\\x00-\\x1f\"*:<>?|]", "_").trim();
        // 防止 "." / ".." 或空字串
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return "artifact";
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name;
    }

    /**
     * 從檔名擷取安全的副檔名（含點），不合法時回傳空字串。
     */
    private static String extractSafeExtension(String filename) {
        String name = sanitizeFilename(filename);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1);
        return SAFE_EXTENSION.matcher(ext).matches() ? "." + ext.toLowerCase() : "";
    }
}
