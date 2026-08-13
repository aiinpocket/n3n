package com.aiinpocket.n3n.hostedapp.web;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.service.AppDeployService;
import com.aiinpocket.n3n.hostedapp.service.AppHostService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 小應用子網域反向代理：Host 為 {slug}.{base-domain} 且對應到 HostedApp
 * 時，把整個請求串流轉送到該應用的 web 容器。
 *
 * 順序 -111，剛好在 HostSiteFilter（-110）之前——同一個 wildcard 網域
 * 先給小應用認領，沒認領的 host 原封不動往下走（靜態站台或主應用）。
 *
 * 轉送原則：
 *   - 逐一複製請求 / 回應 header，僅剝除 hop-by-hop（RFC 7230 §6.1）
 *     與 JDK HttpClient 的受限 header；不注入任何安全 header——
 *     應用在自己的子網域 origin 上，該設什麼 header 由它自己決定
 *   - 補上 X-Forwarded-For / X-Forwarded-Proto / X-Forwarded-Host
 *   - 請求與回應 body 皆為串流（不落地緩衝），逾時 60 秒
 *   - WebSocket / Upgrade 請求 v1 不支援，回 501 與明確訊息
 *   - 連線失敗或應用不在執行中 → 502 溫暖友善頁
 */
@RequiredArgsConstructor
@Slf4j
public class AppProxyFilter extends OncePerRequestFilter {

    private static final Duration PROXY_TIMEOUT = Duration.ofSeconds(60);

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD");

