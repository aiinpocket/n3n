package com.aiinpocket.n3n.site.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

/**
 * 站台回應共用的隔離 header（路徑式 /sites/{slug}/ 與 Host 路由皆用）。
 *
 * CSP sandbox 使頁面進入 unique opaque origin：無法讀取平台 origin 的
 * localStorage / cookies，發出的請求皆為跨來源且不帶憑證。子網域託管時
 * 已是不同 origin，仍保留此 header 作 defense in depth（成本極低）。
 */
public final class SiteSecurityHeaders {

    public static final String SITE_CSP =
            "sandbox allow-scripts allow-forms allow-popups allow-modals; "
                    + "default-src 'self' 'unsafe-inline' data: blob:";
    public static final String CACHE_CONTROL = "public, max-age=60";

    private SiteSecurityHeaders() {
    }

    /** 寫入所有站台回應必備的隔離 header（含 404） */
    public static void apply(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", SITE_CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
    }
}
