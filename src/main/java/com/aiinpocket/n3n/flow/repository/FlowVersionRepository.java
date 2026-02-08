package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowVersionRepository extends JpaRepository<FlowVersion, UUID> {

    List<FlowVersion> findByFlowIdOrderByCreatedAtDesc(UUID flowId);

    Optional<FlowVersion> findByFlowIdAndVersion(UUID flowId, String version);

    Optional<FlowVersion> findByFlowIdAndStatus(UUID flowId, String status);

    @Modifying
    @Query("UPDATE FlowVersion v SET v.status = :newStatus WHERE v.flowId = :flowId AND v.status = :currentStatus")
    int updateStatusByFlowIdAndStatus(@Param("flowId") UUID flowId,
                                      @Param("currentStatus") String currentStatus,
                                      @Param("newStatus") String newStatus);

    boolean existsByFlowIdAndVersion(UUID flowId, String version);

    /**
     * 批次查詢多個 Flow 的版本資訊（解決 N+1 問題）
     * 結果按 flowId 和 createdAt DESC 排序
     */
    List<FlowVersion> findByFlowIdInOrderByFlowIdAscCreatedAtDesc(List<UUID> flowIds);

    /**
     * 批次查詢多個 Flow 的已發布版本
     */
    List<FlowVersion> findByFlowIdInAndStatus(List<UUID> flowIds, String status);
}
