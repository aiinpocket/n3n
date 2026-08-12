package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.AiModuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiModuleConfigRepository extends JpaRepository<AiModuleConfig, UUID> {

    List<AiModuleConfig> findByUserId(UUID userId);

    List<AiModuleConfig> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<AiModuleConfig> findByUserIdAndFeatureAndIsActiveTrue(UUID userId, String feature);

    Optional<AiModuleConfig> findByUserIdAndFeature(UUID userId, String feature);

    /**
     * 平台 fallback：取得任一使用者為此功能設定的啟用配置
     * （AI 金鑰為平台共用，成員沒有自己的配置時沿用平台配置）
     */
    List<AiModuleConfig> findByFeatureAndIsActiveTrueOrderByCreatedAtAsc(String feature);
}
