package com.aiinpocket.n3n.backup.repository;

import com.aiinpocket.n3n.backup.entity.BackupSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BackupSettingsRepository extends JpaRepository<BackupSettings, Long> {

    @Transactional
    default BackupSettings getOrCreate() {
        return findById(1L).orElseGet(() -> save(BackupSettings.builder().build()));
    }
}
