package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

    /**
     * Find submission by execution ID and node ID
     */
    Optional<FormSubmission> findByExecutionIdAndNodeId(UUID executionId, String nodeId);

    /**
     * Find all submissions for an execution
     */
    List<FormSubmission> findByExecutionId(UUID executionId);

    /**
     * Check if submission exists for execution and node
     */
    boolean existsByExecutionIdAndNodeId(UUID executionId, String nodeId);

    /**
     * Find submissions by user
     */
    List<FormSubmission> findBySubmittedByOrderBySubmittedAtDesc(UUID submittedBy);
}
