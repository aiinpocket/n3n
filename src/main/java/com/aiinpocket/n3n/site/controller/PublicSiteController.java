package com.aiinpocket.n3n.site.controller;

import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.service.SitePaths;
import com.aiinpocket.n3n.site.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * 公開網站託管：GET /sites/{slug}/**（不在 /api 下、免登入）。
 *
 * 安全模型（關鍵）：使用者撰寫的 HTML/JS 由平台 origin 提供，若不隔離，
 * 站內 script 可讀取平台 localStorage 的 JWT、以使用者身分呼叫 API。
 * 因此每個回應都帶 CSP `sandbox` 指令 —— 它強制瀏覽器將頁面放入
 * 「唯一不透明 origin」（unique opaque origin），使頁面：
 *   - 無法讀取平台 origin 的 localStorage / cookies / IndexedDB
 *   - 發出的請求皆為跨來源且不帶憑證，無法以使用者身分呼叫 /api/**
 * 同時保留 allow-scripts / allow-forms 等能力讓網站正常互動。
 * 搭配 X-Content-Type-Options: nosniff 與 Referrer-Policy: no-referrer。
 */
@RestController
@RequiredArgsConstructor
public class PublicSiteController {

    private static final String INDEX_HTML = "index.html";

    private final SiteService siteService;

    /**
     * /sites/{slug} → 301 到 /sites/{slug}/，讓相對路徑正確解析。
     */
    @GetMapping("/sites/{slug}")
    public ResponseEntity<Void> redirectToRoot(@PathVariable String slug) {
        if (!isSafeSlug(slug)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/sites/" + slug + "/"))
                .build();
    }

    @GetMapping("/sites/{slug}/**")
    public ResponseEntity<byte[]> serve(@PathVariable String slug, HttpServletRequest request) {
        if (!isSafeSlug(slug)) {
            return notFound();
        }

        String path = extractSitePath(request, slug);
        if (path == null) {
            return notFound();
        }
        if (path.isEmpty()) {
            path = INDEX_HTML;
        }

        Optional<SiteFile> file = siteService.findPublicFile(slug, path);

        // SPA-ish fallback：無副檔名的路徑（前端路由）退回 index.html
        if (file.isEmpty() && !SitePaths.hasExtension(path)) {
            file = siteService.findPublicFile(slug, INDEX_HTML);
        }
        if (file.isEmpty()) {
            return notFound();
        }

        SiteFile siteFile = file.get();
        return withSecurityHeaders(ResponseEntity.ok())
                .contentType(parseMediaType(siteFile.getContentType()))
                .contentLength(siteFile.getSizeBytes())
                .body(siteFile.getData());
    }

    /**
     * 從 request URI 取出 slug 之後的站內路徑並淨化。
     * 回傳 null 表示路徑不合法（含 ..、反斜線等），呼叫端應回 404。
     */
    private String extractSitePath(HttpServletRequest request, String slug) {
        String uri = request.getRequestURI();
        String prefix = "/sites/" + slug + "/";
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        String raw = uri.substring(prefix.length());
        if (raw.isEmpty()) {
            return "";
        }
        try {
            return SitePaths.sanitize(raw);
        } catch (IllegalArgumentException e) {
            // 無副檔名（SPA 路由）也會被 sanitize 的白名單擋下 — 這裡改用寬鬆檢查
            return SitePaths.sanitizeLoose(raw);
        }
    }

    private static boolean isSafeSlug(String slug) {
        return slug != null && com.aiinpocket.n3n.site.service.SiteSlugs.SLUG_PATTERN.matcher(slug).matches();
    }

    private static ResponseEntity<byte[]> notFound() {
        return withSecurityHeaders(ResponseEntity.status(HttpStatus.NOT_FOUND)).build();
    }

    /**
     * 每個 /sites/** 回應（含 404）都必須帶隔離 header。
     * 這裡先寫入，Spring Security 的 HeaderWriter 見已有同名 header 便不覆寫。
     */
    private static ResponseEntity.BodyBuilder withSecurityHeaders(ResponseEntity.BodyBuilder builder) {
        return builder
                .header("Content-Security-Policy", com.aiinpocket.n3n.site.web.SiteSecurityHeaders.SITE_CSP)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CACHE_CONTROL, com.aiinpocket.n3n.site.web.SiteSecurityHeaders.CACHE_CONTROL);
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
