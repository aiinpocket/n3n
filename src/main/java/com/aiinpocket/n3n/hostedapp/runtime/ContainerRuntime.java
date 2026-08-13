package com.aiinpocket.n3n.hostedapp.runtime;

import java.util.List;
import java.util.Map;

/**
 * 容器執行環境抽象：所有 Docker 呼叫皆走此介面，測試以 mock 取代、
 * 完全不需要 Docker。實作見 DockerContainerRuntime（僅在
 * n3n.apps.enabled=true 時建立）。
 */
public interface ContainerRuntime {

    /** 確保專用 bridge network 存在（不存在時建立） */
    void ensureNetwork(String name);

    /** 拉取映像（含 tag；無 tag 視為 latest） */
    void pullImage(String image);

    /**
     * 以檔案集合為 build context 建置映像（實作端負責轉為 tar）。
     *
     * @param files 相對路徑 → 內容（必須含 Dockerfile）
     * @return image id
     */
    String buildImage(String imageTag, Map<String, byte[]> files, Map<String, String> labels);

    /** 建立容器（不啟動），回傳 container id */
    String createContainer(ContainerSpec spec);

    void startContainer(String containerId);

    void stopContainer(String containerId);

    void removeContainer(String containerId);

    /** 以 label 篩選容器（含已停止的），只會找到我們自己建立的容器 */
    List<String> findContainerIdsByLabel(String labelKey, String labelValue);

    /** 移除帶指定 label 的映像（僅清掉我們建置的，不碰其他映像） */
    void removeImagesByLabel(String labelKey, String labelValue);

    /** 回傳容器狀態字串（running / exited / ...），不存在時回 null */
    String inspectStatus(String containerId);

    /** 取容器最後 N 行 log（stdout + stderr） */
    String tailLogs(String containerId, int lines);
}
