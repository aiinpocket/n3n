package com.aiinpocket.n3n.hostedapp.web;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.service.AppHostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppProxyFilter：host 命中即代理（mock HTTP 接縫）、未命中 pass through、
 * 非執行中應用回 502 友善頁、Upgrade 回 501、hop-by-hop headers 剝除。
 */
@ExtendWith(MockitoExtension.class)
class AppProxyFilterTest {

    private static final String HOST = "my-app-ab12.apps.example.com";
    private static final String SLUG = "my-app-ab12";

    @Mock
    private AppHostService appHostService;

    @Mock
    private HostedAppProperties properties;

    @Mock
    private AppProxyHttpClient httpClient;

    private AppProxyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AppProxyFilter(appHostService, properties, httpClient);
        lenient().when(appHostService.isActive()).thenReturn(true);
        lenient().when(properties.getProxyTarget())
                .thenReturn(HostedAppProperties.PROXY_TARGET_CONTAINER);
    }

    private HostedApp runningApp() {
        return HostedApp.builder()
                .id(UUID.randomUUID()).ownerId(UUID.randomUUID())
                .name("My App").slug(SLUG)
                .appType("compose").status(AppStatus.RUNNING)
                .manifest(Map.of("webService", "web", "internalPort", 80))
                .hostPort(28001).internalPort(80)
                .build();
    }

    private MockHttpServletRequest requestFor(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServerName(HOST);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    /** 極簡 HttpResponse stub（僅測試用） */
    private static HttpResponse<InputStream> upstreamResponse(
            int status, Map<String, List<String>> headers, String body) {
        HttpHeaders httpHeaders = HttpHeaders.of(headers, (a, b) -> true);
        InputStream stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }
            @Override public HttpHeaders headers() { return httpHeaders; }
            @Override public InputStream body() { return stream; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://upstream/"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    @Test
    @DisplayName("host 命中執行中應用：代理到容器名:internalPort，回應原樣轉回")
    void proxiesToContainerTarget() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any())).thenReturn(upstreamResponse(
                200, Map.of("Content-Type", List.of("text/html"),
                        "X-App-Header", List.of("hello")), "<html>app</html>"));

        MockHttpServletRequest request = requestFor("GET", "/dashboard");
        request.setQueryString("tab=1");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).exchange(sent.capture());
        assertThat(sent.getValue().uri().toString())
                .isEqualTo("http://n3napp-" + SLUG + "-web:80/dashboard?tab=1");
        assertThat(sent.getValue().headers().firstValue("X-Forwarded-Host")).contains(HOST);
        assertThat(sent.getValue().headers().firstValue("X-Forwarded-For")).contains("203.0.113.7");
        assertThat(sent.getValue().headers().firstValue("Accept")).contains("text/html");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("app");
        assertThat(response.getHeader("X-App-Header")).isEqualTo("hello");
        assertThat(chain.getRequest()).isNull(); // 不落入後續 chain
    }

    @Test
    @DisplayName("host-port 模式：代理到 127.0.0.1:{hostPort}")
    void proxiesToHostPortTarget() throws Exception {
        when(properties.getProxyTarget())
                .thenReturn(HostedAppProperties.PROXY_TARGET_HOST_PORT);
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any()))
                .thenReturn(upstreamResponse(200, Map.of(), "ok"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor("GET", "/"), response, new MockFilterChain());

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).exchange(sent.capture());
        assertThat(sent.getValue().uri().toString()).isEqualTo("http://127.0.0.1:28001/");
    }

    @Test
    @DisplayName("非小應用 host：原封不動 pass through")
    void nonMatchingHostPassesThrough() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("GET", "/"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(httpClient, never()).exchange(any());
    }

    @Test
    @DisplayName("功能休眠（apps 關閉或 base-domain 未設）：完全 pass through")
    void dormantWhenInactive() throws Exception {
        when(appHostService.isActive()).thenReturn(false);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("GET", "/"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(appHostService, never()).resolveApp(any());
    }

    @Test
    @DisplayName("應用不在執行中（stopped）：502 溫暖友善頁")
    void stoppedAppReturns502Page() throws Exception {
        HostedApp stopped = runningApp();
        stopped.setStatus(AppStatus.STOPPED);
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(stopped));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(requestFor("GET", "/"), response, chain);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentType()).contains("text/html");
        assertThat(response.getContentAsString()).contains("暫時休息中");
        assertThat(chain.getRequest()).isNull();
        verify(httpClient, never()).exchange(any());
    }

    @Test
    @DisplayName("連線失敗（IOException）：502 友善頁")
    void connectFailureReturns502Page() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any())).thenThrow(new IOException("connection refused"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor("GET", "/"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).contains("暫時休息中");
    }

    @Test
    @DisplayName("Upgrade（WebSocket）請求：501 與明確訊息")
    void upgradeRequestReturns501() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));

        MockHttpServletRequest request = requestFor("GET", "/ws");
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Connection", "Upgrade");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(501);
        assertThat(response.getContentAsString()).contains("not supported");
        verify(httpClient, never()).exchange(any());
    }

    @Test
    @DisplayName("hop-by-hop 與 Connection 點名的 headers 不轉送；一般 header 保留")
    void hopByHopHeadersStripped() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any()))
                .thenReturn(upstreamResponse(200, Map.of(), "ok"));

        MockHttpServletRequest request = requestFor("GET", "/");
        request.addHeader("Connection", "keep-alive, X-Custom-Hop");
        request.addHeader("Keep-Alive", "timeout=5");
        request.addHeader("Proxy-Authorization", "Basic xxx");
        request.addHeader("Transfer-Encoding", "chunked");
        request.addHeader("X-Custom-Hop", "should-be-dropped");
        request.addHeader("X-Request-Id", "abc-123");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).exchange(sent.capture());
        HttpHeaders headers = sent.getValue().headers();
        assertThat(headers.firstValue("Connection")).isEmpty();
        assertThat(headers.firstValue("Keep-Alive")).isEmpty();
        assertThat(headers.firstValue("Proxy-Authorization")).isEmpty();
        assertThat(headers.firstValue("Transfer-Encoding")).isEmpty();
        assertThat(headers.firstValue("X-Custom-Hop")).isEmpty();
        assertThat(headers.firstValue("X-Request-Id")).contains("abc-123");
    }

    @Test
    @DisplayName("上游回應的 hop-by-hop headers 也剝除；應用自身 headers 原樣通過")
    void upstreamHopByHopStripped() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any())).thenReturn(upstreamResponse(200, Map.of(
                "Connection", List.of("keep-alive"),
                "Transfer-Encoding", List.of("chunked"),
                "Set-Cookie", List.of("session=1; Path=/"),
                "Content-Security-Policy", List.of("default-src 'self'")), "ok"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFor("GET", "/"), response, new MockFilterChain());

        assertThat(response.getHeader("Connection")).isNull();
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getHeader("Set-Cookie")).isEqualTo("session=1; Path=/");
        // 不注入、不覆寫：應用自己的安全 header 原樣通過
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
    }

    @Test
    @DisplayName("POST body 轉送且方法保留")
    void postBodyForwarded() throws Exception {
        when(appHostService.resolveApp(HOST)).thenReturn(Optional.of(runningApp()));
        when(httpClient.exchange(any()))
                .thenReturn(upstreamResponse(201, Map.of(), "created"));

        MockHttpServletRequest request = requestFor("POST", "/api/items");
        request.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Content-Type", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).exchange(sent.capture());
        assertThat(sent.getValue().method()).isEqualTo("POST");
        assertThat(sent.getValue().bodyPublisher()).isPresent();
        assertThat(response.getStatus()).isEqualTo(201);
    }
}
