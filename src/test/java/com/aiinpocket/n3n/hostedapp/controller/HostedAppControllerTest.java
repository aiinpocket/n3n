package com.aiinpocket.n3n.hostedapp.controller;

import com.aiinpocket.n3n.hostedapp.service.HostedAppService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * /api/apps/availability 回應形狀：{enabled, baseDomain}。
 * baseDomain 未設定時為 null（前端退回 host:port 網址）。
 */
@ExtendWith(MockitoExtension.class)
class HostedAppControllerTest {

    @Mock
    private HostedAppService appService;

    @Test
    @DisplayName("availability：enabled=true 且 base-domain 已設定 → 回傳網域")
    void availabilityWithBaseDomain() {
        when(appService.isEnabled()).thenReturn(true);
        HostedAppController controller =
                new HostedAppController(appService, new SiteDomains("apps.example.com"));

        Map<String, Object> body = controller.availability().getBody();

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("enabled", true);
        assertThat(body).containsEntry("baseDomain", "apps.example.com");
    }

    @Test
    @DisplayName("availability：功能關閉且無 base-domain → enabled=false、baseDomain=null")
    void availabilityDisabled() {
        when(appService.isEnabled()).thenReturn(false);
        HostedAppController controller =
                new HostedAppController(appService, new SiteDomains(""));

        Map<String, Object> body = controller.availability().getBody();

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("enabled", false);
        assertThat(body).containsKey("baseDomain");
        assertThat(body.get("baseDomain")).isNull();
    }
}
