package com.aiinpocket.n3n.agent.repository;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.entity.AgentRegistration.AgentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 註冊 Repository
 */
@Repository
public interface AgentRegistrationRepository extends JpaRepository<AgentRegistration, UUID> {

    /**
     * 根據 Token Hash 查詢註冊記錄
     */
    Optional<AgentRegistration> findByRegistrationTokenHash(String tokenHash);

    /**
     * 根據 Device ID 查詢註冊記錄
     */
    Optional<AgentRegistration> findByDeviceId(String deviceId);

    /**
     * 查詢使用者的所有註冊
     */
    Page<AgentRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 查詢使用者的所有註冊（不分頁）
     */
    List<AgentRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 根據狀態查詢使用者的註冊
     */
    List<AgentRegistration> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AgentStatus status);

    /**
     * 計算使用者的註冊數量
     */
    long countByUserId(UUID userId);

    /**
     * 計算使用者特定狀態的註冊數量
     */
    long countByUserIdAndStatus(UUID userId, AgentStatus status);

    /**
     * 檢查 Token Hash 是否存在
     */
    boolean existsByRegistrationTokenHash(String tokenHash);

    /**
     * 檢查 Device ID 是否已註冊
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * 驗證註冊歸屬
     */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * 查詢所有已封鎖的 Agent
     */
    List<AgentRegistration> findByStatus(AgentStatus status);

    /**
     * 更新最後活動時間
     */
    @Modifying
    @Query("UPDATE AgentRegistration r SET r.lastSeenAt = CURRENT_TIMESTAMP WHERE r.deviceId = :deviceId")
    int updateLastSeenByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 根據 ID 和使用者 ID 查詢（確保權限）
     */
    Optional<AgentRegistration> findByIdAndUserId(UUID id, UUID userId);
}
