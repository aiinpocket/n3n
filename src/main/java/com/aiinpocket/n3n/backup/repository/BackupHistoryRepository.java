package com.aiinpocket.n3n.backup.repository;

import com.aiinpocket.n3n.backup.entity.BackupHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackupHistoryRepository extends JpaRepository<BackupHistory, UUID> {

    Page<BackupHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
