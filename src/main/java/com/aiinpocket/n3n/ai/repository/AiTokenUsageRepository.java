package com.aiinpocket.n3n.ai.repository;

import com.aiinpocket.n3n.ai.entity.AiTokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiTokenUsageRepository extends JpaRepository<AiTokenUsage, UUID> {
}
