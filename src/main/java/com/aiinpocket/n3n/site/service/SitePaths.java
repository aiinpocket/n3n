package com.aiinpocket.n3n.site.service;

import java.util.Locale;
import java.util.Map;

/**
 * 網站檔案路徑淨化與 content-type 推斷。
 *
 * 安全原則：路徑一律為站內相對路徑，拒絕任何可能造成目錄穿越或
 * 混淆的輸入（.., 開頭 /, 反斜線, null byte, 控制字元）。
 * 副檔名採白名單制，未列入者一律拒絕。
 */
public final class SitePaths {

    public static final int MAX_PATH_LENGTH = 400;

    /** 副檔名 → content type 白名單（未列入的副檔名一律拒絕） */
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("map", "application/json")
    );

    private SitePaths() {
    }

    /**
     * 淨化並驗證站內相對路徑。
     *
     * @return 正規化後的路徑（例如 "assets/main.js"）
     * @throws IllegalArgumentException 路徑不合法（訊息可安全回傳給呼叫者）
     */
    public static String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        String path = rawPath.trim();

        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("File path contains null byte");
        }
        if (path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("File path must use forward slashes: " + path);
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("File path must be relative (no leading /): " + path);
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("File path too long (max " + MAX_PATH_LENGTH + " chars)");
        }
        for (char c : path.toCharArray()) {
            if (c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("File path contains control characters");
            }
        }

        // 正規化：折疊重複斜線，逐段檢查
        StringBuilder normalized = new StringBuilder(path.length());
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue; // 折疊 "a//b" → "a/b"
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("File path must not contain . or .. segments: " + path);
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("File path is empty after normalization: " + rawPath);
        }

        String result = normalized.toString();
        // 副檔名白名單（同時擋掉無副檔名的檔案）
        inferContentType(result);
        return result;
    }

    /**
     * 寬鬆版淨化（public serving 專用）：允許無副檔名的路徑（SPA 路由
     * fallback 用），但仍拒絕目錄穿越、反斜線、null byte 與過長路徑。
     *
     * @return 正規化後的路徑；不合法時回傳 null（呼叫端應回 404）
     */
    public static String sanitizeLoose(String raw) {
        if (raw == null
                || raw.indexOf('\0') >= 0
                || raw.indexOf('\\') >= 0
                || raw.length() > MAX_PATH_LENGTH) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(raw.length());
        for (String segment : raw.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..")) {
                return null;
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    /**
     * 由副檔名推斷 content type；副檔名不在白名單時拋出例外。
     */
    public static String inferContentType(String path) {
        String ext = extensionOf(path);
        String contentType = ext == null ? null : CONTENT_TYPES.get(ext);
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "Unsupported file extension for site file: " + path
                            + " (allowed: " + String.join(", ", CONTENT_TYPES.keySet().stream().sorted().toList()) + ")");
        }
        return contentType;
    }

    /**
     * 路徑是否帶有副檔名（用於 public serving 的 SPA fallback 判斷）。
     */
    public static boolean hasExtension(String path) {
        return extensionOf(path) != null;
    }

    private static String extensionOf(String path) {
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
