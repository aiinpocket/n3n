package com.aiinpocket.n3n.site.controller;

import com.aiinpocket.n3n.site.service.SiteDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caddy on_demand_tls 的 ask 端點：只有「{slug}.{base-domain} 對應已發佈
 * 站台」或「已驗證自訂網域」才回 200，否則 404 —— 防止任意網域指向平台
 * 誘發憑證簽發濫用（Let's Encrypt rate limit 燒毀 / 網域佔用）。
 * 路徑在 /api/public/** 之下，SecurityConfig 已 permitAll。
 */
@RestController
@RequiredArgsConstructor
public class SiteTlsCheckController {

    private final SiteDomainService siteDomainService;

    @GetMapping("/api/public/sites/tls-check")
    public ResponseEntity<Void> tlsCheck(@RequestParam String domain) {
        if (domain == null || domain.isBlank() || domain.length() > 255) {
            return ResponseEntity.notFound().build();
        }
        return siteDomainService.isServableHost(domain)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }
}
