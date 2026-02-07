package com.aiinpocket.n3n.plugin.controller;

import com.aiinpocket.n3n.plugin.entity.PluginInstallTask;
import com.aiinpocket.n3n.plugin.service.PluginInstallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin 安裝控制器
 * 處理 Plugin 自動安裝相關 API
 */
@Slf4j
@RestController
@RequestMapping("/api/plugins/install")
@RequiredArgsConstructor
@Tag(name = "Plugin Installation", description = "Plugin installation management")
public class PluginInstallController {

    private final PluginInstallService pluginInstallService;

    /**
     * 批量安裝缺失的節點類型
     *
     * POST /api/plugins/install/missing
     * Body: { "nodeTypes": ["puppeteer", "ffmpeg", ...] }
     */
    @PostMapping("/missing")
    public ResponseEntity<Map<String, Object>> installMissingNodes(
            @Valid @RequestBody InstallMissingRequest request,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        log.info("Installing missing nodes for user {}: {}", userId, request.nodeTypes());

        List<UUID> taskIds = pluginInstallService.installMissingNodes(request.nodeTypes(), userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "開始安裝 " + taskIds.size() + " 個元件",
            "taskIds", taskIds
        ));
    }

    /**
     * 安裝單一 Plugin
     *
     * POST /api/plugins/install/{pluginId}
     * Body: { "version": "1.0.0" } (optional)
     */
    @PostMapping("/{pluginId}")
    public ResponseEntity<Map<String, Object>> installPlugin(
            @PathVariable UUID pluginId,
            @RequestBody(required = false) InstallPluginVersionRequest request,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        String version = request != null ? request.version() : null;

        log.info("Installing plugin {} version {} for user {}", pluginId, version, userId);

        UUID taskId = pluginInstallService.installPlugin(pluginId, userId, version);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "開始安裝 Plugin",
            "taskId", taskId
        ));
    }

    /**
     * 取得安裝任務狀態
     *
     * GET /api/plugins/install/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<PluginInstallTaskDto> getTaskStatus(
            @PathVariable UUID taskId,
            Authentication authentication) {

        PluginInstallTask task = pluginInstallService.getTaskStatus(taskId);

        // 驗證權限
        UUID userId = getUserId(authentication);
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toDto(task));
    }

    /**
     * 取得使用者進行中的安裝任務
     *
     * GET /api/plugins/install/tasks
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<PluginInstallTaskDto>> getActiveTasks(Authentication authentication) {
        UUID userId = getUserId(authentication);
        List<PluginInstallTask> tasks = pluginInstallService.getActiveTasks(userId);
        return ResponseEntity.ok(tasks.stream().map(this::toDto).toList());
    }

    /**
     * 取消安裝任務
     *
     * DELETE /api/plugins/install/tasks/{taskId}
     */
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(
            @PathVariable UUID taskId,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        pluginInstallService.cancelTask(taskId, userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "安裝任務已取消"
        ));
    }

    private UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new SecurityException("Authentication required");
        }
        // 假設 principal 是 UUID 或可轉換為 UUID
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        return UUID.fromString(principal.toString());
    }

    private PluginInstallTaskDto toDto(PluginInstallTask task) {
        return new PluginInstallTaskDto(
            task.getId(),
            task.getPluginId(),
            task.getNodeType(),
            task.getSource().name(),
            task.getSourceReference(),
            task.getStatus().name(),
            task.getProgressPercent(),
            task.getCurrentStage(),
            task.getErrorMessage(),
            task.getContainerId(),
            task.getContainerPort(),
            task.getCreatedAt(),
            task.getStartedAt(),
            task.getCompletedAt()
        );
    }

    // Request DTOs
    public record InstallMissingRequest(List<String> nodeTypes) {}
    public record InstallPluginVersionRequest(String version) {}

    // Response DTO
    public record PluginInstallTaskDto(
        UUID id,
        UUID pluginId,
        String nodeType,
        String source,
        String sourceReference,
        String status,
        Integer progressPercent,
        String currentStage,
        String errorMessage,
        String containerId,
        Integer containerPort,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime startedAt,
        java.time.LocalDateTime completedAt
    ) {}
}
