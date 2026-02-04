package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.plugin.entity.PluginInstallTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Plugin 安裝通知服務
 * 透過 WebSocket 推送安裝進度給前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_PREFIX = "/topic/users/";
    private static final String PLUGIN_INSTALL_TOPIC = "/plugin-install";

    /**
     * 通知安裝進度
     */
    public void notifyTaskProgress(PluginInstallTask task) {
        sendNotification(task, "progress");
    }

    /**
     * 通知安裝完成
     */
    public void notifyTaskCompleted(PluginInstallTask task) {
        sendNotification(task, "completed");
    }

    /**
     * 通知安裝失敗
     */
    public void notifyTaskFailed(PluginInstallTask task) {
        sendNotification(task, "failed");
    }

    /**
     * 通知安裝取消
     */
    public void notifyTaskCancelled(PluginInstallTask task) {
        sendNotification(task, "cancelled");
    }

    /**
     * 發送通知
     */
    private void sendNotification(PluginInstallTask task, String eventType) {
        String destination = TOPIC_PREFIX + task.getUserId() + PLUGIN_INSTALL_TOPIC;

        Map<String, Object> payload = Map.of(
            "type", eventType,
            "taskId", task.getId().toString(),
            "nodeType", task.getNodeType(),
            "status", task.getStatus().name(),
            "progress", task.getProgressPercent() != null ? task.getProgressPercent() : 0,
            "stage", task.getCurrentStage() != null ? task.getCurrentStage() : "",
            "error", task.getErrorMessage() != null ? task.getErrorMessage() : "",
            "containerId", task.getContainerId() != null ? task.getContainerId() : "",
            "containerPort", task.getContainerPort() != null ? task.getContainerPort() : 0
        );

        try {
            messagingTemplate.convertAndSend(destination, (Object) payload);
            log.debug("Sent {} notification for task {} to {}", eventType, task.getId(), destination);
        } catch (Exception e) {
            log.warn("Failed to send notification: {}", e.getMessage());
        }
    }
}
