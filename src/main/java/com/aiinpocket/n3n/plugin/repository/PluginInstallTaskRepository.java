package com.aiinpocket.n3n.plugin.repository;

import com.aiinpocket.n3n.plugin.entity.PluginInstallTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PluginInstallTaskRepository extends JpaRepository<PluginInstallTask, UUID> {

    /**
     * 查詢使用者的安裝任務
     */
    List<PluginInstallTask> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 查詢使用者進行中的安裝任務
     */
    @Query("SELECT t FROM PluginInstallTask t WHERE t.userId = :userId " +
           "AND t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') " +
           "ORDER BY t.createdAt DESC")
    List<PluginInstallTask> findActiveTasksByUserId(@Param("userId") UUID userId);

    /**
     * 查詢特定節點類型的安裝任務
     */
    Optional<PluginInstallTask> findByUserIdAndNodeTypeAndStatusNot(
        UUID userId, String nodeType, PluginInstallTask.InstallStatus status);

    /**
     * 查詢所有進行中的任務
     */
    @Query("SELECT t FROM PluginInstallTask t WHERE t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    List<PluginInstallTask> findAllActiveTasks();

    /**
     * 檢查是否有相同節點類型的進行中任務
     */
    @Query("SELECT COUNT(t) > 0 FROM PluginInstallTask t WHERE t.userId = :userId " +
           "AND t.nodeType = :nodeType AND t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    boolean existsActiveTaskForNodeType(@Param("userId") UUID userId, @Param("nodeType") String nodeType);

    /**
     * 根據容器 ID 查詢
     */
    Optional<PluginInstallTask> findByContainerId(String containerId);
}
