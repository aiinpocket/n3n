package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.entity.GatewaySettings;
import com.aiinpocket.n3n.agent.repository.GatewaySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gateway 設定管理服務
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GatewaySettingsService {

    private final GatewaySettingsRepository gatewaySettingsRepository;

    /**
     * 取得 Gateway 設定
     */
    @Transactional(readOnly = true)
    public GatewaySettings getSettings() {
        return gatewaySettingsRepository.getSettings();
    }

    /**
     * 更新 Gateway 設定
     */
    @Transactional
    public GatewaySettings updateSettings(String domain, Integer port, Boolean enabled) {
        GatewaySettings settings = gatewaySettingsRepository.getSettings();

        if (domain != null && !domain.isBlank()) {
            settings.setGatewayDomain(domain.trim());
        }

        if (port != null && port > 0 && port <= 65535) {
            settings.setGatewayPort(port);
        }

        if (enabled != null) {
            settings.setEnabled(enabled);
        }

        GatewaySettings saved = gatewaySettingsRepository.save(settings);
        log.info("Gateway settings updated: domain={}, port={}, enabled={}",
            saved.getGatewayDomain(), saved.getGatewayPort(), saved.getEnabled());

        return saved;
    }

    /**
     * 產生 Agent Config 內容
     */
    public AgentConfig generateAgentConfig(String registrationToken, String agentId) {
        GatewaySettings settings = getSettings();

        return new AgentConfig(
            1,
            new AgentConfig.Gateway(
                settings.getWebSocketUrl(),
                settings.getGatewayDomain(),
                settings.getGatewayPort()
            ),
            new AgentConfig.Registration(
                registrationToken,
                agentId
            )
        );
    }

    /**
     * Agent Config 資料結構
     */
    public record AgentConfig(
        int version,
        Gateway gateway,
        Registration registration
    ) {
        public record Gateway(
            String url,
            String domain,
            int port
        ) {}

        public record Registration(
            String token,
            String agentId
        ) {}
    }
}
