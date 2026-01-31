package com.aiinpocket.n3n.agent.repository;

import com.aiinpocket.n3n.agent.entity.AgentConversation;
import com.aiinpocket.n3n.agent.entity.AgentConversation.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 對話 Repository
 */
@Repository
public interface AgentConversationRepository extends JpaRepository<AgentConversation, UUID> {

    /**
     * 根據使用者 ID 查詢對話列表
     */
    Page<AgentConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 根據使用者 ID 和狀態查詢對話
     */
    Page<AgentConversation> findByUserIdAndStatusOrderByUpdatedAtDesc(
        UUID userId, ConversationStatus status, Pageable pageable);

    /**
     * 取得使用者最近的活躍對話
     */
    Optional<AgentConversation> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
        UUID userId, ConversationStatus status);

    /**
     * 計算使用者的對話數量
     */
    long countByUserId(UUID userId);

    /**
     * 計算使用者活躍對話數量
     */
    long countByUserIdAndStatus(UUID userId, ConversationStatus status);

    /**
     * 查詢與特定流程相關的對話
     */
    List<AgentConversation> findByDraftFlowId(UUID draftFlowId);

    /**
     * 查詢並載入訊息（避免 N+1）
     */
    @Query("SELECT c FROM AgentConversation c " +
           "LEFT JOIN FETCH c.messages " +
           "WHERE c.id = :id")
    Optional<AgentConversation> findByIdWithMessages(@Param("id") UUID id);

    /**
     * 驗證對話歸屬
     */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * 批量更新狀態（用於清理舊對話）
     */
    @Modifying
    @Query("UPDATE AgentConversation c SET c.status = :newStatus " +
           "WHERE c.status = :oldStatus AND c.updatedAt < :before")
    int updateStatusByStatusAndUpdatedAtBefore(
        @Param("oldStatus") ConversationStatus oldStatus,
        @Param("newStatus") ConversationStatus newStatus,
        @Param("before") Instant before);

    /**
     * 刪除過期對話
     */
    @Modifying
    @Query("DELETE FROM AgentConversation c WHERE c.status = :status AND c.updatedAt < :before")
    int deleteByStatusAndUpdatedAtBefore(
        @Param("status") ConversationStatus status,
        @Param("before") Instant before);
}
