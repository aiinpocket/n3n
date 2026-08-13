package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.site.service.SiteDomains;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Host → HostedApp 解析：{slug}.{base-domain} 對應到小應用。
 *
 * 供 AppProxyFilter（反向代理路由）與 SiteTlsCheckController
 * （Caddy on-demand TLS 簽發守門）共用。功能未啟用或
 * SITE_BASE_DOMAIN 未設定時一律回空（完全休眠）。
 */
@Service
@RequiredArgsConstructor
public class AppHostService {

    private final HostedAppProperties properties;
    private final SiteDomains siteDomains;
    private final HostedAppRepository repository;

    /** 子網域代理是否啟用（apps 功能開啟且 base-domain 已設定） */
    public boolean isActive() {
        return properties.isEnabled() && siteDomains.isConfigured();
    }

    /** 由 Host 解析小應用（不限狀態；找不到或功能休眠時回空） */
    @Transactional(readOnly = true)
    public Optional<HostedApp> resolveApp(String host) {
        if (!isActive()) {
            return Optional.empty();
        }
        String normalized = SiteDomains.normalizeHost(host);
        if (normalized == null) {
            return Optional.empty();
        }
        return siteDomains.slugFromHost(normalized).flatMap(repository::findBySlug);
    }

    /** 執行中的小應用（TLS 簽發守門用：只有執行中的應用值得簽憑證） */
    @Transactional(readOnly = true)
    public boolean isRunningAppHost(String host) {
        return resolveApp(host)
                .filter(app -> AppStatus.RUNNING.equals(app.getStatus()))
                .isPresent();
    }
}
