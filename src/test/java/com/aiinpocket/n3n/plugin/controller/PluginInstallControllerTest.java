package com.aiinpocket.n3n.plugin.controller;

import com.aiinpocket.n3n.plugin.entity.PluginInstallTask;
import com.aiinpocket.n3n.plugin.service.PluginInstallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginInstallControllerTest {

    @Mock
    private PluginInstallService pluginInstallService;

    @InjectMocks
    private PluginInstallController controller;

    private final UUID userId = UUID.randomUUID();

    private Authentication testAuth() {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    }

    private PluginInstallTask sampleTask() {
        var task = new PluginInstallTask();
        task.setId(UUID.randomUUID());
        task.setUserId(userId);
        task.setPluginId(UUID.randomUUID());
        task.setNodeType("puppeteer");
        task.setSource(PluginInstallTask.InstallSource.DOCKER_HUB);
        task.setSourceReference("n3n/puppeteer-plugin:latest");
        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        task.setProgressPercent(0);
        task.setCurrentStage("Pending installation");
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }

    // ========== installMissingNodes ==========

    @Test
    void installMissingNodes_success_returnsTaskIds() {
        var taskId1 = UUID.randomUUID();
        var taskId2 = UUID.randomUUID();
        var nodeTypes = List.of("puppeteer", "ffmpeg");

        when(pluginInstallService.installMissingNodes(nodeTypes, userId))
                .thenReturn(List.of(taskId1, taskId2));

        var request = new PluginInstallController.InstallMissingRequest(nodeTypes);
        var result = controller.installMissingNodes(request, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        assertThat(result.getBody().get("message")).isEqualTo("Installing 2 components");
        @SuppressWarnings("unchecked")
        List<UUID> taskIds = (List<UUID>) result.getBody().get("taskIds");
        assertThat(taskIds).containsExactly(taskId1, taskId2);
    }

    @Test
    void installMissingNodes_empty_returnsZeroTasks() {
        when(pluginInstallService.installMissingNodes(List.of("unknown"), userId))
                .thenReturn(List.of());

        var request = new PluginInstallController.InstallMissingRequest(List.of("unknown"));
        var result = controller.installMissingNodes(request, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("message")).isEqualTo("Installing 0 components");
    }

    @Test
    void installMissingNodes_singleNode_returnsOneTask() {
        var taskId = UUID.randomUUID();
        when(pluginInstallService.installMissingNodes(List.of("selenium"), userId))
                .thenReturn(List.of(taskId));

        var request = new PluginInstallController.InstallMissingRequest(List.of("selenium"));
        var result = controller.installMissingNodes(request, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("message")).isEqualTo("Installing 1 components");
    }

    // ========== installPlugin ==========

    @Test
    void installPlugin_withVersion_returnsTaskId() {
        var pluginId = UUID.randomUUID();
        var taskId = UUID.randomUUID();
        when(pluginInstallService.installPlugin(pluginId, userId, "1.0.0")).thenReturn(taskId);

        var request = new PluginInstallController.InstallPluginVersionRequest("1.0.0");
        var result = controller.installPlugin(pluginId, request, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        assertThat(result.getBody().get("message")).isEqualTo("Installing plugin");
        assertThat(result.getBody().get("taskId")).isEqualTo(taskId);
    }

    @Test
    void installPlugin_withoutVersion_passesNull() {
        var pluginId = UUID.randomUUID();
        var taskId = UUID.randomUUID();
        when(pluginInstallService.installPlugin(pluginId, userId, null)).thenReturn(taskId);

        var result = controller.installPlugin(pluginId, null, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pluginInstallService).installPlugin(pluginId, userId, null);
    }

    @Test
    void installPlugin_pluginNotFound_throwsException() {
        var pluginId = UUID.randomUUID();
        when(pluginInstallService.installPlugin(pluginId, userId, null))
                .thenThrow(new IllegalArgumentException("Plugin not found: " + pluginId));

        assertThatThrownBy(() -> controller.installPlugin(pluginId, null, testAuth()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plugin not found");
    }

    @Test
    void installPlugin_versionNotFound_throwsException() {
        var pluginId = UUID.randomUUID();
        when(pluginInstallService.installPlugin(pluginId, userId, "99.0.0"))
                .thenThrow(new IllegalArgumentException("Version not found: 99.0.0"));

        var request = new PluginInstallController.InstallPluginVersionRequest("99.0.0");
        assertThatThrownBy(() -> controller.installPlugin(pluginId, request, testAuth()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version not found");
    }

    // ========== getTaskStatus ==========

    @Test
    void getTaskStatus_ownTask_returnsDto() {
        var task = sampleTask();
        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(task.getId());
        assertThat(result.getBody().nodeType()).isEqualTo("puppeteer");
        assertThat(result.getBody().source()).isEqualTo("DOCKER_HUB");
        assertThat(result.getBody().sourceReference()).isEqualTo("n3n/puppeteer-plugin:latest");
        assertThat(result.getBody().status()).isEqualTo("PENDING");
        assertThat(result.getBody().progressPercent()).isEqualTo(0);
        assertThat(result.getBody().currentStage()).isEqualTo("Pending installation");
    }

    @Test
    void getTaskStatus_notOwnTask_returnsNotFound() {
        var task = sampleTask();
        task.setUserId(UUID.randomUUID()); // different user
        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTaskStatus_taskNotFound_throwsException() {
        var taskId = UUID.randomUUID();
        when(pluginInstallService.getTaskStatus(taskId))
                .thenThrow(new IllegalArgumentException("Task not found: " + taskId));

        assertThatThrownBy(() -> controller.getTaskStatus(taskId, testAuth()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task not found");
    }

    @Test
    void getTaskStatus_completedTask_returnsCompleted() {
        var task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.COMPLETED);
        task.setProgressPercent(100);
        task.setCurrentStage("Completed");
        task.setStartedAt(LocalDateTime.now().minusMinutes(5));
        task.setCompletedAt(LocalDateTime.now());
        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("COMPLETED");
        assertThat(result.getBody().progressPercent()).isEqualTo(100);
        assertThat(result.getBody().startedAt()).isNotNull();
        assertThat(result.getBody().completedAt()).isNotNull();
    }

    @Test
    void getTaskStatus_failedTask_returnsError() {
        var task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.FAILED);
        task.setErrorMessage("Container failed to start");
        task.setCompletedAt(LocalDateTime.now());
        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("FAILED");
        assertThat(result.getBody().errorMessage()).isEqualTo("Container failed to start");
    }

    @Test
    void getTaskStatus_runningTask_returnsProgress() {
        var task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.PULLING);
        task.setProgressPercent(45);
        task.setCurrentStage("Pulling image: n3n/puppeteer-plugin:latest");
        task.setContainerId("container-abc");
        task.setContainerPort(8081);
        task.setStartedAt(LocalDateTime.now().minusMinutes(2));
        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("PULLING");
        assertThat(result.getBody().progressPercent()).isEqualTo(45);
        assertThat(result.getBody().containerId()).isEqualTo("container-abc");
        assertThat(result.getBody().containerPort()).isEqualTo(8081);
    }

    // ========== getActiveTasks ==========

    @Test
    void getActiveTasks_returnsTasks() {
        var task1 = sampleTask();
        task1.setNodeType("puppeteer");
        var task2 = sampleTask();
        task2.setNodeType("ffmpeg");
        task2.setSource(PluginInstallTask.InstallSource.BUILTIN);

        when(pluginInstallService.getActiveTasks(userId)).thenReturn(List.of(task1, task2));

        var result = controller.getActiveTasks(testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).nodeType()).isEqualTo("puppeteer");
        assertThat(result.getBody().get(1).nodeType()).isEqualTo("ffmpeg");
        assertThat(result.getBody().get(1).source()).isEqualTo("BUILTIN");
    }

    @Test
    void getActiveTasks_noActive_returnsEmptyList() {
        when(pluginInstallService.getActiveTasks(userId)).thenReturn(List.of());

        var result = controller.getActiveTasks(testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    // ========== cancelTask ==========

    @Test
    void cancelTask_success_returnsNoContent() {
        var taskId = UUID.randomUUID();
        doNothing().when(pluginInstallService).cancelTask(taskId, userId);

        var result = controller.cancelTask(taskId, testAuth());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pluginInstallService).cancelTask(taskId, userId);
    }

    @Test
    void cancelTask_notOwned_throwsSecurityException() {
        var taskId = UUID.randomUUID();
        doThrow(new SecurityException("Not authorized to cancel this task"))
                .when(pluginInstallService).cancelTask(taskId, userId);

        assertThatThrownBy(() -> controller.cancelTask(taskId, testAuth()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void cancelTask_alreadyCompleted_throwsException() {
        var taskId = UUID.randomUUID();
        doThrow(new IllegalStateException("Task already completed"))
                .when(pluginInstallService).cancelTask(taskId, userId);

        assertThatThrownBy(() -> controller.cancelTask(taskId, testAuth()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task already completed");
    }

    @Test
    void cancelTask_taskNotFound_throwsException() {
        var taskId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Task not found: " + taskId))
                .when(pluginInstallService).cancelTask(taskId, userId);

        assertThatThrownBy(() -> controller.cancelTask(taskId, testAuth()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task not found");
    }

    // ========== Authentication handling ==========

    @Test
    void installMissingNodes_nullAuth_throwsSecurityException() {
        var request = new PluginInstallController.InstallMissingRequest(List.of("puppeteer"));

        assertThatThrownBy(() -> controller.installMissingNodes(request, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void getActiveTasks_uuidPrincipal_works() {
        // Test with UUID principal directly
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        when(pluginInstallService.getActiveTasks(userId)).thenReturn(List.of());

        var result = controller.getActiveTasks(auth);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ========== DTO mapping ==========

    @Test
    void toDto_mapsAllFields() {
        var task = new PluginInstallTask();
        task.setId(UUID.randomUUID());
        task.setPluginId(UUID.randomUUID());
        task.setUserId(userId);
        task.setNodeType("tesseract");
        task.setSource(PluginInstallTask.InstallSource.DOCKER_HUB);
        task.setSourceReference("n3n/tesseract:1.0");
        task.setStatus(PluginInstallTask.InstallStatus.REGISTERING);
        task.setProgressPercent(85);
        task.setCurrentStage("Registering node");
        task.setErrorMessage(null);
        task.setContainerId("cnt-123");
        task.setContainerPort(9090);
        task.setCreatedAt(LocalDateTime.of(2026, 2, 15, 10, 0));
        task.setStartedAt(LocalDateTime.of(2026, 2, 15, 10, 1));
        task.setCompletedAt(null);

        when(pluginInstallService.getTaskStatus(task.getId())).thenReturn(task);

        var result = controller.getTaskStatus(task.getId(), testAuth());

        assertThat(result.getBody()).isNotNull();
        var dto = result.getBody();
        assertThat(dto.id()).isEqualTo(task.getId());
        assertThat(dto.pluginId()).isEqualTo(task.getPluginId());
        assertThat(dto.nodeType()).isEqualTo("tesseract");
        assertThat(dto.source()).isEqualTo("DOCKER_HUB");
        assertThat(dto.sourceReference()).isEqualTo("n3n/tesseract:1.0");
        assertThat(dto.status()).isEqualTo("REGISTERING");
        assertThat(dto.progressPercent()).isEqualTo(85);
        assertThat(dto.currentStage()).isEqualTo("Registering node");
        assertThat(dto.errorMessage()).isNull();
        assertThat(dto.containerId()).isEqualTo("cnt-123");
        assertThat(dto.containerPort()).isEqualTo(9090);
        assertThat(dto.createdAt()).isEqualTo(LocalDateTime.of(2026, 2, 15, 10, 0));
        assertThat(dto.startedAt()).isEqualTo(LocalDateTime.of(2026, 2, 15, 10, 1));
        assertThat(dto.completedAt()).isNull();
    }
}
