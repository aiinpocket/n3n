package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hosted App zip 的安全解包。
 *
 * 防禦沿用 site 模組 SiteZipService 的做法（zip-slip / zip-bomb）：
 *   - entry 名稱嚴格檢查（.. 段、絕對路徑、反斜線、null byte 一律拒絕）
 *   - 不信任宣告大小，實際逐位元組計數；單檔與總量皆有硬上限
 *   - entry 數量上限
 * 與 site 不同處：這裡不能套副檔名白名單（Dockerfile、compose.yml、
 * 原始碼都是合法內容），改以總量與數量上限控管。
 */
@Service
@RequiredArgsConstructor
public class AppZipReader {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ENTRIES = 2000;
    /** 解壓後總量允許為 zip 上限的數倍（原始碼壓縮率高），仍為硬上限防 zip bomb */
    private static final int EXPANSION_FACTOR = 5;

    private final HostedAppProperties properties;

    /**
     * 解包整個 zip 為（相對路徑 → 內容），任一項不合法即丟出
     * IllegalArgumentException，不做部分套用。保留 entry 順序。
     */
    public Map<String, byte[]> read(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("請提供 zip 檔");
        }
        if (zipBytes.length > properties.maxZipBytes()) {
            throw new IllegalArgumentException(
                    "zip 檔過大（上限 " + properties.getMaxZipMb() + " MB）");
        }
        long maxTotalBytes = properties.maxZipBytes() * EXPANSION_FACTOR;

        Map<String, byte[]> files = new LinkedHashMap<>();
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName();
                rejectUnsafeName(name);

                byte[] data = readBounded(zip, name, maxTotalBytes - totalBytes);
                totalBytes += data.length;
                files.put(name, data);
                if (files.size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "zip 內檔案數量過多（上限 " + MAX_ENTRIES + "）");
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("zip 檔格式無效或已損毀", e);
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("zip 內沒有任何檔案");
        }
        return files;
    }

    /** zip-slip 防護：entry 名稱層級的硬性拒絕（與 SiteZipService 相同規則） */
    private void rejectUnsafeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("zip entry 名稱為空");
        }
        if (name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("zip entry 名稱含 null byte");
        }
        if (name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("zip entry 名稱含反斜線: " + name);
        }
        if (name.startsWith("/")) {
            throw new IllegalArgumentException("zip entry 為絕對路徑: " + name);
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException("zip entry 含路徑跳脫: " + name);
            }
        }
    }

    /** 逐位元組計數讀取，超過剩餘配額立即中止（不信任 zip header 宣告值） */
    private byte[] readBounded(ZipInputStream zip, String name, long remainingQuota) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long read = 0;
        int n;
        while ((n = zip.read(buffer)) > 0) {
            read += n;
            if (read > remainingQuota) {
                throw new IllegalArgumentException(
                        "zip 解壓後內容超過大小上限（" + name + "），疑似 zip bomb");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
