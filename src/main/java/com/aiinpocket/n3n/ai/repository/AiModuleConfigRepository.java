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
}
