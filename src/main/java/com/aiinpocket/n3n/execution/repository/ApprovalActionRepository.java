package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {

    /**
     * Find all actions for an approval
     */
    List<ApprovalAction> findByApprovalIdOrderByCreatedAtDesc(UUID approvalId);

    /**
     * Find action by approval ID and user ID
     */
    Optional<ApprovalAction> findByApprovalIdAndUserId(UUID approvalId, UUID userId);

    /**
     * Check if user has already acted on this approval
     */
    boolean existsByApprovalIdAndUserId(UUID approvalId, UUID userId);

    /**
     * Count approvals by approval ID and action type
     */
    @Query("SELECT COUNT(a) FROM ApprovalAction a WHERE a.approvalId = :approvalId AND a.action = :action")
    long countByApprovalIdAndAction(@Param("approvalId") UUID approvalId, @Param("action") String action);

    /**
     * Find all approval actions by a user
     */
    List<ApprovalAction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
