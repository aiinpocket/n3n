package com.aiinpocket.n3n.site.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiteDomainsTest {

    @Nested
    @DisplayName("dot mode (SITE_BASE_DOMAIN=apps.example.com)")
    class DotMode {

        private final SiteDomains domains = new SiteDomains("apps.example.com");

        @Test
        void buildsDotSeparatedHosts() {
            assertThat(domains.isConfigured()).isTrue();
            assertThat(domains.hostSuffix()).isEqualTo(".apps.example.com");
            assertThat(domains.subdomainHost("blog")).isEqualTo("blog.apps.example.com");
            assertThat(domains.publicUrl("blog")).isEqualTo("https://blog.apps.example.com/");
        }

        @Test
        void parsesSlugFromHost() {
            assertThat(domains.slugFromHost("blog.apps.example.com")).contains("blog");
            assertThat(domains.slugFromHost("a.b.apps.example.com")).isEmpty();
            assertThat(domains.slugFromHost("apps.example.com")).isEmpty();
            assertThat(domains.slugFromHost("other.com")).isEmpty();
        }

        @Test
        void detectsPlatformDomains() {
            assertThat(domains.isPlatformDomain("apps.example.com")).isTrue();
            assertThat(domains.isPlatformDomain("x.apps.example.com")).isTrue();
            assertThat(domains.isPlatformDomain("example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("dash-suffix mode (SITE_BASE_DOMAIN=-n3n.example.com)")
    class DashMode {

        private final SiteDomains domains = new SiteDomains("-n3n.example.com");

        @Test
        void buildsDashSuffixedHosts() {
            assertThat(domains.isConfigured()).isTrue();
            assertThat(domains.baseDomain()).isEqualTo("n3n.example.com");
            assertThat(domains.hostSuffix()).isEqualTo("-n3n.example.com");
            assertThat(domains.subdomainHost("blog")).isEqualTo("blog-n3n.example.com");
            assertThat(domains.publicUrl("blog")).isEqualTo("https://blog-n3n.example.com/");
        }

        @Test
        void parsesSlugFromHost() {
            assertThat(domains.slugFromHost("blog-n3n.example.com")).contains("blog");
            assertThat(domains.slugFromHost("Blog-N3N.example.com")).contains("blog");
            // 多層或不合格式的 host 一律拒絕
            assertThat(domains.slugFromHost("a.blog-n3n.example.com")).isEmpty();
            assertThat(domains.slugFromHost("-n3n.example.com")).isEmpty();
            assertThat(domains.slugFromHost("n3n.example.com")).isEmpty();
            assertThat(domains.slugFromHost("other.com")).isEmpty();
        }

        @Test
        void detectsPlatformDomains() {
            // 主網域與其子網域、以及任何 {slug}-n3n 主機都是平台網域
            assertThat(domains.isPlatformDomain("n3n.example.com")).isTrue();
            assertThat(domains.isPlatformDomain("blog-n3n.example.com")).isTrue();
            assertThat(domains.isPlatformDomain("x.n3n.example.com")).isTrue();
            assertThat(domains.isPlatformDomain("example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("dormant when unset")
    class Dormant {

        private final SiteDomains domains = new SiteDomains("");

        @Test
        void fallsBackToPathUrls() {
            assertThat(domains.isConfigured()).isFalse();
            assertThat(domains.publicUrl("blog")).isEqualTo("/sites/blog/");
            assertThat(domains.slugFromHost("blog.example.com")).isEmpty();
            assertThat(domains.isPlatformDomain("example.com")).isFalse();
        }
    }
}
