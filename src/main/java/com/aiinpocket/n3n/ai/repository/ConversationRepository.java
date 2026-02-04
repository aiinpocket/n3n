package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for AI conversations.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Find conversations by user ID, ordered by updated time.
     */
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /**
     * Find conversations by user ID with pagination.
     */
    Page<Conversation> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find conversations by user ID and flow ID.
     */
    List<Conversation> findByUserIdAndFlowIdOrderByUpdatedAtDesc(UUID userId, UUID flowId);

    /**
     * Count conversations by user ID.
     */
    long countByUserId(UUID userId);

    /**
     * Delete all conversations for a user.
     */
    void deleteByUserId(UUID userId);
}
