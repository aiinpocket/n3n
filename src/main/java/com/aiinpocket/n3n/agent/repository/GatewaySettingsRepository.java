package com.aiinpocket.n3n.agent.repository;

import com.aiinpocket.n3n.agent.entity.GatewaySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Gateway 設定 Repository
 * 這是單例表格，只有 id=1 的一筆記錄
 */
@Repository
public interface GatewaySettingsRepository extends JpaRepository<GatewaySettings, Long> {

    /**
     * 取得 Gateway 設定（單例）
     */
    default GatewaySettings getSettings() {
        return findById(1L).orElseGet(() -> {
            GatewaySettings settings = GatewaySettings.builder().build();
            return save(settings);
        });
    }
}
