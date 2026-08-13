package com.aiinpocket.n3n.hostedapp.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AppProxyFilter 的 HTTP 呼叫接縫：實際轉送由 JdkAppProxyHttpClient
 * 執行，測試以 mock 取代（不需真的容器）。
 */
public interface AppProxyHttpClient {

    /** 送出請求並以串流方式回傳 body（呼叫端負責關閉 InputStream） */
    HttpResponse<InputStream> exchange(HttpRequest request) throws IOException, InterruptedException;
}
