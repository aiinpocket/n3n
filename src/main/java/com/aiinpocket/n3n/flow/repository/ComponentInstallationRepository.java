package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.ComponentInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComponentInstallationRepository extends JpaRepository<ComponentInstallation, UUID> {

    /**
     * 取得指定匯入記錄的安裝記錄
     */
    List<ComponentInstallation> findByImportId(UUID importId);

    /**
     * 取得指定狀態的安裝記錄
     */
    List<ComponentInstallation> findByStatus(String status);

    /**
     * 取得 pending 狀態的安裝記錄
     */
    default List<ComponentInstallation> findPending() {
        return findByStatus(ComponentInstallation.STATUS_PENDING);
    }
}
