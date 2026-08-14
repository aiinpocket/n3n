package com.aiinpocket.n3n.hostedapp.controller;

import com.aiinpocket.n3n.hostedapp.dto.AppDeployRequest;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.HostedAppResponse;
import com.aiinpocket.n3n.hostedapp.service.HostedAppService;
import com.aiinpocket.n3n.site.service.SiteDomains;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosted Apps（沙盒動態應用）管理 API。皆需登入、僅能操作自己的應用；
 * 除 /availability 外全部端點在功能關閉（n3n.apps.enabled=false）時回 404
 * （由 HostedAppService.requireEnabled 統一處理）。
 */
@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
@Tag(name = "Hosted Apps", description = "Sandboxed dynamic app hosting (Docker)")
public class HostedAppController {

    private final HostedAppService appService;
    private final SiteDomains siteDomains;

    /**
     * 功能可用性查詢（永遠可用，UI 據此隱藏入口）。
     * hostSuffix 供前端組出 {slug}{hostSuffix} 網址（含 "." 或 "-" 分隔符）；
     * 未設定時為 null，前端退回 http://{host}:{hostPort}。
     * baseDomain 保留給舊版前端相容。
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> availability() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", appService.isEnabled());
        body.put("baseDomain", siteDomains.isConfigured() ? siteDomains.baseDomain() : null);
        body.put("hostSuffix", siteDomains.isConfigured() ? siteDomains.hostSuffix() : null);
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public ResponseEntity<List<HostedAppResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appService.list(userId(userDetails)).stream()
                .map(HostedAppResponse::from)
                .toList());
    }

    /** 僅分析 zip（不持久化），回傳 manifest 供 UI 渲染參數表單 */
    @PostMapping("/analyze")
    public ResponseEntity<AppManifest> analyze(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        userId(userDetails);
        return ResponseEntity.ok(appService.analyze(zipBytes(file)));
    }

    /** 建立應用：上傳 zip + 名稱，回傳含 manifest 的應用資料 */
    @PostMapping
    public ResponseEntity<HostedAppResponse> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(HostedAppResponse.from(
                appService.create(userId(userDetails), name, zipBytes(file))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HostedAppResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(HostedAppResponse.from(
                appService.getOwned(id, userId(userDetails))));
    }

    @PostMapping("/{id}/deploy")
    public ResponseEntity<HostedAppResponse> deploy(
            @PathVariable UUID id,
            @RequestBody(required = false) AppDeployRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, String> params = request == null ? Map.of() : request.getParams();
        return ResponseEntity.ok(HostedAppResponse.from(
                appService.deploy(id, userId(userDetails), params)));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<HostedAppResponse> stop(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(HostedAppResponse.from(
                appService.stop(id, userId(userDetails))));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<HostedAppResponse> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(HostedAppResponse.from(
                appService.start(id, userId(userDetails))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        appService.remove(id, userId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<Map<String, String>> logs(
            @PathVariable UUID id,
            @RequestParam(value = "lines", required = false) Integer lines,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "logs", appService.logs(id, userId(userDetails), lines)));
    }

    private byte[] zipBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請上傳 .zip 檔");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 檔讀取失敗");
        }
    }

    private static UUID userId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入");
        }
        return UUID.fromString(userDetails.getUsername());
    }
}
