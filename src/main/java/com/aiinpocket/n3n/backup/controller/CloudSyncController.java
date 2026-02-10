package com.aiinpocket.n3n.backup.controller;

import com.aiinpocket.n3n.backup.dto.request.ImportSyncRequest;
import com.aiinpocket.n3n.backup.dto.request.ScanRemoteRequest;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncImportResult;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncManifest;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncStatus;
import com.aiinpocket.n3n.backup.service.CloudSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 雲端即時同步 API
 */
@RestController
@RequestMapping("/api/cloud-sync")
@RequiredArgsConstructor
@Tag(name = "Cloud Sync", description = "雲端即時同步與跨實例匯入")
public class CloudSyncController {

    private final CloudSyncService cloudSyncService;

    @PostMapping("/scan")
    @Operation(summary = "掃描遠端同步資料")
    public ResponseEntity<CloudSyncManifest> scan(
            @Valid @RequestBody ScanRemoteRequest request) {
        return ResponseEntity.ok(
                cloudSyncService.listRemoteEntities(request.getRecoveryKeyPhrase()));
    }

    @PostMapping("/import")
    @Operation(summary = "匯入遠端資料並重新加密")
    public ResponseEntity<CloudSyncImportResult> importEntities(
            @Valid @RequestBody ImportSyncRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(
                cloudSyncService.importFromRecoveryKey(
                        request.getRecoveryKeyPhrase(), userId));
    }

    @GetMapping("/status")
    @Operation(summary = "取得雲端同步狀態")
    public ResponseEntity<CloudSyncStatus> getStatus() {
        return ResponseEntity.ok(cloudSyncService.getSyncStatus());
    }
}
