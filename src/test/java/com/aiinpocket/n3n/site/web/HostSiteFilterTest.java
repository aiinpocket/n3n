package com.aiinpocket.n3n.site.web;

import com.aiinpocket.n3n.site.entity.Site;
import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.service.SiteDomainService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Host 路由的單元測試：子網域命中、主應用 pass through、
 * 未知 slug 404、自訂網域（已驗證/未驗證）、功能休眠。
 */
@ExtendWith(MockitoExtension.class)
class HostSiteFilterTest {

    private static final String BASE_DOMAIN = "sites.example.com";
    private static final String SLUG = "my-site-ab12";

    @Mock
    private SiteDomainService siteDomainService;

    @Mock
    private SiteService siteService;

    private HostSiteFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HostSiteFilter(new SiteDomains(BASE_DOMAIN), siteDomainService, siteService);
    }

    private Site publishedSite() {
        return Site.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .slug(SLUG)
                .name("My Site")
                .isPublished(true)
                .build();
    }

    private SiteFile htmlFile(String path, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        return SiteFile.builder()
                .id(UUID.randomUUID()).siteId(UUID.randomUUID())
                .path(path).contentType("text/html; charset=utf-8")
                .data(data).sizeBytes(data.length)
                .build();
    }

    private MockHttpServletRequest requestFor(String host, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServerName(host);
        return request;
    }

    @Test
    @DisplayName("子網域命中：以根路徑服務站台檔案並帶隔離 header")
    void subdomainServesSiteAtRoot() throws Exception {
        Site site = publishedSite();
        when(siteDomainService.resolveHost(SLUG + "." + BASE_DOMAIN)).thenReturn(Optional.of(site));
        when(siteService.findPublicFile(site, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html>hi</html>")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor(SLUG + "." + BASE_DOMAIN, "/"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("hi");
        assertThat(response.getHeader("Content-Security-Policy")).contains("sandbox");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(chain.getRequest()).isNull(); // 未進入後續 chain（Security 看不到）
    }

    @Test
    @DisplayName("子網域上的任意路徑（/style.css）也直接服務")
    void subdomainServesNestedAsset() throws Exception {
        Site site = publishedSite();
        when(siteDomainService.resolveHost(SLUG + "." + BASE_DOMAIN)).thenReturn(Optional.of(site));
        when(siteService.findPublicFile(site, "style.css"))
                .thenReturn(Optional.of(htmlFile("style.css", "body{}")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor(SLUG + "." + BASE_DOMAIN, "/style.css"),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("body{}");
    }

    @Test
    @DisplayName("主應用 host 不解析為站台 → pass through，回應不被碰")
    void mainHostPassesThrough() throws Exception {
        when(siteDomainService.resolveHost("n3n.example.com")).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("n3n.example.com", "/api/flows"), response, chain);

        assertThat(chain.getRequest()).isNotNull(); // 有走到 chain
        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }

    @Test
    @DisplayName("base domain 本身（apex）pass through 且不查 DB")
    void apexDomainPassesThroughWithoutLookup() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor(BASE_DOMAIN, "/"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(siteDomainService, never()).resolveHost(anyString());
    }

    @Test
    @DisplayName("wildcard 網域下未知/未發佈 slug → 404，不落到主應用")
    void unknownSlugOnWildcardDomainReturns404() throws Exception {
        when(siteDomainService.resolveHost("nope-zz99." + BASE_DOMAIN)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("nope-zz99." + BASE_DOMAIN, "/"), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("Content-Security-Policy")).contains("sandbox");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("已驗證自訂網域命中站台")
    void verifiedCustomDomainServesSite() throws Exception {
        Site site = publishedSite();
        site.setCustomDomain("www.customer.com");
        site.setCustomDomainVerified(true);
        when(siteDomainService.resolveHost("www.customer.com")).thenReturn(Optional.of(site));
        when(siteService.findPublicFile(site, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html>custom</html>")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor("www.customer.com", "/"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("custom");
    }

    @Test
    @DisplayName("未驗證自訂網域不解析 → pass through")
    void unverifiedCustomDomainPassesThrough() throws Exception {
        // SiteDomainService 只回傳已驗證網域，未驗證時 resolveHost 為空
        when(siteDomainService.resolveHost("unverified.customer.com")).thenReturn(Optional.empty());

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("unverified.customer.com", "/"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("base-domain 未設定時完全休眠")
    void dormantWhenBaseDomainNotConfigured() throws Exception {
        HostSiteFilter dormant = new HostSiteFilter(new SiteDomains(""), siteDomainService, siteService);

        MockFilterChain chain = new MockFilterChain();
        dormant.doFilter(requestFor(SLUG + "." + BASE_DOMAIN, "/"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(siteDomainService, never()).resolveHost(anyString());
    }

    @Test
    @DisplayName("站台 host 上的非 GET/HEAD 請求 → 404（不暴露平台 API）")
    void nonGetOnSiteHostReturns404() throws Exception {
        Site site = publishedSite();
        when(siteDomainService.resolveHost(SLUG + "." + BASE_DOMAIN)).thenReturn(Optional.of(site));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServerName(SLUG + "." + BASE_DOMAIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
        verify(siteService, never()).findPublicFile(any(Site.class), anyString());
    }

    @Test
    @DisplayName("路徑穿越在站台 host 上回 404")
    void pathTraversalOnSiteHostReturns404() throws Exception {
        Site site = publishedSite();
        when(siteDomainService.resolveHost(SLUG + "." + BASE_DOMAIN)).thenReturn(Optional.of(site));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor(SLUG + "." + BASE_DOMAIN, "/../secret.html"),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        verify(siteService, never()).findPublicFile(any(Site.class), anyString());
    }

    @Test
    @DisplayName("SPA fallback：無副檔名路徑退回 index.html")
    void spaFallbackOnSubdomain() throws Exception {
        Site site = publishedSite();
        when(siteDomainService.resolveHost(SLUG + "." + BASE_DOMAIN)).thenReturn(Optional.of(site));
        when(siteService.findPublicFile(site, "about")).thenReturn(Optional.empty());
        when(siteService.findPublicFile(site, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html>spa</html>")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor(SLUG + "." + BASE_DOMAIN, "/about"),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("spa");
    }
}
