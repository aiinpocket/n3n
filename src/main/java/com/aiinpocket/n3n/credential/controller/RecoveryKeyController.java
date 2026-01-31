package com.aiinpocket.n3n.credential.controller;

import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Recovery Key API Controller
 *
 * 處理 Recovery Key 的備份確認、遷移和緊急還原
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
@Slf4j
public class RecoveryKeyController {

    private final RecoveryKeyService recoveryKeyService;
    private final MasterKeyProvider masterKeyProvider;

    /**
     * 取得加密系統狀態
     */
    @GetMapping("/status")
    public ResponseEntity<SecurityStatusResponse> getSecurityStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        SecurityStatusResponse status = SecurityStatusResponse.builder()
                .needsRecoveryKeySetup(masterKeyProvider.needsRecoveryKeySetup())
                .keyMismatch(masterKeyProvider.isKeyMismatch())
                .currentKeyVersion(masterKeyProvider.getCurrentKeyVersion())
                .build();

        log.debug("Security status requested by user: {}", userId);
        return ResponseEntity.ok(status);
    }

    /**
     * 確認已備份 Recovery Key
     */
    @PostMapping("/recovery-key/confirm")
    public ResponseEntity<Void> confirmRecoveryKeyBackup(
            @Valid @RequestBody ConfirmBackupRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        // 驗證用戶輸入的 Recovery Key 是否正確
        if (!recoveryKeyService.validate(request.getRecoveryKeyPhrase())) {
            return ResponseEntity.badRequest().build();
        }

        // 確認備份
        masterKeyProvider.confirmRecoveryKeyBackup(userId, request.getRecoveryKeyPhrase());

        log.info("Recovery key backup confirmed by user: {}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 使用舊 Recovery Key 遷移單一憑證
     */
    @PostMapping("/migrate")
    public ResponseEntity<MigrateResponse> migrateCredential(
            @Valid @RequestBody MigrateCredentialRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        try {
            masterKeyProvider.migrateWithRecoveryKey(
                    request.getOldRecoveryKeyPhrase(),
                    request.getCredentialId(),
                    userId
            );

            log.info("Credential migrated: credentialId={}, userId={}", request.getCredentialId(), userId);
            return ResponseEntity.ok(MigrateResponse.builder()
                    .success(true)
                    .message("憑證遷移成功")
                    .build());

        } catch (Exception e) {
            log.error("Credential migration failed: {}", e.getMessage());
            return ResponseEntity.ok(MigrateResponse.builder()
                    .success(false)
                    .message("遷移失敗：" + e.getMessage())
                    .build());
        }
    }

    /**
     * 緊急還原（需要 Recovery Key + 永久密碼）
     */
    @PostMapping("/emergency-restore")
    public ResponseEntity<MigrateResponse> emergencyRestore(
            @Valid @RequestBody EmergencyRestoreRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        try {
            // 驗證 Recovery Key 格式
            if (!recoveryKeyService.validate(request.getRecoveryKeyPhrase())) {
                return ResponseEntity.badRequest().body(MigrateResponse.builder()
                        .success(false)
                        .message("Recovery Key 格式無效")
                        .build());
            }

            // 執行緊急還原
            RecoveryKey newRecoveryKey = masterKeyProvider.emergencyRestore(
                    request.getRecoveryKeyPhrase(),
                    request.getPermanentPassword(),
                    userId
            );

            log.warn("Emergency restore performed by user: {}", userId);
            return ResponseEntity.ok(MigrateResponse.builder()
                    .success(true)
                    .message("緊急還原成功，請備份新的 Recovery Key")
                    .newRecoveryKey(newRecoveryKey)
                    .build());

        } catch (Exception e) {
            log.error("Emergency restore failed: {}", e.getMessage());
            return ResponseEntity.ok(MigrateResponse.builder()
                    .success(false)
                    .message("還原失敗：" + e.getMessage())
                    .build());
        }
    }

    // ==================== Request/Response DTOs ====================

    @Data
    @lombok.Builder
    public static class SecurityStatusResponse {
        private boolean needsRecoveryKeySetup;
        private boolean keyMismatch;
        private Integer currentKeyVersion;
    }

    @Data
    public static class ConfirmBackupRequest {
        @NotBlank(message = "Recovery Key 不能為空")
        private String recoveryKeyPhrase;
    }

    @Data
    public static class MigrateCredentialRequest {
        @NotBlank(message = "舊 Recovery Key 不能為空")
        private String oldRecoveryKeyPhrase;

        private UUID credentialId;
    }

    @Data
    public static class EmergencyRestoreRequest {
        @NotBlank(message = "Recovery Key 不能為空")
        private String recoveryKeyPhrase;

        @NotBlank(message = "永久密碼不能為空")
        private String permanentPassword;
    }

    @Data
    @lombok.Builder
    public static class MigrateResponse {
        private boolean success;
        private String message;
        private RecoveryKey newRecoveryKey;
    }
}
