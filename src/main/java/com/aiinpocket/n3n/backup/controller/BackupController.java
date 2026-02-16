package com.aiinpocket.n3n.backup.controller;

import com.aiinpocket.n3n.backup.dto.request.ListRemoteRequest;
import com.aiinpocket.n3n.backup.dto.request.RestoreBackupRequest;
import com.aiinpocket.n3n.backup.dto.request.UpdateBackupSettingsRequest;
import com.aiinpocket.n3n.backup.dto.response.*;
import com.aiinpocket.n3n.backup.service.BackupService;
import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Cloud Backup", description = "雲端加密備份管理")
public class BackupController {

    private final BackupService backupService;
    private final IpRateLimiter ipRateLimiter;

    @GetMapping("/settings")
    @Operation(summary = "取得備份設定")
    public ResponseEntity<BackupSettingsResponse> getSettings() {
        return ResponseEntity.ok(backupService.getSettings());
    }

    @PutMapping("/settings")
    @Operation(summary = "更新備份設定")
    public ResponseEntity<BackupSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateBackupSettingsRequest request) {
        return ResponseEntity.ok(backupService.updateSettings(request));
    }

    @PostMapping("/test-connection")
    @Operation(summary = "測試儲存連線")
    public ResponseEntity<TestConnectionResponse> testConnection(HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("backup-test", httpRequest.getRemoteAddr(), 10, 60);
        boolean success = backupService.testConnection();
        return ResponseEntity.ok(TestConnectionResponse.builder()
                .success(success)
                .message(success ? "Connection successful" : "Connection failed")
                .build());
    }

    @PostMapping("/create")
    @Operation(summary = "建立備份")
    public ResponseEntity<BackupHistoryResponse> createBackup(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("backup-create", httpRequest.getRemoteAddr(), 3, 60);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(backupService.createBackup(userId));
    }

    @GetMapping("/history")
    @Operation(summary = "備份歷史")
    public ResponseEntity<Page<BackupHistoryResponse>> getHistory(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(backupService.getHistory(pageable));
    }

    @PostMapping("/list-remote")
    @Operation(summary = "列出遠端備份（透過 Recovery Key fingerprint 過濾）")
    public ResponseEntity<List<RemoteBackupInfo>> listRemoteBackups(
            @Valid @RequestBody ListRemoteRequest request) {
        return ResponseEntity.ok(backupService.listRemoteBackups(request.getRecoveryKeyPhrase()));
    }

    @PostMapping("/restore")
    @Operation(summary = "還原備份")
    public ResponseEntity<RestoreBackupResponse> restoreBackup(
            @Valid @RequestBody RestoreBackupRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        ipRateLimiter.checkAllowed("backup-restore", httpRequest.getRemoteAddr(), 2, 60);
        UUID userId = UUID.fromString(userDetails.getUsername());
        backupService.restoreBackup(
                request.getRecoveryKeyPhrase(),
                request.getFilename(),
                userId
        );
        return ResponseEntity.ok(RestoreBackupResponse.builder()
                .message("Backup restored successfully")
                .build());
    }
}
