package com.aiinpocket.n3n.site.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * 站台網域設定與純函式輔助。
 *
 * n3n.sites.base-domain（SITE_BASE_DOMAIN）設定後由 HostSiteFilter 直接對應站台；
 * 空值時功能休眠，僅有路徑式 /sites/{slug}/。支援兩種模式：
 *
 * <ul>
 *   <li>點分隔（預設）：{@code SITE_BASE_DOMAIN=apps.example.com} →
 *       {@code {slug}.apps.example.com}（需要 *.apps.example.com 的憑證）。</li>
 *   <li>連字號後綴：{@code SITE_BASE_DOMAIN=-n3n.example.com}（以 "-" 開頭）→
 *       {@code {slug}-n3n.example.com}。host 仍是 example.com 的一層子網域，
 *       Cloudflare 免費 Universal SSL（僅涵蓋一層 wildcard）即可代理。</li>
 * </ul>
 *
 * 遵循 zero-config 原則：不設定也能在本機完整運作。
 */
@Component
public class SiteDomains {

    private final String baseDomain;
    /** slug 之後的完整字尾（含分隔符），例如 ".apps.example.com" 或 "-n3n.example.com" */
    private final String hostSuffix;

    public SiteDomains(@Value("${n3n.sites.base-domain:}") String baseDomain) {
        String normalized = baseDomain == null
                ? ""
                : baseDomain.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        if (normalized.startsWith("-")) {
            this.hostSuffix = normalized;
            this.baseDomain = normalized.substring(1);
        } else {
            this.baseDomain = normalized;
            this.hostSuffix = normalized.isEmpty() ? "" : "." + normalized;
        }
    }

    /** 子網域託管是否啟用 */
    public boolean isConfigured() {
        return !hostSuffix.isEmpty();
    }

    public String baseDomain() {
        return baseDomain;
    }

    /** slug 之後的完整字尾（含 "." 或 "-" 分隔符）；前端以 {slug}{hostSuffix} 組網址 */
    public String hostSuffix() {
        return hostSuffix;
    }

    /** 站台公開網址：啟用時為 https://{slug}{suffix}/，否則為相對路徑 /sites/{slug}/ */
    public String publicUrl(String slug) {
        if (isConfigured()) {
            return "https://" + slug + hostSuffix + "/";
        }
        return "/sites/" + slug + "/";
    }

    /** 子網域的 host（{slug}{suffix}）用於 CNAME 目標 */
    public String subdomainHost(String slug) {
        return slug + hostSuffix;
    }

    /**
     * 從 Host 解析 slug：僅接受剛好一層的 {slug}{suffix} 且 slug 格式合法。
     */
    public Optional<String> slugFromHost(String host) {
        if (!isConfigured() || host == null) {
            return Optional.empty();
        }
        String normalized = normalizeHost(host);
        if (normalized == null || !normalized.endsWith(hostSuffix)) {
            return Optional.empty();
        }
        String slug = normalized.substring(0, normalized.length() - hostSuffix.length());
        if (slug.isEmpty() || slug.contains(".") || !SiteSlugs.SLUG_PATTERN.matcher(slug).matches()) {
            return Optional.empty();
        }
        return Optional.of(slug);
    }

    /** domain 是否為平台自身的網域（base domain、其子網域或任一 {slug}{suffix}；自訂網域不得使用） */
    public boolean isPlatformDomain(String domain) {
        if (!isConfigured() || domain == null) {
            return false;
        }
        String normalized = normalizeHost(domain);
        return normalized != null
                && (normalized.equals(baseDomain)
                    || normalized.endsWith("." + baseDomain)
                    || normalized.endsWith(hostSuffix));
    }

    /** 小寫、去除尾端點；不含 port（filter 端以 getServerName 取得） */
    public static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
    }
}
