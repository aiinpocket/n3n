package com.aiinpocket.n3n.hostedapp.web;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.service.AppHostService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 註冊 AppProxyFilter 於 HostSiteFilter（-110）之前：
 * 同一個 wildcard base-domain 上，小應用先認領自己的 host，
 * 沒認領的請求原樣往下走（靜態站台或主應用）。
 * 功能未啟用時 filter 每請求僅一次 boolean 判斷即 pass through。
 */
@Configuration
public class AppProxyFilterConfig {

    @Bean
    public FilterRegistrationBean<AppProxyFilter> appProxyFilterRegistration(
            AppHostService appHostService,
            HostedAppProperties properties,
            AppProxyHttpClient httpClient) {
        FilterRegistrationBean<AppProxyFilter> registration = new FilterRegistrationBean<>(
                new AppProxyFilter(appHostService, properties, httpClient));
        registration.addUrlPatterns("/*");
        // HostSiteFilter 為 -110；-111 保證先於它（也先於 Security chain -100）
        registration.setOrder(-111);
        return registration;
    }
}
