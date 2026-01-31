package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {

    /**
     * 取得使用者的使用記錄
     */
    Page<AiUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 取得指定時間範圍內的使用記錄
     */
    List<AiUsageLog> findByUserIdAndCreatedAtBetween(UUID userId, Instant start, Instant end);

    /**
     * 統計使用者指定時間範圍的 Token 使用量
     */
    @Query("SELECT SUM(l.totalTokens) FROM AiUsageLog l WHERE l.userId = :userId AND l.createdAt >= :start")
    Long sumTotalTokensByUserIdSince(@Param("userId") UUID userId, @Param("start") Instant start);

    /**
     * 統計指定供應商的使用量
     */
    @Query("SELECT l.provider, l.model, COUNT(l), SUM(l.totalTokens) " +
           "FROM AiUsageLog l " +
           "WHERE l.userId = :userId AND l.createdAt >= :start " +
           "GROUP BY l.provider, l.model")
    List<Object[]> getUsageStatsByUserIdSince(@Param("userId") UUID userId, @Param("start") Instant start);

    /**
     * 取得最近的請求數（用於 Rate Limiting）
     */
    @Query("SELECT COUNT(l) FROM AiUsageLog l WHERE l.userId = :userId AND l.provider = :provider AND l.createdAt >= :since")
    long countRecentRequests(@Param("userId") UUID userId, @Param("provider") String provider, @Param("since") Instant since);
}
