package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowImportRepository extends JpaRepository<FlowImport, UUID> {

    /**
     * 取得指定 Flow 的匯入記錄
     */
    Optional<FlowImport> findByFlowId(UUID flowId);

    /**
     * 取得使用者的匯入記錄
     */
    List<FlowImport> findByImportedByOrderByImportedAtDesc(UUID userId);

    /**
     * 取得指定狀態的匯入記錄
     */
    List<FlowImport> findByStatus(String status);
}
