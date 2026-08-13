package com.aiinpocket.n3n.site.controller;

import com.aiinpocket.n3n.site.entity.SiteFile;
import com.aiinpocket.n3n.site.service.SiteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公開網站託管的單元測試：隔離 header（CSP sandbox）、
 * default document、SPA fallback、路徑穿越防護。
 */
@ExtendWith(MockitoExtension.class)
class PublicSiteControllerTest {

    @Mock
    private SiteService siteService;

    @InjectMocks
    private PublicSiteController controller;

    private static final String SLUG = "my-site-ab12";

    private MockHttpServletRequest requestFor(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return request;
    }

    private SiteFile htmlFile(String path, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        return SiteFile.builder()
                .id(UUID.randomUUID()).siteId(UUID.randomUUID())
                .path(path).contentType("text/html; charset=utf-8")
                .data(data).sizeBytes(data.length)
                .build();
    }

    @Test
    @DisplayName("每個回應都帶 CSP sandbox 與隔離 header")
    void securityHeadersOnEveryResponse() {
        when(siteService.findPublicFile(SLUG, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html></html>")));

        ResponseEntity<byte[]> response = controller.serve(SLUG, requestFor("/sites/" + SLUG + "/"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
                .contains("sandbox")
                .contains("allow-scripts");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("public, max-age=60");
    }

    @Test
    @DisplayName("404 回應也帶隔離 header")
    void securityHeadersOn404() {
        when(siteService.findPublicFile(anyString(), anyString())).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = controller.serve(SLUG,
                requestFor("/sites/" + SLUG + "/missing.html"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
    }

    @Test
    @DisplayName("根路徑提供 index.html")
    void rootServesIndexHtml() {
        when(siteService.findPublicFile(SLUG, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html>hi</html>")));

        ResponseEntity<byte[]> response = controller.serve(SLUG, requestFor("/sites/" + SLUG + "/"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("hi");
    }

    @Test
    @DisplayName("無副檔名路徑（SPA 路由）fallback 到 index.html")
    void spaFallbackForExtensionlessPaths() {
        when(siteService.findPublicFile(SLUG, "about")).thenReturn(Optional.empty());
        when(siteService.findPublicFile(SLUG, "index.html"))
                .thenReturn(Optional.of(htmlFile("index.html", "<html>spa</html>")));

        ResponseEntity<byte[]> response = controller.serve(SLUG,
                requestFor("/sites/" + SLUG + "/about"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).contains("spa");
    }

    @Test
    @DisplayName("路徑穿越回 404 且不查詢服務")
    void pathTraversalReturns404() {
        for (String uri : new String[]{
                "/sites/" + SLUG + "/../secret.html",
                "/sites/" + SLUG + "/a/../../x.html",
                "/sites/" + SLUG + "/..",
        }) {
            ResponseEntity<byte[]> response = controller.serve(SLUG, requestFor(uri));
            assertThat(response.getStatusCode()).as("uri: " + uri).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verify(siteService, never()).findPublicFile(anyString(), anyString());
    }

    @Test
    @DisplayName("非法 slug 回 404")
    void invalidSlugReturns404() {
        ResponseEntity<byte[]> response = controller.serve("Bad_Slug",
                requestFor("/sites/Bad_Slug/index.html"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(siteService, never()).findPublicFile(anyString(), anyString());
    }

    @Test
    @DisplayName("/sites/{slug} 301 導向 /sites/{slug}/")
    void redirectsToTrailingSlash() {
        ResponseEntity<Void> response = controller.redirectToRoot(SLUG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(response.getHeaders().getLocation()).hasPath("/sites/" + SLUG + "/");
    }
}
