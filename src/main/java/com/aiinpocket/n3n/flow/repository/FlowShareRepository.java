package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowShareRepository extends JpaRepository<FlowShare, UUID> {

    /**
     * 取得流程的所有分享記錄
     */
    List<FlowShare> findByFlowId(UUID flowId);

    /**
     * 取得用戶被分享的所有流程
     */
    List<FlowShare> findByUserId(UUID userId);

    /**
     * 檢查特定用戶是否有流程的存取權
     */
    Optional<FlowShare> findByFlowIdAndUserId(UUID flowId, UUID userId);

    /**
     * 檢查 Email 邀請是否存在
     */
    Optional<FlowShare> findByFlowIdAndInvitedEmail(UUID flowId, String email);

    /**
     * 取得用戶在特定流程的權限
     */
    @Query("SELECT fs.permission FROM FlowShare fs WHERE fs.flowId = :flowId AND fs.userId = :userId")
    Optional<String> findPermissionByFlowIdAndUserId(UUID flowId, UUID userId);

    /**
     * 刪除流程的所有分享記錄
     */
    void deleteByFlowId(UUID flowId);

    /**
     * 刪除特定分享
     */
    void deleteByFlowIdAndUserId(UUID flowId, UUID userId);

    /**
     * 檢查用戶是否有編輯權限
     */
    @Query("SELECT COUNT(fs) > 0 FROM FlowShare fs WHERE fs.flowId = :flowId AND fs.userId = :userId " +
           "AND fs.permission IN ('edit', 'admin')")
    boolean hasEditPermission(UUID flowId, UUID userId);

    /**
     * 取得待接受的 Email 邀請
     */
    List<FlowShare> findByInvitedEmailAndAcceptedAtIsNull(String email);

    /**
     * 統計流程的分享數量
     */
    long countByFlowId(UUID flowId);
}
