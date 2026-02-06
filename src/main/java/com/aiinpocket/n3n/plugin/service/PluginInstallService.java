package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.plugin.dto.InstallPluginRequest;
import com.aiinpocket.n3n.plugin.entity.*;
import com.aiinpocket.n3n.plugin.orchestrator.ContainerOrchestrator;
import com.aiinpocket.n3n.plugin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Plugin 安裝服務
 * 處理 Plugin 的自動安裝，透過 ContainerOrchestrator 管理容器（Docker 或 K8s）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginInstallService {

    private final PluginInstallTaskRepository taskRepository;
    private final PluginRepository pluginRepository;
    private final PluginVersionRepository pluginVersionRepository;
    @Qualifier("pluginPluginService")
    private final PluginService pluginService;
    private final ContainerOrchestrator containerOrchestrator;
    private final PluginNotificationService notificationService;
    private final ContainerNodeDefinitionFetcher nodeDefinitionFetcher;

    /**
     * 批量安裝缺失的節點類型
     * 返回安裝任務 ID 列表
     */
    @Transactional
    public List<UUID> installMissingNodes(List<String> nodeTypes, UUID userId) {
        List<UUID> taskIds = new ArrayList<>();

        for (String nodeType : nodeTypes) {
            // 檢查是否已有進行中的任務
            if (taskRepository.existsActiveTaskForNodeType(userId, nodeType)) {
                log.info("Already installing node type: {}", nodeType);
                continue;
            }

            // 解析安裝來源
            InstallSourceInfo sourceInfo = resolveInstallSource(nodeType);
            if (sourceInfo == null) {
                log.warn("Cannot resolve install source for node type: {}", nodeType);
                continue;
            }

            // 建立安裝任務
            PluginInstallTask task = new PluginInstallTask();
            task.setUserId(userId);
            task.setNodeType(nodeType);
            task.setSource(sourceInfo.source);
            task.setSourceReference(sourceInfo.reference);
            task.setPluginId(sourceInfo.pluginId);
            task.setStatus(PluginInstallTask.InstallStatus.PENDING);
            task.setCurrentStage("等待安裝");
            task.setMetadata(sourceInfo.metadata);

            taskRepository.save(task);
            taskIds.add(task.getId());

            // 非同步執行安裝
            executeInstallAsync(task.getId());
        }

        return taskIds;
    }

    /**
     * 安裝單一 Plugin
     */
    @Transactional
    public UUID installPlugin(UUID pluginId, UUID userId, String version) {
        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginId));

        PluginVersion pluginVersion = version != null ?
            pluginVersionRepository.findByPluginIdAndVersion(pluginId, version)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version)) :
            pluginVersionRepository.findLatestByPluginId(pluginId)
                .orElseThrow(() -> new IllegalStateException("No version available"));

        // 取得主要節點類型
        String nodeType = plugin.getName();
        Map<String, Object> nodeDefinitions = pluginVersion.getNodeDefinitions();
        if (nodeDefinitions != null && nodeDefinitions.containsKey("nodes")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodeDefinitions.get("nodes");
            if (!nodes.isEmpty()) {
                nodeType = (String) nodes.get(0).getOrDefault("type", plugin.getName());
            }
        }

        // 建立安裝任務
        PluginInstallTask task = new PluginInstallTask();
        task.setUserId(userId);
        task.setPluginId(pluginId);
        task.setNodeType(nodeType);
        task.setSource(PluginInstallTask.InstallSource.MARKETPLACE);
        task.setSourceReference(pluginVersion.getVersion());
        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        task.setCurrentStage("等待安裝");

        taskRepository.save(task);

        // 非同步執行安裝
        executeInstallAsync(task.getId());

        return task.getId();
    }

    /**
     * 取得安裝任務狀態
     */
    public PluginInstallTask getTaskStatus(UUID taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    /**
     * 取得使用者進行中的任務
     */
    public List<PluginInstallTask> getActiveTasks(UUID userId) {
        return taskRepository.findActiveTasksByUserId(userId);
    }

    /**
     * 取消安裝任務
     */
    @Transactional
    public void cancelTask(UUID taskId, UUID userId) {
        PluginInstallTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to cancel this task");
        }

        if (task.isTerminal()) {
            throw new IllegalStateException("Task already completed");
        }

        task.setStatus(PluginInstallTask.InstallStatus.CANCELLED);
        task.setCurrentStage("已取消");
        taskRepository.save(task);

        // 如果有容器正在運行，停止它
        if (task.getContainerId() != null) {
            containerOrchestrator.stop(task.getContainerId());
        }

        notificationService.notifyTaskCancelled(task);
    }

    /**
     * 非同步執行安裝
     */
    @Async
    public CompletableFuture<Void> executeInstallAsync(UUID taskId) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeInstall(taskId);
            } catch (Exception e) {
                log.error("Install task {} failed", taskId, e);
                markTaskFailed(taskId, e.getMessage());
            }
        });
    }

    /**
     * 執行安裝
     */
    private void executeInstall(UUID taskId) {
        PluginInstallTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        log.info("Starting install task: {} for node type: {} (orchestrator: {})",
                taskId, task.getNodeType(), containerOrchestrator.getOrchestratorType());

        try {
            switch (task.getSource()) {
                case MARKETPLACE -> installFromMarketplace(task);
                case DOCKER_HUB, DOCKER_REGISTRY -> installFromDocker(task);
                case LOCAL -> installLocal(task);
                default -> throw new UnsupportedOperationException(
                    "Unsupported install source: " + task.getSource());
            }
        } catch (Exception e) {
            log.error("Install failed for task {}", taskId, e);
            markTaskFailed(taskId, e.getMessage());
            throw e;
        }
    }

    /**
     * 從 Marketplace 安裝
     */
    private void installFromMarketplace(PluginInstallTask task) {
        UUID pluginId = task.getPluginId();
        if (pluginId == null) {
            throw new IllegalStateException("Plugin ID is required for marketplace install");
        }

        updateTaskProgress(task, PluginInstallTask.InstallStatus.PULLING, 10, "取得 Plugin 資訊");

        Plugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));

        PluginVersion version = pluginVersionRepository.findLatestByPluginId(pluginId)
            .orElseThrow(() -> new IllegalStateException("No version available"));

        updateTaskProgress(task, PluginInstallTask.InstallStatus.CONFIGURING, 30, "配置 Plugin");

        // 檢查是否需要容器
        String dockerImage = (String) version.getConfigSchema().get("dockerImage");
        if (dockerImage != null && !dockerImage.isBlank()) {
            installWithContainer(task, dockerImage);
        } else {
            // 直接安裝（不需要容器）
            updateTaskProgress(task, PluginInstallTask.InstallStatus.REGISTERING, 70, "註冊節點");
            InstallPluginRequest installRequest = new InstallPluginRequest();
            installRequest.setVersion(version.getVersion());
            pluginService.installPlugin(pluginId, task.getUserId(), installRequest);
        }

        markTaskCompleted(task);
    }

    /**
     * 從 Docker Hub 安裝
     */
    private void installFromDocker(PluginInstallTask task) {
        String imageRef = task.getSourceReference();
        if (imageRef == null || imageRef.isBlank()) {
            throw new IllegalArgumentException("Docker image reference is required");
        }

        installWithContainer(task, imageRef);
        markTaskCompleted(task);
    }

    /**
     * 使用容器安裝（Docker 或 K8s，由 ContainerOrchestrator 決定）
     */
    private void installWithContainer(PluginInstallTask task, String imageRef) {
        // 解析映像名稱和標籤
        String image = imageRef;
        String tag = "latest";
        if (imageRef.contains(":")) {
            String[] parts = imageRef.split(":");
            image = parts[0];
            tag = parts[1];
        }

        // 拉取映像
        updateTaskProgress(task, PluginInstallTask.InstallStatus.PULLING, 20, "準備映像: " + imageRef);

        containerOrchestrator.pullImage(image, tag, (progress, status) -> {
            int percent = 20 + (int) (progress * 40); // 20% - 60%
            updateTaskProgress(task, PluginInstallTask.InstallStatus.PULLING, percent, status);
            notificationService.notifyTaskProgress(task);
        });

        // 啟動容器/Pod
        updateTaskProgress(task, PluginInstallTask.InstallStatus.STARTING, 65, "啟動容器");

        String containerName = task.getNodeType() + "-" + task.getId().toString().substring(0, 8);
        ContainerOrchestrator.ContainerInfo containerInfo = containerOrchestrator.createAndStart(
            imageRef,
            containerName,
            Map.of(
                "N3N_PLUGIN_TYPE", task.getNodeType(),
                "N3N_TASK_ID", task.getId().toString()
            )
        );

        task.setContainerId(containerInfo.containerId());
        task.setContainerPort(containerInfo.port());
        taskRepository.save(task);

        // 等待容器就緒
        updateTaskProgress(task, PluginInstallTask.InstallStatus.STARTING, 75, "等待容器就緒");

        boolean healthy = containerOrchestrator.waitForHealthy(containerInfo.containerId(), 60);
        if (!healthy) {
            throw new RuntimeException("Container failed to become healthy");
        }

        // 註冊節點
        updateTaskProgress(task, PluginInstallTask.InstallStatus.REGISTERING, 85, "註冊節點");

        boolean registered = nodeDefinitionFetcher.fetchAndRegisterNodes(
                containerInfo.containerId(),
                containerInfo.port(),
                task.getNodeType()
        );

        if (registered) {
            log.info("Successfully registered nodes from container {} on port {}",
                    containerInfo.containerId(), containerInfo.port());
        } else {
            log.warn("Failed to register nodes from container {}, but container is running",
                    containerInfo.containerId());
        }

        updateTaskProgress(task, PluginInstallTask.InstallStatus.REGISTERING, 95, "節點註冊完成");
    }

    /**
     * 本地安裝
     */
    private void installLocal(PluginInstallTask task) {
        updateTaskProgress(task, PluginInstallTask.InstallStatus.REGISTERING, 50, "本地安裝");
        markTaskCompleted(task);
    }

    /**
     * 解析安裝來源
     */
    private InstallSourceInfo resolveInstallSource(String nodeType) {
        // 1. 先從 Marketplace 查找
        List<Plugin> plugins = pluginRepository.findAll();
        for (Plugin plugin : plugins) {
            PluginVersion version = pluginVersionRepository.findLatestByPluginId(plugin.getId())
                .orElse(null);
            if (version == null) continue;

            Map<String, Object> nodeDefinitions = version.getNodeDefinitions();
            if (nodeDefinitions == null || !nodeDefinitions.containsKey("nodes")) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodeDefinitions.get("nodes");
            for (Map<String, Object> node : nodes) {
                if (nodeType.equals(node.get("type"))) {
                    return new InstallSourceInfo(
                        PluginInstallTask.InstallSource.MARKETPLACE,
                        version.getVersion(),
                        plugin.getId(),
                        Map.of("pluginName", plugin.getName())
                    );
                }
            }
        }

        // 2. 查找已知的 Docker 映像對應
        String dockerImage = resolveNodeTypeToDockerImage(nodeType);
        if (dockerImage != null) {
            return new InstallSourceInfo(
                PluginInstallTask.InstallSource.DOCKER_HUB,
                dockerImage,
                null,
                Map.of("autoResolved", true)
            );
        }

        return null;
    }

    /**
     * 節點類型轉 Docker 映像
     */
    private String resolveNodeTypeToDockerImage(String nodeType) {
        return switch (nodeType.toLowerCase()) {
            case "puppeteer", "browser" -> "n3n/puppeteer-plugin:latest";
            case "ffmpeg", "video" -> "n3n/ffmpeg-plugin:latest";
            case "imagemagick", "image" -> "n3n/imagemagick-plugin:latest";
            case "selenium" -> "n3n/selenium-plugin:latest";
            case "tesseract", "ocr" -> "n3n/tesseract-plugin:latest";
            default -> null;
        };
    }

    private void updateTaskProgress(PluginInstallTask task,
                                    PluginInstallTask.InstallStatus status,
                                    int progress, String stage) {
        task.setStatus(status);
        task.updateProgress(progress, stage);
        taskRepository.save(task);
        notificationService.notifyTaskProgress(task);
    }

    private void markTaskCompleted(PluginInstallTask task) {
        task.markCompleted();
        taskRepository.save(task);
        notificationService.notifyTaskCompleted(task);
        log.info("Install task {} completed for node type: {}", task.getId(), task.getNodeType());
    }

    private void markTaskFailed(UUID taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.markFailed(errorMessage);
            taskRepository.save(task);
            notificationService.notifyTaskFailed(task);
        });
    }

    /**
     * 安裝來源資訊
     */
    private record InstallSourceInfo(
        PluginInstallTask.InstallSource source,
        String reference,
        UUID pluginId,
        Map<String, Object> metadata
    ) {}
}
