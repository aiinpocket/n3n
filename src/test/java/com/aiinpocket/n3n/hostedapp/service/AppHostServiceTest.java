package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.site.service.SiteDomains;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppHostService：host → 小應用解析、執行中判定（tls-check 用）、休眠行為。
 */
@ExtendWith(MockitoExtension.class)
class AppHostServiceTest {

    private static final String BASE_DOMAIN = "apps.example.com";
    private static final String SLUG = "my-app-ab12";
    private static final String HOST = SLUG + "." + BASE_DOMAIN;

    @Mock
    private HostedAppProperties properties;

    @Mock
    private HostedAppRepository repository;

    private AppHostService serviceWith(String baseDomain) {
        return new AppHostService(properties, new SiteDomains(baseDomain), repository);
    }

    private HostedApp app(String status) {
        return HostedApp.builder()
                .id(UUID.randomUUID()).ownerId(UUID.randomUUID())
                .name("My App").slug(SLUG).appType("compose").status(status)
                .build();
    }

    @Test
    @DisplayName("功能開啟且 base-domain 設定：{slug}.{base} 解析到小應用")
    void resolvesAppFromHost() {
        when(properties.isEnabled()).thenReturn(true);
        when(repository.findBySlug(SLUG)).thenReturn(Optional.of(app(AppStatus.RUNNING)));

        AppHostService service = serviceWith(BASE_DOMAIN);

        assertThat(service.isActive()).isTrue();
        assertThat(service.resolveApp(HOST)).isPresent();
        assertThat(service.isRunningAppHost(HOST)).isTrue();
    }

    @Test
    @DisplayName("停止中的應用：resolveApp 有值但 isRunningAppHost 為 false")
    void stoppedAppIsNotServable() {
        when(properties.isEnabled()).thenReturn(true);
        when(repository.findBySlug(SLUG)).thenReturn(Optional.of(app(AppStatus.STOPPED)));

        AppHostService service = serviceWith(BASE_DOMAIN);

        assertThat(service.resolveApp(HOST)).isPresent();
        assertThat(service.isRunningAppHost(HOST)).isFalse();
    }

    @Test
    @DisplayName("apps 功能關閉：一律回空、不查 DB")
    void dormantWhenFeatureDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        AppHostService service = serviceWith(BASE_DOMAIN);

        assertThat(service.isActive()).isFalse();
        assertThat(service.resolveApp(HOST)).isEmpty();
        verify(repository, never()).findBySlug(anyString());
    }

    @Test
    @DisplayName("base-domain 未設定：一律回空、不查 DB")
    void dormantWhenBaseDomainMissing() {
        when(properties.isEnabled()).thenReturn(true);

        AppHostService service = serviceWith("");

        assertThat(service.isActive()).isFalse();
        assertThat(service.resolveApp(HOST)).isEmpty();
        verify(repository, never()).findBySlug(anyString());
    }

    @Test
    @DisplayName("非 wildcard 子網域的 host（主應用 / 多層）不解析")
    void unrelatedHostsResolveEmpty() {
        when(properties.isEnabled()).thenReturn(true);

        AppHostService service = serviceWith(BASE_DOMAIN);

        assertThat(service.resolveApp("n3n.example.com")).isEmpty();
        assertThat(service.resolveApp("a.b." + BASE_DOMAIN)).isEmpty();
        assertThat(service.resolveApp(BASE_DOMAIN)).isEmpty();
        verify(repository, never()).findBySlug(anyString());
    }
}
