package com.aiinpocket.n3n.site.web;

import com.aiinpocket.n3n.site.service.SiteDomainService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import com.aiinpocket.n3n.site.service.SiteService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 註冊 HostSiteFilter 於 Spring Security filter chain 之前：
 * 站台網域的請求路徑任意（/about.html …），不能進到需要認證的路由；
 * filter 完整處理命中請求（不 chain），Security 根本不會看到它們。
 */
@Configuration
public class HostSiteFilterConfig {

    @Bean
    public FilterRegistrationBean<HostSiteFilter> hostSiteFilterRegistration(
            SiteDomains siteDomains,
            SiteDomainService siteDomainService,
            SiteService siteService) {
        FilterRegistrationBean<HostSiteFilter> registration = new FilterRegistrationBean<>(
                new HostSiteFilter(siteDomains, siteDomainService, siteService));
        registration.addUrlPatterns("/*");
        // Spring Security filter chain 的預設 order 為 -100；-110 保證先於它執行
        registration.setOrder(-110);
        return registration;
    }
}
