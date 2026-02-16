package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.KeyMigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface KeyMigrationLogRepository extends JpaRepository<KeyMigrationLog, UUID> {
}
