package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowShareLinkRepository extends JpaRepository<FlowShareLink, UUID> {

    /**
     * 以 token 取得分享連結
     */
    Optional<FlowShareLink> findByToken(String token);

    /**
     * 取得流程尚未撤銷的分享連結（新到舊）
     */
    List<FlowShareLink> findByFlowIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID flowId);
}
