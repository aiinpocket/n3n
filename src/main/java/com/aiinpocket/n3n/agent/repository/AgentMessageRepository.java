package com.aiinpocket.n3n.agent.repository;

import com.aiinpocket.n3n.agent.entity.AgentMessage;
import com.aiinpocket.n3n.agent.entity.AgentConversation.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent 訊息 Repository
 */
@Repository
public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {

    /**
     * 根據對話 ID 查詢訊息（按時間排序）
     */
    List<AgentMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * 取得對話的最後 N 則訊息
     */
    @Query("SELECT m FROM AgentMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "ORDER BY m.createdAt DESC")
    List<AgentMessage> findLastMessages(
        @Param("conversationId") UUID conversationId, Pageable pageable);

    /**
     * 取得對話的訊息數量
     */
    long countByConversationId(UUID conversationId);

    /**
     * 計算對話的總 token 數
     */
    @Query("SELECT COALESCE(SUM(m.tokenCount), 0) FROM AgentMessage m " +
           "WHERE m.conversation.id = :conversationId")
    int sumTokenCountByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * 取得包含流程定義的訊息
     */
    @Query("SELECT m FROM AgentMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.role = 'ASSISTANT' " +
           "AND m.structuredData IS NOT NULL " +
           "ORDER BY m.createdAt DESC")
    List<AgentMessage> findMessagesWithStructuredData(
        @Param("conversationId") UUID conversationId);

    /**
     * 統計使用者的 AI 使用量（時間區間內）
     */
    @Query("SELECT COALESCE(SUM(m.tokenCount), 0) FROM AgentMessage m " +
           "WHERE m.conversation.user.id = :userId " +
           "AND m.createdAt >= :from " +
           "AND m.createdAt < :to")
    int sumTokenCountByUserIdAndPeriod(
        @Param("userId") UUID userId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * 統計特定角色的訊息數量
     */
    long countByConversationIdAndRole(UUID conversationId, MessageRole role);

    /**
     * 刪除對話的所有訊息
     */
    void deleteByConversationId(UUID conversationId);
}
