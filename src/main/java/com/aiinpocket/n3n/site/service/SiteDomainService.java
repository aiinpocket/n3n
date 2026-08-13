package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.site.dto.SiteDnsRecord;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 自訂網域管理與 Host → Site 解析。
 *
 * 驗證流程：使用者設定網域後取得 token，需建立兩筆 DNS 記錄：
 *   TXT  _n3n-verify.{domain} = {token}       ← 所有權硬性關卡
 *   CNAME {domain} → {slug}.{base-domain}      ← 流量指向
 * verify 時以真實 DNS 查詢比對 TXT token 並確認網域可解析，通過才標記 verified，
 * HostSiteFilter 只服務 verified 的自訂網域。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteDomainService {

    /** RFC-1123 hostname：至少兩段、無 scheme/port/path、TLD 為字母 */
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private static final String VERIFY_RECORD_PREFIX = "_n3n-verify.";
    private static final String TOKEN_PREFIX = "n3n-verify-";
    private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_RANDOM_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SiteRepository siteRepository;
    private final SiteDomains siteDomains;
    private final DnsLookup dnsLookup;

    // ---------- 自訂網域管理 ----------

    @Transactional
    public Site setCustomDomain(UUID siteId, UUID ownerId, String rawDomain) {
        if (!siteDomains.isConfigured()) {
            throw new IllegalStateException(
                    "Custom domains require SITE_BASE_DOMAIN to be configured on this platform");
        }
        Site site = getOwned(siteId, ownerId);
        String domain = validateDomain(rawDomain);
        boolean sameDomain = domain.equals(site.getCustomDomain());
        if (!sameDomain && siteRepository.existsByCustomDomain(domain)) {
            throw new IllegalArgumentException("This domain is already in use: " + domain);
        }
        site.setCustomDomain(domain);
        if (!sameDomain || site.getCustomDomainToken() == null) {
            site.setCustomDomainVerified(false);
            site.setCustomDomainToken(generateToken());
        }
        Site saved = siteRepository.save(site);
        log.info("Custom domain set: site={}, domain={}", siteId, domain);
        return saved;
    }

    @Transactional
    public Site verifyCustomDomain(UUID siteId, UUID ownerId) {
        Site site = getOwned(siteId, ownerId);
        String domain = site.getCustomDomain();
        String token = site.getCustomDomainToken();
        if (domain == null || token == null) {
            throw new IllegalArgumentException("No custom domain configured for this site");
        }

        String verifyName = VERIFY_RECORD_PREFIX + domain;
        List<String> txtValues = dnsLookup.txtRecords(verifyName);
        if (txtValues.stream().noneMatch(token::equals)) {
            throw new IllegalArgumentException(
                    "TXT record not found or does not match. Expected TXT " + verifyName
                            + " = " + token + " (DNS changes can take a few minutes to propagate)");
        }
        if (!dnsLookup.resolves(domain)) {
            throw new IllegalArgumentException(
                    "Domain does not resolve yet. Create a CNAME record pointing " + domain
                            + " to " + siteDomains.subdomainHost(site.getSlug()));
        }

        site.setCustomDomainVerified(true);
        Site saved = siteRepository.save(site);
        log.info("Custom domain verified: site={}, domain={}", siteId, domain);
        return saved;
    }

    @Transactional
    public Site removeCustomDomain(UUID siteId, UUID ownerId) {
        Site site = getOwned(siteId, ownerId);
        site.setCustomDomain(null);
        site.setCustomDomainVerified(false);
        site.setCustomDomainToken(null);
        Site saved = siteRepository.save(site);
        log.info("Custom domain removed: site={}", siteId);
        return saved;
    }

    /** 使用者須建立的 DNS 記錄（TXT 驗證 + CNAME 指向） */
    public List<SiteDnsRecord> dnsRecords(Site site) {
        if (site.getCustomDomain() == null || site.getCustomDomainToken() == null) {
            return List.of();
        }
        return List.of(
                new SiteDnsRecord("TXT",
                        VERIFY_RECORD_PREFIX + site.getCustomDomain(),
                        site.getCustomDomainToken()),
                new SiteDnsRecord("CNAME",
                        site.getCustomDomain(),
                        siteDomains.subdomainHost(site.getSlug()))
        );
    }

    // ---------- Host 解析（HostSiteFilter / tls-check 用） ----------

    /**
     * 由 Host 找出應服務的站台：{slug}.{base-domain} 或已驗證的自訂網域。
     * 僅回傳已發佈站台；找不到回空（呼叫端 pass through 或 404）。
     */
    @Transactional(readOnly = true)
    public Optional<Site> resolveHost(String host) {
        if (!siteDomains.isConfigured()) {
            return Optional.empty();
        }
        String normalized = SiteDomains.normalizeHost(host);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<String> slug = siteDomains.slugFromHost(normalized);
        if (slug.isPresent()) {
            return siteRepository.findBySlug(slug.get()).filter(Site::isPublished);
        }
        if (siteDomains.isPlatformDomain(normalized)) {
            return Optional.empty();
        }
        return siteRepository.findByCustomDomainAndCustomDomainVerifiedTrue(normalized)
                .filter(Site::isPublished);
    }

    /** Caddy on_demand_tls ask：此網域是否允許簽發憑證 */
    @Transactional(readOnly = true)
    public boolean isServableHost(String host) {
        return resolveHost(host).isPresent();
    }

    // ---------- Internals ----------

    private Site getOwned(UUID siteId, UUID ownerId) {
        return siteRepository.findByIdAndOwnerId(siteId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + siteId));
    }

    /**
     * 驗證並正規化自訂網域：小寫 hostname，拒絕 scheme/path/port、
     * 平台 base domain 及其子網域。
     */
    private String validateDomain(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            throw new IllegalArgumentException("Domain is required");
        }
        String domain = SiteDomains.normalizeHost(rawDomain);
        if (domain == null || domain.contains("/") || domain.contains(":") || domain.contains("@")) {
            throw new IllegalArgumentException(
                    "Invalid domain (no scheme, port or path allowed): " + rawDomain);
        }
        if (!HOSTNAME_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid domain name: " + rawDomain);
        }
        if (siteDomains.isPlatformDomain(domain)) {
            throw new IllegalArgumentException(
                    "Domain must not be the platform domain or its subdomain: " + domain);
        }
        return domain;
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_PREFIX);
        for (int i = 0; i < TOKEN_RANDOM_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
