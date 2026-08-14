package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.execution.service.NodeProbeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 單節點試打端點：編排時「真的執行一次」節點以取得實際輸出。
 * 臨時執行，不留任何執行紀錄。
 */
@RestController
@RequestMapping("/api/node-probe")
@RequiredArgsConstructor
@Validated
public class NodeProbeController {

    private final NodeProbeService nodeProbeService;
    private final IpRateLimiter ipRateLimiter;

    @Data
    public static class ProbeRequest {
        @NotBlank
        @Size(max = 100)
        private String nodeType;

        @Size(max = 100)
        private String nodeId;

        /** 節點設定（編輯面板目前的值，含未儲存的修改） */
        private Map<String, Object> config;

        /** 上游節點的實際輸出（{nodeId: output}），表達式據此求值 */
        private Map<String, Object> previousOutputs;
    }

    @PostMapping
    public ResponseEntity<NodeProbeService.ProbeResult> probe(
            @Validated @RequestBody ProbeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // 每分鐘 20 次：試打會真的呼叫外部服務
        ipRateLimiter.checkAllowed("node-probe", userId.toString(), 20, 60);
        return ResponseEntity.ok(nodeProbeService.probe(
            userId, request.getNodeType(), request.getNodeId(),
            request.getConfig(), request.getPreviousOutputs()));
    }
}
