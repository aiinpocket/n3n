package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, UUID> {

    /**
     * 取得使用者所有啟用的設定
     */
    List<AiProviderConfig> findByOwnerIdAndIsActiveTrue(UUID ownerId);

    /**
     * 取得使用者的預設設定
     */
    Optional<AiProviderConfig> findByOwnerIdAndIsDefaultTrue(UUID ownerId);

    /**
     * 取得使用者指定供應商的設定
     */
    List<AiProviderConfig> findByOwnerIdAndProviderAndIsActiveTrue(UUID ownerId, String provider);

    /**
     * 檢查名稱是否已存在
     */
    boolean existsByOwnerIdAndName(UUID ownerId, String name);

    /**
     * 根據 ID 和擁有者取得設定
     */
    Optional<AiProviderConfig> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * 計算使用者設定數量
     */
    long countByOwnerIdAndIsActiveTrue(UUID ownerId);

    // ==================== 平台共用設定（管理員統一管理） ====================

    /**
     * 取得所有平台共用且啟用的設定
     */
    List<AiProviderConfig> findByIsSharedTrueAndIsActiveTrue();

    /**
     * 取得平台共用的預設設定
     */
    Optional<AiProviderConfig> findByIsSharedTrueAndIsDefaultTrue();

    /**
     * 依 ID 取得平台共用設定
     */
    Optional<AiProviderConfig> findByIdAndIsSharedTrue(UUID id);

    /**
     * 檢查平台共用設定名稱是否已存在
     */
    boolean existsByIsSharedTrueAndName(String name);

    /**
     * 清除所有平台共用設定的預設標記（平台僅一個預設）
     */
    @Modifying
    @Query("UPDATE AiProviderConfig c SET c.isDefault = false WHERE c.isShared = true")
    void clearDefaultForShared();
}
