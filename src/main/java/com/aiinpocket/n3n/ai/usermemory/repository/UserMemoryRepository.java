package com.aiinpocket.n3n.ai.usermemory.repository;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用者記憶存取層。所有查詢一律以 userId 為界，避免跨使用者洩漏。
 */
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, UUID> {

    List<UserMemory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<UserMemory> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    Optional<UserMemory> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    @Modifying
    void deleteByUserId(UUID userId);
}
