package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 站台 zip 上傳的解析與驗證。
 *
 * Zip bomb / zip-slip 防禦：
 *   - entry 名稱嚴格檢查（.. 段、絕對路徑、反斜線、null byte 一律拒絕）
 *   - 副檔名白名單與路徑淨化沿用 SitePaths.sanitize
 *   - 宣告的解壓大小超過單檔上限即拒絕（不信任宣告值，僅作 fail-fast）
 *   - 實際讀取以位元組計數：單檔與全站皆有硬上限，超過立即中止
 *   - entry 數量上限（與每站檔案數一致）
 * 全部驗證通過後才由 SiteService.replaceFiles 交易式取代現有檔案。
 */
@Service
@Slf4j
public class SiteZipService {

    private static final int BUFFER_SIZE = 8192;

    @Value("${n3n.sites.max-files:200}")
    private int maxFilesPerSite;

    @Value("${n3n.sites.max-file-bytes:5242880}")
    private long maxFileBytes;

    @Value("${n3n.sites.max-site-bytes:20971520}")
    private long maxSiteBytes;

    /**
     * 解析並驗證整個 zip；任一項不合法即丟出 IllegalArgumentException（不做部分套用）。
     *
     * @return 已淨化的檔案清單（content 以 base64 攜帶，沿用既有 upsert 管線）
     */
    public List<SiteFileUpsertEntry> parse(InputStream input) throws IOException {
        List<SiteFileUpsertEntry> entries = new ArrayList<>();
        long totalBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String rawName = entry.getName();
                rejectUnsafeName(rawName);
                // 淨化 + 副檔名白名單（不合法直接丟出）
                String path = SitePaths.sanitize(rawName);

                // 宣告大小 fail-fast（宣告值可造假，實際讀取仍逐位元組計數）
                if (entry.getSize() > maxFileBytes) {
                    throw new IllegalArgumentException(
                            "File in zip too large: " + path + " (declared " + entry.getSize()
                                    + " bytes, max " + maxFileBytes + ")");
                }

                byte[] data = readBounded(zip, path);
                totalBytes += data.length;
                if (totalBytes > maxSiteBytes) {
                    throw new IllegalArgumentException(
                            "Zip contents exceed site size limit (max " + maxSiteBytes + " bytes)");
                }

                entries.add(SiteFileUpsertEntry.builder()
                        .path(path)
                        .contentBase64(Base64.getEncoder().encodeToString(data))
                        .build());
                if (entries.size() > maxFilesPerSite) {
                    throw new IllegalArgumentException(
                            "Too many files in zip (max " + maxFilesPerSite + ")");
                }
                zip.closeEntry();
            }
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Zip contains no site files");
        }
        return entries;
    }

    /**
     * zip-slip 防護：entry 名稱層級的硬性拒絕（SitePaths.sanitize 之前先擋，
     * 確保訊息明確且不依賴後續邏輯）。
     */
    private void rejectUnsafeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Zip entry has an empty name");
        }
        if (name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Zip entry name contains null byte");
        }
        if (name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Zip entry name contains backslash: " + name);
        }
        if (name.startsWith("/")) {
            throw new IllegalArgumentException("Zip entry has absolute path: " + name);
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("Zip entry contains path traversal: " + name);
            }
        }
    }

    /**
     * 讀取單一 entry，超過單檔上限立即中止（zip bomb 硬上限，不信任 header）。
     */
    private byte[] readBounded(ZipInputStream zip, String path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long read = 0;
        int n;
        while ((n = zip.read(buffer)) > 0) {
            read += n;
            if (read > maxFileBytes) {
                throw new IllegalArgumentException(
                        "File in zip too large: " + path + " (max " + maxFileBytes + " bytes)");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
