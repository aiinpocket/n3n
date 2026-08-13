package com.aiinpocket.n3n.hostedapp.web;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AppProxyHttpClient 的 JDK HttpClient 實作。
 *
 * 固定 HTTP/1.1（上游是容器內的一般 web server，避免 h2c 升級干擾）、
 * 不跟隨轉址（3xx 原樣回給瀏覽器，Location 由應用自己決定）。
 */
@Component
public class JdkAppProxyHttpClient implements AppProxyHttpClient {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public HttpResponse<InputStream> exchange(HttpRequest request)
            throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }
}
