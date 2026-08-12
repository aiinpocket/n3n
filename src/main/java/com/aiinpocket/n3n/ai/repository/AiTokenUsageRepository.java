package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.AiTokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiTokenUsageRepository extends JpaRepository<AiTokenUsage, UUID> {

    /**
     * 依 provider + model 彙總指定期間的 token 用量。
     * 回傳欄位順序：provider, model, callCount, inputTokens, outputTokens
     */
    @Query("""
            SELECT u.provider, u.model, COUNT(u), SUM(u.inputTokens), SUM(u.outputTokens)
            FROM AiTokenUsage u
            WHERE u.userId = :userId AND u.createdAt >= :since
            GROUP BY u.provider, u.model
            ORDER BY SUM(u.inputTokens) + SUM(u.outputTokens) DESC
            """)
    List<Object[]> summarizeByProviderAndModel(@Param("userId") UUID userId, @Param("since") Instant since);

    /**
     * 平台全域：依 provider + model 彙總指定期間的 token 用量（不分使用者）。
     * 回傳欄位順序：provider, model, callCount, inputTokens, outputTokens
     */
    @Query("""
            SELECT u.provider, u.model, COUNT(u), SUM(u.inputTokens), SUM(u.outputTokens)
            FROM AiTokenUsage u
            WHERE u.createdAt >= :since
            GROUP BY u.provider, u.model
            ORDER BY SUM(u.inputTokens) + SUM(u.outputTokens) DESC
            """)
    List<Object[]> summarizePlatformByProviderAndModel(@Param("since") Instant since);

    /**
     * 平台全域：依 userId + model 彙總指定期間的 token 用量，
     * 供管理員的成員用量報表使用（model 保留以便估算成本）。
     * 回傳欄位順序：userId, model, callCount, inputTokens, outputTokens
     */
    @Query("""
            SELECT u.userId, u.model, COUNT(u), SUM(u.inputTokens), SUM(u.outputTokens)
            FROM AiTokenUsage u
            WHERE u.createdAt >= :since
            GROUP BY u.userId, u.model
            ORDER BY SUM(u.inputTokens) + SUM(u.outputTokens) DESC
            """)
    List<Object[]> summarizeByUserAndModel(@Param("since") Instant since);
}
