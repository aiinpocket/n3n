package com.aiinpocket.n3n.webhook.repository;

import com.aiinpocket.n3n.webhook.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    Optional<Webhook> findByPathAndMethodAndIsActiveTrue(String path, String method);

    List<Webhook> findByFlowIdOrderByCreatedAtDesc(UUID flowId);

    List<Webhook> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    boolean existsByPath(String path);

    boolean existsByPathAndMethod(String path, String method);
}
