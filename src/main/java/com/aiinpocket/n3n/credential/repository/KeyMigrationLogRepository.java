package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.KeyMigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KeyMigrationLogRepository extends JpaRepository<KeyMigrationLog, UUID> {

    /**
     * 取得指定憑證的遷移記錄
     */
    List<KeyMigrationLog> findByCredentialIdOrderByStartedAtDesc(UUID credentialId);

    /**
     * 取得指定狀態的遷移記錄
     */
    List<KeyMigrationLog> findByStatus(String status);

    /**
     * 取得使用者執行的遷移記錄
     */
    List<KeyMigrationLog> findByMigratedByOrderByStartedAtDesc(UUID userId);
}
