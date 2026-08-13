package com.aiinpocket.n3n.site.web;

import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.service.SiteDomainService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SitePaths;
import com.aiinpocket.n3n.site.service.SiteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host 路由：Host 為 {slug}.{base-domain} 或已驗證自訂網域時，
 * 在 Spring Security 之前直接以根路徑服務站台檔案（/, /style.css …）。
 *
 * 不遮蔽主應用的保證：
 *   1. base-domain 未設定 → 完全休眠，直接 pass through
 *   2. Host 等於 base-domain 本身（apex）→ pass through
 *   3. Host 非 *.{base-domain} 且非已驗證自訂網域 → pass through
 *      （主應用 host 永遠落在此分支；未命中結果有短 TTL 快取，避免每請求查 DB）
 *   4. 命中站台的 Host 上，所有路徑（含 /api/**）都只回站台內容——
 *      站台網域上不暴露任何平台 API
 * 命中時回應一律帶 SiteSecurityHeaders（CSP sandbox，defense in depth）。
 */
@RequiredArgsConstructor
@Slf4j
public class HostSiteFilter extends OncePerRequestFilter {

    private static final String INDEX_HTML = "index.html";
    private static final long MISS_CACHE_TTL_MS = 30_000L;
    private static final int MISS_CACHE_MAX_ENTRIES = 2_000;

    private final SiteDomains siteDomains;
    private final SiteDomainService siteDomainService;
    private final SiteService siteService;

    /** 未命中 host 的短 TTL 快取（保護主應用 host 的每請求 DB 查詢） */
    private final Map<String, Long> missCache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!siteDomains.isConfigured()) {
            chain.doFilter(request, response);
            return;
        }
        String host = SiteDomains.normalizeHost(request.getServerName());
        if (host == null || host.equals(siteDomains.baseDomain()) || isCachedMiss(host)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<Site> site = siteDomainService.resolveHost(host);
        if (site.isEmpty()) {
            if (siteDomains.slugFromHost(host).isPresent()) {
                // 我們的 wildcard 網域但站台不存在/未發佈 → 404，不能落到主應用
                writeNotFound(response);
                return;
            }
            cacheMiss(host);
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            // 站台網域只提供靜態內容
            writeNotFound(response);
            return;
        }
        serve(site.get(), request, response);
    }

    // ---------- Serving ----------

    private void serve(Site site, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = sitePath(request);
        if (path == null) {
            writeNotFound(response);
            return;
        }
        if (path.isEmpty()) {
            path = INDEX_HTML;
        }

        Optional<SiteFile> file = siteService.findPublicFile(site, path);
        // SPA fallback：無副檔名的路徑退回 index.html
        if (file.isEmpty() && !SitePaths.hasExtension(path)) {
            file = siteService.findPublicFile(site, INDEX_HTML);
        }
        if (file.isEmpty()) {
            writeNotFound(response);
            return;
        }

        SiteFile siteFile = file.get();
        SiteSecurityHeaders.apply(response);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(siteFile.getContentType() != null
                ? siteFile.getContentType() : "application/octet-stream");
        response.setContentLengthLong(siteFile.getSizeBytes());
        response.getOutputStream().write(siteFile.getData());
    }

    /** 取出並淨化站內路徑；null = 不合法（回 404） */
    private String sitePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty() || "/".equals(uri)) {
            return "";
        }
        String raw = uri.startsWith("/") ? uri.substring(1) : uri;
        return SitePaths.sanitizeLoose(raw);
    }

    private static void writeNotFound(HttpServletResponse response) {
        SiteSecurityHeaders.apply(response);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    // ---------- Miss cache ----------

    private boolean isCachedMiss(String host) {
        Long expiry = missCache.get(host);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            missCache.remove(host);
            return false;
        }
        return true;
    }

    private void cacheMiss(String host) {
        if (missCache.size() >= MISS_CACHE_MAX_ENTRIES) {
            missCache.clear();
        }
        missCache.put(host, System.currentTimeMillis() + MISS_CACHE_TTL_MS);
    }
}
