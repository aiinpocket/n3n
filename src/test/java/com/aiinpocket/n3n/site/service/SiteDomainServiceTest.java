package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.site.dto.SiteDnsRecord;
import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自訂網域：hostname 驗證、token 產生、DNS 驗證（stub DnsLookup seam）、
 * DNS 記錄 payload 形狀、Host 解析。
 */
class SiteDomainServiceTest extends BaseServiceTest {

    private static final String BASE_DOMAIN = "sites.example.com";

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private DnsLookup dnsLookup;

    private SiteDomainService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SiteDomainService(siteRepository, new SiteDomains(BASE_DOMAIN), dnsLookup);
    }

    private Site ownedSite() {
        return Site.builder()
                .id(siteId)
                .ownerId(ownerId)
                .slug("my-site-ab12")
                .name("My Site")
                .isPublished(true)
                .build();
    }

    private void stubOwnedSite(Site site) {
        when(siteRepository.findByIdAndOwnerId(siteId, ownerId)).thenReturn(Optional.of(site));
        lenient().when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- setCustomDomain ----------

    @Test
    @DisplayName("設定自訂網域：小寫正規化、產生 token、未驗證狀態")
    void setCustomDomainNormalizesAndGeneratesToken() {
        stubOwnedSite(ownedSite());
        when(siteRepository.existsByCustomDomain("www.example.org")).thenReturn(false);

        Site site = service.setCustomDomain(siteId, ownerId, "WWW.Example.ORG");

        assertThat(site.getCustomDomain()).isEqualTo("www.example.org");
        assertThat(site.isCustomDomainVerified()).isFalse();
        assertThat(site.getCustomDomainToken()).matches("^n3n-verify-[a-z0-9]{16}$");
    }

    @Test
    @DisplayName("非法網域一律拒絕（scheme/port/path/底線/平台網域）")
    void invalidDomainsRejected() {
        stubOwnedSite(ownedSite());
        List<String> invalid = List.of(
                "http://example.org",
                "example.org/path",
                "example.org:8080",
                "no-dots",
                "bad_domain.example.org",
                "-leading.example.org",
                "user@example.org",
                BASE_DOMAIN,
                "anything." + BASE_DOMAIN
        );
        for (String domain : invalid) {
            assertThatThrownBy(() -> service.setCustomDomain(siteId, ownerId, domain))
                    .as("domain: " + domain)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("已被其他站台使用的網域拒絕")
    void duplicateDomainRejected() {
        stubOwnedSite(ownedSite());
        when(siteRepository.existsByCustomDomain("taken.example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.setCustomDomain(siteId, ownerId, "taken.example.org"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("base-domain 未設定時自訂網域功能不可用")
    void requiresBaseDomainConfigured() {
        SiteDomainService dormant = new SiteDomainService(
                siteRepository, new SiteDomains(""), dnsLookup);

        assertThatThrownBy(() -> dormant.setCustomDomain(siteId, ownerId, "www.example.org"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SITE_BASE_DOMAIN");
    }

    @Test
    @DisplayName("非擁有者操作回 404")
    void ownerCheckFailsClosed() {
        UUID stranger = UUID.randomUUID();
        when(siteRepository.findByIdAndOwnerId(siteId, stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCustomDomain(siteId, stranger, "www.example.org"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- DNS 記錄 payload ----------

    @Test
    @DisplayName("DNS 記錄：TXT 驗證記錄 + CNAME 指向 {slug}.{base-domain}")
    void dnsRecordsShape() {
        stubOwnedSite(ownedSite());
        when(siteRepository.existsByCustomDomain(anyString())).thenReturn(false);
        Site site = service.setCustomDomain(siteId, ownerId, "www.example.org");

        List<SiteDnsRecord> records = service.dnsRecords(site);

        assertThat(records).hasSize(2);
        SiteDnsRecord txt = records.get(0);
        assertThat(txt.type()).isEqualTo("TXT");
        assertThat(txt.host()).isEqualTo("_n3n-verify.www.example.org");
        assertThat(txt.value()).isEqualTo(site.getCustomDomainToken());
        SiteDnsRecord cname = records.get(1);
        assertThat(cname.type()).isEqualTo("CNAME");
        assertThat(cname.host()).isEqualTo("www.example.org");
        assertThat(cname.value()).isEqualTo("my-site-ab12." + BASE_DOMAIN);
    }

    @Test
    @DisplayName("未設定網域時 DNS 記錄為空")
    void dnsRecordsEmptyWithoutDomain() {
        assertThat(service.dnsRecords(ownedSite())).isEmpty();
    }

    // ---------- verifyCustomDomain ----------

    private Site siteWithDomain() {
        Site site = ownedSite();
        site.setCustomDomain("www.example.org");
        site.setCustomDomainToken("n3n-verify-abcd1234efgh5678");
        return site;
    }

    @Test
    @DisplayName("驗證成功：TXT token 相符且網域可解析")
    void verifySucceedsWhenTxtMatchesAndResolves() {
        Site site = siteWithDomain();
        stubOwnedSite(site);
        when(dnsLookup.txtRecords("_n3n-verify.www.example.org"))
                .thenReturn(List.of("other-value", "n3n-verify-abcd1234efgh5678"));
        when(dnsLookup.resolves("www.example.org")).thenReturn(true);

        Site verified = service.verifyCustomDomain(siteId, ownerId);

        assertThat(verified.isCustomDomainVerified()).isTrue();
    }

    @Test
    @DisplayName("TXT 記錄缺失或不符 → 驗證失敗且不標記")
    void verifyFailsWhenTxtMissing() {
        Site site = siteWithDomain();
        stubOwnedSite(site);
        when(dnsLookup.txtRecords("_n3n-verify.www.example.org")).thenReturn(List.of());

        assertThatThrownBy(() -> service.verifyCustomDomain(siteId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TXT");
        assertThat(site.isCustomDomainVerified()).isFalse();
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("TXT 相符但網域未指向平台 → 驗證失敗")
    void verifyFailsWhenDomainDoesNotResolve() {
        Site site = siteWithDomain();
        stubOwnedSite(site);
        when(dnsLookup.txtRecords("_n3n-verify.www.example.org"))
                .thenReturn(List.of("n3n-verify-abcd1234efgh5678"));
        when(dnsLookup.resolves("www.example.org")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyCustomDomain(siteId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNAME");
        assertThat(site.isCustomDomainVerified()).isFalse();
    }

    @Test
    @DisplayName("未設定網域時驗證回明確錯誤")
    void verifyWithoutDomainRejected() {
        stubOwnedSite(ownedSite());

        assertThatThrownBy(() -> service.verifyCustomDomain(siteId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No custom domain");
    }

    // ---------- removeCustomDomain ----------

    @Test
    @DisplayName("移除網域：清空 domain/verified/token")
    void removeClearsAllDomainFields() {
        Site site = siteWithDomain();
        site.setCustomDomainVerified(true);
        stubOwnedSite(site);

        Site removed = service.removeCustomDomain(siteId, ownerId);

        assertThat(removed.getCustomDomain()).isNull();
        assertThat(removed.isCustomDomainVerified()).isFalse();
        assertThat(removed.getCustomDomainToken()).isNull();
    }

    // ---------- resolveHost ----------

    @Test
    @DisplayName("Host 解析：子網域對應已發佈站台")
    void resolveHostBySubdomain() {
        Site site = ownedSite();
        when(siteRepository.findBySlug("my-site-ab12")).thenReturn(Optional.of(site));

        assertThat(service.resolveHost("my-site-ab12." + BASE_DOMAIN)).contains(site);
    }

    @Test
    @DisplayName("Host 解析：未發佈站台不對外")
    void resolveHostFiltersUnpublished() {
        Site site = ownedSite();
        site.setPublished(false);
        when(siteRepository.findBySlug("my-site-ab12")).thenReturn(Optional.of(site));

        assertThat(service.resolveHost("my-site-ab12." + BASE_DOMAIN)).isEmpty();
    }

    @Test
    @DisplayName("Host 解析：已驗證自訂網域命中，其他 host 回空")
    void resolveHostByVerifiedCustomDomain() {
        Site site = siteWithDomain();
        site.setCustomDomainVerified(true);
        when(siteRepository.findByCustomDomainAndCustomDomainVerifiedTrue("www.example.org"))
                .thenReturn(Optional.of(site));
        when(siteRepository.findByCustomDomainAndCustomDomainVerifiedTrue("other.example.org"))
                .thenReturn(Optional.empty());

        assertThat(service.resolveHost("www.example.org")).contains(site);
        assertThat(service.resolveHost("other.example.org")).isEmpty();
        assertThat(service.isServableHost("www.example.org")).isTrue();
        assertThat(service.isServableHost("other.example.org")).isFalse();
    }

    @Test
    @DisplayName("多層子網域（a.b.{base}）不視為站台 host")
    void nestedSubdomainNotResolved() {
        assertThat(service.resolveHost("a.b." + BASE_DOMAIN)).isEmpty();
        verify(siteRepository, never()).findBySlug(anyString());
    }
}