    /** hop-by-hop headers（RFC 7230 §6.1）：不得跨代理轉送 */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "trailers", "transfer-encoding", "upgrade");

    /** JDK HttpClient 禁止手動設定、或由我們統一改寫的請求 header */
    private static final Set<String> REQUEST_SKIP = Set.of(
            "host", "content-length", "expect",
            "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host");

    private final AppHostService appHostService;
    private final HostedAppProperties properties;
    private final AppProxyHttpClient httpClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!appHostService.isActive()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<HostedApp> app = appHostService.resolveApp(request.getServerName());
        if (app.isEmpty()) {
            // 不是小應用的 host：交給後面的 HostSiteFilter / 主應用
            chain.doFilter(request, response);
            return;
        }
        handle(app.get(), request, response);
    }

    // ---------- Request handling ----------

    private void handle(HostedApp app, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!AppStatus.RUNNING.equals(app.getStatus())) {
            writeUnavailable(response, app.getName());
            return;
        }
        if (isUpgradeRequest(request)) {
            writePlain(response, 501,
                    "WebSocket / protocol upgrade is not supported for hosted apps yet.");
            return;
        }
        String method = request.getMethod();
        if (!ALLOWED_METHODS.contains(method)) {
            writePlain(response, 405, "Method not allowed through the app proxy: " + method);
            return;
        }

        String target;
        try {
            target = resolveTarget(app);
        } catch (IllegalStateException e) {
            log.warn("App proxy target unresolvable: slug={}, reason={}", app.getSlug(), e.getMessage());
            writeUnavailable(response, app.getName());
            return;
        }

        try {
            HttpResponse<InputStream> upstream = httpClient.exchange(
                    buildRequest(target, method, request));
            copyResponse(upstream, response);
        } catch (IOException e) {
            log.warn("App proxy connect failed: slug={}, target={}, error={}",
                    app.getSlug(), target, e.toString());
            writeUnavailable(response, app.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeUnavailable(response, app.getName());
        }
    }

    /**
     * 代理目標位址：
     *   container —— {n3napp-slug-webService}:{internalPort}（共用 n3n-apps network 的容器 DNS）
     *   host-port —— 127.0.0.1:{hostPort}（平台跑在主機上時）
     */
    private String resolveTarget(HostedApp app) {
        if (HostedAppProperties.PROXY_TARGET_HOST_PORT.equals(properties.getProxyTarget())) {
            if (app.getHostPort() == null) {
                throw new IllegalStateException("app has no hostPort allocated");
            }
            return "127.0.0.1:" + app.getHostPort();
        }
        Map<String, Object> manifest = app.getManifest();
        Object webService = manifest == null ? null : manifest.get("webService");
        if (!(webService instanceof String service) || service.isBlank()) {
            throw new IllegalStateException("manifest has no webService");
        }
        if (app.getInternalPort() == null) {
            throw new IllegalStateException("app has no internalPort");
        }
        return AppDeployService.containerName(app.getSlug(), service) + ":" + app.getInternalPort();
    }

    private HttpRequest buildRequest(String target, String method, HttpServletRequest request)
            throws IOException {
        StringBuilder uri = new StringBuilder("http://").append(target).append(request.getRequestURI());
        if (request.getQueryString() != null) {
            uri.append('?').append(request.getQueryString());
        }

        HttpRequest.BodyPublisher body = hasBody(method)
                ? HttpRequest.BodyPublishers.ofInputStream(inputStreamSupplier(request))
                : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri.toString()))
                .timeout(PROXY_TIMEOUT)
                .method(method, body);

        copyRequestHeaders(request, builder);
        builder.setHeader("X-Forwarded-For", forwardedFor(request));
        builder.setHeader("X-Forwarded-Proto", forwardedProto(request));
        builder.setHeader("X-Forwarded-Host", request.getServerName());
        return builder.build();
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Set<String> connectionListed = connectionListedHeaders(request);
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower) || REQUEST_SKIP.contains(lower)
                    || connectionListed.contains(lower)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                try {
                    builder.header(name, values.nextElement());
                } catch (IllegalArgumentException e) {
                    // JDK HttpClient 拒絕的受限 header：靜默略過（等同剝除）
                }
            }
        }
    }

    private void copyResponse(HttpResponse<InputStream> upstream, HttpServletResponse response)
            throws IOException {
        response.setStatus(upstream.statusCode());
        upstream.headers().map().forEach((name, values) -> {
            if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                response.addHeader(name, value);
            }
        });
        try (InputStream body = upstream.body()) {
            body.transferTo(response.getOutputStream());
        }
    }

    // ---------- Helpers ----------

    private static boolean hasBody(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method);
    }

    private static java.util.function.Supplier<InputStream> inputStreamSupplier(
            HttpServletRequest request) {
        return () -> {
            try {
                return request.getInputStream();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("讀取請求 body 失敗", e);
            }
        };
    }

    private static boolean isUpgradeRequest(HttpServletRequest request) {
        return request.getHeader("Upgrade") != null;
    }

    /** Connection header 點名的額外 hop-by-hop headers */
    private static Set<String> connectionListedHeaders(HttpServletRequest request) {
        String connection = request.getHeader("Connection");
        if (connection == null || connection.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(connection.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String forwardedFor(HttpServletRequest request) {
        String existing = request.getHeader("X-Forwarded-For");
        String client = request.getRemoteAddr();
        return existing == null || existing.isBlank() ? client : existing + ", " + client;
    }

    private static String forwardedProto(HttpServletRequest request) {
        String existing = request.getHeader("X-Forwarded-Proto");
        return existing == null || existing.isBlank() ? request.getScheme() : existing;
    }

    // ---------- Error pages ----------

    /** 502：連不上或應用不在執行中——溫暖的歇業頁 */
    private static void writeUnavailable(HttpServletResponse response, String appName)
            throws IOException {
        String safeName = appName == null ? "" : appName
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String html = """
                <!doctype html>
                <html lang="zh-Hant">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>暫時休息中</title>
                <style>body{font-family:system-ui,-apple-system,"Noto Sans TC",sans-serif;background:#faf6ef;color:#4a4238;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}main{max-width:26rem;padding:2rem;text-align:center}h1{font-size:1.4rem;font-weight:600;letter-spacing:.05em}p{line-height:1.9;color:#7a705f}</style>
                </head>
                <body><main>
                <h1>暫時休息中</h1>
                <p>「%s」現在不在線上。<br>它可能正在部署、或被主人暫時收起來了，<br>沏杯茶，晚點再來看看吧。</p>
                </main></body></html>
                """.formatted(safeName);
        response.reset();
        response.setStatus(502);
        response.setContentType("text/html; charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
    }

    private static void writePlain(HttpServletResponse response, int status, String message)
            throws IOException {
        response.reset();
        response.setStatus(status);
        response.setContentType("text/plain; charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
    }
}
