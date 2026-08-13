package com.aiinpocket.n3n.site.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * 站台網域設定與純函式輔助。
 *
 * n3n.sites.base-domain（SITE_BASE_DOMAIN）設定後，{slug}.{base-domain}
 * 由 HostSiteFilter 直接對應站台；空值時功能休眠，僅有路徑式 /sites/{slug}/。
 * 遵循 zero-config 原則：不設定也能在本機完整運作。
 */
@Component
public class SiteDomains {

    private final String baseDomain;

    public SiteDomains(@Value("${n3n.sites.base-domain:}") String baseDomain) {
        this.baseDomain = baseDomain == null
                ? ""
                : baseDomain.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
    }

    /** 子網域託管是否啟用 */
    public boolean isConfigured() {
        return !baseDomain.isEmpty();
    }

    public String baseDomain() {
        return baseDomain;
    }

    /** 站台公開網址：啟用時為 https://{slug}.{base}/，否則為相對路徑 /sites/{slug}/ */
    public String publicUrl(String slug) {
        if (isConfigured()) {
            return "https://" + slug + "." + baseDomain + "/";
        }
        return "/sites/" + slug + "/";
    }

    /** 子網域的 host（{slug}.{base}）用於 CNAME 目標 */
    public String subdomainHost(String slug) {
        return slug + "." + baseDomain;
    }

    /**
     * 從 Host 解析 slug：僅接受剛好一層的 {slug}.{base-domain} 且 slug 格式合法。
     */
    public Optional<String> slugFromHost(String host) {
        if (!isConfigured() || host == null) {
            return Optional.empty();
        }
        String normalized = normalizeHost(host);
        String suffix = "." + baseDomain;
        if (normalized == null || !normalized.endsWith(suffix)) {
            return Optional.empty();
        }
        String slug = normalized.substring(0, normalized.length() - suffix.length());
        if (slug.isEmpty() || slug.contains(".") || !SiteSlugs.SLUG_PATTERN.matcher(slug).matches()) {
            return Optional.empty();
        }
        return Optional.of(slug);
    }

    /** domain 是否為平台自身的 base domain 或其子網域（自訂網域不得使用） */
    public boolean isPlatformDomain(String domain) {
        if (!isConfigured() || domain == null) {
            return false;
        }
        String normalized = normalizeHost(domain);
        return normalized != null
                && (normalized.equals(baseDomain) || normalized.endsWith("." + baseDomain));
    }

    /** 小寫、去除尾端點；不含 port（filter 端以 getServerName 取得） */
    public static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
    }
}
