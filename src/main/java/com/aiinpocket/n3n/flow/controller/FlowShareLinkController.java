package com.aiinpocket.n3n.flow.controller;

import com.aiinpocket.n3n.flow.dto.ClaimShareLinkResponse;
import com.aiinpocket.n3n.flow.dto.CreateShareLinkRequest;
import com.aiinpocket.n3n.flow.dto.ShareLinkResponse;
import com.aiinpocket.n3n.flow.service.FlowShareLinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 流程分享連結 API
 */
@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Flow Share Links", description = "Share flows via co-edit links")
public class FlowShareLinkController {

    private final FlowShareLinkService flowShareLinkService;

    @PostMapping("/{flowId}/share-links")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @PathVariable UUID flowId,
            @Valid @RequestBody CreateShareLinkRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ShareLinkResponse response = flowShareLinkService.createShareLink(flowId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{flowId}/share-links")
    public ResponseEntity<List<ShareLinkResponse>> listShareLinks(
            @PathVariable UUID flowId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowShareLinkService.listShareLinks(flowId, userId));
    }

    @DeleteMapping("/{flowId}/share-links/{linkId}")
    public ResponseEntity<Void> revokeShareLink(
            @PathVariable UUID flowId,
            @PathVariable UUID linkId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        flowShareLinkService.revokeShareLink(flowId, linkId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 兌換分享連結：任何登入使用者持有效 token 可取得流程存取權
     */
    @PostMapping("/share-links/{token}/claim")
    public ResponseEntity<ClaimShareLinkResponse> claimShareLink(
            @PathVariable @Size(max = 64) String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(flowShareLinkService.claimShareLink(token, userId));
    }
}
