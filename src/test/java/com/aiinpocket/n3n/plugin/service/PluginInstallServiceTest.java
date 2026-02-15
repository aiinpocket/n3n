package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.plugin.entity.*;
import com.aiinpocket.n3n.plugin.orchestrator.ContainerOrchestrator;
import com.aiinpocket.n3n.plugin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PluginInstallServiceTest extends BaseServiceTest {

    @Mock private PluginInstallTaskRepository taskRepository;
    @Mock private PluginRepository pluginRepository;
    @Mock private PluginVersionRepository pluginVersionRepository;
    @Mock private PluginService pluginService;
    @Mock private ContainerOrchestrator containerOrchestrator;
    @Mock private PluginNotificationService notificationService;
    @Mock private ContainerNodeDefinitionFetcher nodeDefinitionFetcher;

    private PluginInstallService installService;

    private final UUID userId = UUID.randomUUID();
    private final UUID pluginId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        installService = new PluginInstallService(
                taskRepository,
                pluginRepository,
                pluginVersionRepository,
                pluginService,
                containerOrchestrator,
                notificationService,
                nodeDefinitionFetcher
        );
    }

    // ==================== getTaskStatus ====================

    @Test
    void getTaskStatus_shouldReturnTask() {
        PluginInstallTask task = sampleTask();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        PluginInstallTask result = installService.getTaskStatus(taskId);

        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getNodeType()).isEqualTo("test-node");
    }

    @Test
    void getTaskStatus_shouldThrowWhenNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installService.getTaskStatus(taskId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(taskId.toString());
    }

    // ==================== getActiveTasks ====================

    @Test
    void getActiveTasks_shouldReturnActive() {
        PluginInstallTask task = sampleTask();
        when(taskRepository.findActiveTasksByUserId(userId)).thenReturn(List.of(task));

        List<PluginInstallTask> result = installService.getActiveTasks(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeType()).isEqualTo("test-node");
    }

    @Test
    void getActiveTasks_shouldReturnEmptyWhenNone() {
        when(taskRepository.findActiveTasksByUserId(userId)).thenReturn(List.of());

        List<PluginInstallTask> result = installService.getActiveTasks(userId);

        assertThat(result).isEmpty();
    }

    // ==================== cancelTask ====================

    @Test
    void cancelTask_shouldCancelPendingTask() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        installService.cancelTask(taskId, userId);

        assertThat(task.getStatus()).isEqualTo(PluginInstallTask.InstallStatus.CANCELLED);
        verify(taskRepository).save(task);
        verify(notificationService).notifyTaskCancelled(task);
    }

    @Test
    void cancelTask_shouldStopContainerIfRunning() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.PULLING);
        task.setContainerId("container-123");
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        installService.cancelTask(taskId, userId);

        verify(containerOrchestrator).stop("container-123");
        verify(notificationService).notifyTaskCancelled(task);
    }

    @Test
    void cancelTask_shouldNotStopContainerIfNoContainer() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        task.setContainerId(null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        installService.cancelTask(taskId, userId);

        verify(containerOrchestrator, never()).stop(any());
    }

    @Test
    void cancelTask_shouldThrowWhenNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installService.cancelTask(taskId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(taskId.toString());
    }

    @Test
    void cancelTask_shouldThrowWhenNotOwner() {
        PluginInstallTask task = sampleTask();
        UUID otherUser = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> installService.cancelTask(taskId, otherUser))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Not authorized to cancel this task");
    }

    @Test
    void cancelTask_shouldThrowWhenAlreadyCompleted() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.COMPLETED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> installService.cancelTask(taskId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Task already completed");
    }

    @Test
    void cancelTask_shouldThrowWhenAlreadyFailed() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.FAILED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> installService.cancelTask(taskId, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelTask_shouldThrowWhenAlreadyCancelled() {
        PluginInstallTask task = sampleTask();
        task.setStatus(PluginInstallTask.InstallStatus.CANCELLED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> installService.cancelTask(taskId, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==================== installPlugin ====================

    @Test
    void installPlugin_shouldCreateTaskForLatestVersion() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(version));
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(taskId);
            return t;
        });

        UUID result = installService.installPlugin(pluginId, userId, null);

        assertThat(result).isEqualTo(taskId);
        verify(taskRepository).save(argThat(t ->
                t.getPluginId().equals(pluginId) &&
                t.getUserId().equals(userId) &&
                t.getStatus() == PluginInstallTask.InstallStatus.PENDING));
    }

    @Test
    void installPlugin_shouldCreateTaskForSpecificVersion() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        version.setVersion("0.9.0");
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginVersionRepository.findByPluginIdAndVersion(pluginId, "0.9.0")).thenReturn(Optional.of(version));
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(taskId);
            return t;
        });

        UUID result = installService.installPlugin(pluginId, userId, "0.9.0");

        assertThat(result).isEqualTo(taskId);
    }

    @Test
    void installPlugin_shouldThrowWhenPluginNotFound() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installService.installPlugin(pluginId, userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(pluginId.toString());
    }

    @Test
    void installPlugin_shouldThrowWhenVersionNotFound() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginVersionRepository.findByPluginIdAndVersion(pluginId, "99.0.0")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installService.installPlugin(pluginId, userId, "99.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99.0.0");
    }

    @Test
    void installPlugin_shouldThrowWhenNoVersionAvailable() {
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(samplePlugin()));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installService.installPlugin(pluginId, userId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No version available");
    }

    @Test
    void installPlugin_shouldExtractNodeTypeFromNodeDefinitions() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        version.setNodeDefinitions(Map.of(
                "nodes", List.of(Map.of("type", "custom-node-type"))
        ));
        when(pluginRepository.findById(pluginId)).thenReturn(Optional.of(plugin));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(version));
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(taskId);
            return t;
        });

        installService.installPlugin(pluginId, userId, null);

        verify(taskRepository).save(argThat(t ->
                "custom-node-type".equals(t.getNodeType())));
    }

    // ==================== installMissingNodes ====================

    @Test
    void installMissingNodes_shouldSkipAlreadyInstalling() {
        when(taskRepository.existsActiveTaskForNodeType(userId, "test-node")).thenReturn(true);

        List<UUID> result = installService.installMissingNodes(List.of("test-node"), userId);

        assertThat(result).isEmpty();
        verify(taskRepository, never()).save(any());
    }

    @Test
    void installMissingNodes_shouldSkipUnresolvableNodeType() {
        when(taskRepository.existsActiveTaskForNodeType(userId, "unknown-type")).thenReturn(false);
        when(pluginRepository.findAll()).thenReturn(List.of());

        List<UUID> result = installService.installMissingNodes(List.of("unknown-type"), userId);

        assertThat(result).isEmpty();
    }

    @Test
    void installMissingNodes_shouldResolveBuiltinPlugin() {
        Plugin plugin = samplePlugin();
        PluginVersion version = sampleVersion();
        version.setNodeDefinitions(Map.of(
                "nodes", List.of(Map.of("type", "test-node"))
        ));

        when(taskRepository.existsActiveTaskForNodeType(userId, "test-node")).thenReturn(false);
        when(pluginRepository.findAll()).thenReturn(List.of(plugin));
        when(pluginVersionRepository.findLatestByPluginId(pluginId)).thenReturn(Optional.of(version));
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(taskId);
            return t;
        });

        List<UUID> result = installService.installMissingNodes(List.of("test-node"), userId);

        assertThat(result).hasSize(1);
        verify(taskRepository).save(argThat(t ->
                t.getSource() == PluginInstallTask.InstallSource.BUILTIN));
    }

    @Test
    void installMissingNodes_shouldResolveDockerImage() {
        when(taskRepository.existsActiveTaskForNodeType(userId, "puppeteer")).thenReturn(false);
        when(pluginRepository.findAll()).thenReturn(List.of());
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(taskId);
            return t;
        });

        List<UUID> result = installService.installMissingNodes(List.of("puppeteer"), userId);

        assertThat(result).hasSize(1);
        verify(taskRepository).save(argThat(t ->
                t.getSource() == PluginInstallTask.InstallSource.DOCKER_HUB &&
                t.getSourceReference().contains("puppeteer")));
    }

    @Test
    void installMissingNodes_shouldHandleMultipleNodeTypes() {
        when(taskRepository.existsActiveTaskForNodeType(eq(userId), any())).thenReturn(false);
        when(pluginRepository.findAll()).thenReturn(List.of());
        when(taskRepository.save(any(PluginInstallTask.class))).thenAnswer(inv -> {
            PluginInstallTask t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        List<UUID> result = installService.installMissingNodes(
                List.of("ffmpeg", "tesseract"), userId);

        assertThat(result).hasSize(2);
        verify(taskRepository, times(2)).save(any(PluginInstallTask.class));
    }

    // ==================== PluginInstallTask entity tests ====================

    @Test
    void pluginInstallTask_isTerminal_shouldReturnTrueForCompletedStates() {
        PluginInstallTask task = new PluginInstallTask();

        task.setStatus(PluginInstallTask.InstallStatus.COMPLETED);
        assertThat(task.isTerminal()).isTrue();

        task.setStatus(PluginInstallTask.InstallStatus.FAILED);
        assertThat(task.isTerminal()).isTrue();

        task.setStatus(PluginInstallTask.InstallStatus.CANCELLED);
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void pluginInstallTask_isTerminal_shouldReturnFalseForActiveStates() {
        PluginInstallTask task = new PluginInstallTask();

        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        assertThat(task.isTerminal()).isFalse();

        task.setStatus(PluginInstallTask.InstallStatus.PULLING);
        assertThat(task.isTerminal()).isFalse();

        task.setStatus(PluginInstallTask.InstallStatus.STARTING);
        assertThat(task.isTerminal()).isFalse();

        task.setStatus(PluginInstallTask.InstallStatus.REGISTERING);
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    void pluginInstallTask_markCompleted_shouldSetFieldsCorrectly() {
        PluginInstallTask task = new PluginInstallTask();
        task.setStatus(PluginInstallTask.InstallStatus.REGISTERING);

        task.markCompleted();

        assertThat(task.getStatus()).isEqualTo(PluginInstallTask.InstallStatus.COMPLETED);
        assertThat(task.getProgressPercent()).isEqualTo(100);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void pluginInstallTask_markFailed_shouldSetFieldsCorrectly() {
        PluginInstallTask task = new PluginInstallTask();
        task.setStatus(PluginInstallTask.InstallStatus.PULLING);

        task.markFailed("Connection timeout");

        assertThat(task.getStatus()).isEqualTo(PluginInstallTask.InstallStatus.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void pluginInstallTask_updateProgress_shouldSetStartedAt() {
        PluginInstallTask task = new PluginInstallTask();
        assertThat(task.getStartedAt()).isNull();

        task.updateProgress(10, "Pulling image");

        assertThat(task.getProgressPercent()).isEqualTo(10);
        assertThat(task.getCurrentStage()).isEqualTo("Pulling image");
        assertThat(task.getStartedAt()).isNotNull();
    }

    @Test
    void pluginInstallTask_updateProgress_shouldNotOverwriteStartedAt() {
        PluginInstallTask task = new PluginInstallTask();
        task.updateProgress(10, "Step 1");
        var firstStart = task.getStartedAt();

        task.updateProgress(50, "Step 2");

        assertThat(task.getStartedAt()).isEqualTo(firstStart);
    }

    // ==================== Helper methods ====================

    private PluginInstallTask sampleTask() {
        PluginInstallTask task = new PluginInstallTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setPluginId(pluginId);
        task.setNodeType("test-node");
        task.setSource(PluginInstallTask.InstallSource.BUILTIN);
        task.setStatus(PluginInstallTask.InstallStatus.PENDING);
        return task;
    }

    private Plugin samplePlugin() {
        Plugin p = new Plugin();
        p.setId(pluginId);
        p.setName("test-plugin");
        p.setDisplayName("Test Plugin");
        p.setDescription("A test plugin");
        p.setCategory("ai");
        p.setAuthor("Test");
        return p;
    }

    private PluginVersion sampleVersion() {
        PluginVersion v = new PluginVersion();
        v.setId(versionId);
        v.setPluginId(pluginId);
        v.setVersion("1.0.0");
        v.setConfigSchema(Map.of());
        v.setNodeDefinitions(Map.of());
        return v;
    }
}
