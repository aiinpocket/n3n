package com.aiinpocket.n3n.plugin.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 容器編排抽象介面
 *
 * 支援 Docker 和 Kubernetes 兩種實作，系統啟動時自動偵測環境。
 * Docker 模式使用 Docker CLI 管理容器，K8s 模式使用 fabric8 API 管理 Pod/Service。
 */
public interface ContainerOrchestrator {

    /**
     * 容器/Pod 資訊
     */
    record ContainerInfo(String containerId, Integer port, String name) {}

    /**
     * 容器/Pod 狀態快照
     */
    record ContainerStatus(
            String id,
            String name,
            String state,
            Integer port,
            Map<String, String> labels,
            String image
    ) {}

    /**
     * 檢查編排系統是否可用
     */
    boolean isAvailable();

    /**
     * 取得編排類型名稱（docker 或 kubernetes）
     */
    String getOrchestratorType();

    /**
     * 驗證映像是否來自可信任的 Registry
     */
    boolean isFromTrustedRegistry(String image);

    /**
     * 拉取/準備映像
     * K8s 模式中由 kubelet 自動拉取，此方法只做驗證。
     *
     * @param image            映像名稱
     * @param tag              映像標籤
     * @param progressCallback 進度回調 (progress 0-1, statusMessage)
     */
    void pullImage(String image, String tag, BiConsumer<Double, String> progressCallback);

    /**
     * 建立並啟動容器/Pod
     *
     * @param image 映像名稱（含標籤）
     * @param name  容器/Deployment 名稱
     * @param envVars 環境變數
     * @return 容器資訊
     */
    ContainerInfo createAndStart(String image, String name, Map<String, String> envVars);

    /**
     * 等待容器/Pod 健康
     *
     * @param containerId    容器 ID 或 Deployment 名稱
     * @param timeoutSeconds 超時秒數
     * @return 是否健康
     */
    boolean waitForHealthy(String containerId, int timeoutSeconds);

    /**
     * 停止容器/縮減 Pod 副本為 0
     */
    void stop(String containerId);

    /**
     * 停止並移除容器/完全清理 Deployment + Service
     */
    void stopAndRemove(String name);

    /**
     * 取得容器/Pod 日誌
     */
    String getLogs(String containerId, int tailLines);

    // ===== 服務發現 =====

    /**
     * 列出所有管理中的 Plugin 容器/Pod
     */
    List<ContainerStatus> listPluginContainers();

    /**
     * 取得特定容器/Pod 的可存取端點 URL
     *
     * @param containerId 容器 ID 或 Deployment 名稱
     * @return 完整的 HTTP URL（如 http://localhost:32768 或 http://name.ns.svc.cluster.local:8080）
     */
    String getServiceEndpoint(String containerId);
}
