package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.ClaimShareLinkResponse;
import com.aiinpocket.n3n.flow.dto.CreateShareLinkRequest;
import com.aiinpocket.n3n.flow.dto.ShareLinkResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowShare;
import com.aiinpocket.n3n.flow.entity.FlowShareLink;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareLinkRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流程分享連結服務
 *
 * 建立 / 列出 / 撤銷分享連結，以及讓登入使用者以 token 兌換流程存取權。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowShareLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final String GENERIC_NOT_FOUND = "Share link not found";

    /** 權限等級排序，用於「只升不降」的比較 */
    private static final Map<String, Integer> PERMISSION_RANK = Map.of(
        "view", 1,
        "edit", 2,
        "admin", 3
    );

    private final FlowShareLinkRepository shareLinkRepository;
    private final FlowShareRepository flowShareRepository;
    private final FlowRepository flowRepository;

    /**
     * 建立分享連結（僅流程擁有者或 admin 權限使用者）
     */
    @Transactional
    public ShareLinkResponse createShareLink(UUID flowId, CreateShareLinkRequest request, UUID userId) {
        Flow flow = requireManageableFlow(flowId, userId, "create share link");

        Instant expiresAt = request.getExpiresInDays() != null
            ? Instant.now().plus(request.getExpiresInDays(), ChronoUnit.DAYS)
            : null;

        FlowShareLink link = FlowShareLink.builder()
            .flowId(flow.getId())
            .token(generateToken())
            .permission(request.getPermission())
            .createdBy(userId)
            .expiresAt(expiresAt)
            .build();

        link = shareLinkRepository.save(link);
        log.info("Share link {} created for flow {} by user {} (permission: {}, expiresAt: {})",
            link.getId(), flowId, userId, link.getPermission(), expiresAt);

        return ShareLinkResponse.from(link);
    }

    /**
     * 列出流程目前有效的分享連結（僅流程擁有者或 admin 權限使用者）
     */
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listShareLinks(UUID flowId, UUID userId) {
        requireManageableFlow(flowId, userId, "list share links");

        return shareLinkRepository.findByFlowIdAndRevokedAtIsNullOrderByCreatedAtDesc(flowId).stream()
            .filter(FlowShareLink::isActive)
            .map(ShareLinkResponse::from)
            .toList();
    }

    /**
     * 撤銷分享連結（僅流程擁有者或 admin 權限使用者）
     */
    @Transactional
    public void revokeShareLink(UUID flowId, UUID linkId, UUID userId) {
        requireManageableFlow(flowId, userId, "revoke share link");

        FlowShareLink link = shareLinkRepository.findById(linkId)
            .orElseThrow(() -> new ResourceNotFoundException(GENERIC_NOT_FOUND));

        if (!link.getFlowId().equals(flowId)) {
            log.warn("User {} attempted to revoke link {} not belonging to flow {}", userId, linkId, flowId);
            throw new ResourceNotFoundException(GENERIC_NOT_FOUND);
        }

        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now());
            shareLinkRepository.save(link);
        }
        log.info("Share link {} for flow {} revoked by user {}", linkId, flowId, userId);
    }

    /**
     * 兌換分享連結：為當前使用者建立（或升級）流程分享記錄。
     * 無效 token 一律回傳泛用 404，避免洩漏流程是否存在。
     */
    @Transactional
    public ClaimShareLinkResponse claimShareLink(String token, UUID userId) {
        FlowShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> {
                log.info("Share link claim failed: token not found (user {})", userId);
                return new ResourceNotFoundException(GENERIC_NOT_FOUND);
            });

        if (!link.isActive()) {
            log.info("Share link claim failed: link {} revoked or expired (user {})", link.getId(), userId);
            throw new ResourceNotFoundException(GENERIC_NOT_FOUND);
        }

        Flow flow = flowRepository.findByIdAndIsDeletedFalse(link.getFlowId())
            .orElseThrow(() -> {
                log.warn("Share link {} points to missing/deleted flow {}", link.getId(), link.getFlowId());
                return new ResourceNotFoundException(GENERIC_NOT_FOUND);
            });

        // 擁有者兌換自己的連結：no-op
        if (flow.getCreatedBy().equals(userId)) {
            return ClaimShareLinkResponse.builder()
                .flowId(flow.getId())
                .permission("owner")
                .flowName(flow.getName())
                .build();
        }

        String effectivePermission = upsertShare(link, flow, userId);

        return ClaimShareLinkResponse.builder()
            .flowId(flow.getId())
            .permission(effectivePermission)
            .flowName(flow.getName())
            .build();
    }

    /**
     * 建立或升級 FlowShare（永不降級既有較高權限）
     */
    private String upsertShare(FlowShareLink link, Flow flow, UUID userId) {
        FlowShare existing = flowShareRepository.findByFlowIdAndUserId(flow.getId(), userId).orElse(null);

        if (existing == null) {
            FlowShare share = FlowShare.builder()
                .flowId(flow.getId())
                .userId(userId)
                .permission(link.getPermission())
                .sharedBy(link.getCreatedBy())
                .acceptedAt(Instant.now())
                .build();
            flowShareRepository.save(share);
            log.info("User {} claimed share link {} for flow {} with permission {}",
                userId, link.getId(), flow.getId(), link.getPermission());
            return link.getPermission();
        }

        if (permissionRank(link.getPermission()) > permissionRank(existing.getPermission())) {
            existing.setPermission(link.getPermission());
            flowShareRepository.save(existing);
            log.info("User {} upgraded to permission {} on flow {} via share link {}",
                userId, link.getPermission(), flow.getId(), link.getId());
            return link.getPermission();
        }

        log.debug("User {} claimed share link {} but keeps existing permission {} on flow {}",
            userId, link.getId(), existing.getPermission(), flow.getId());
        return existing.getPermission();
    }

    /**
     * 驗證流程存在且使用者為擁有者或具 admin 分享權限
     */
    private Flow requireManageableFlow(UUID flowId, UUID userId, String action) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        if (!flow.getCreatedBy().equals(userId) && !hasAdminPermission(flowId, userId)) {
            log.warn("User {} denied to {} on flow {}", userId, action, flowId);
            throw new IllegalArgumentException("You don't have permission to manage share links");
        }
        return flow;
    }

    private boolean hasAdminPermission(UUID flowId, UUID userId) {
        return flowShareRepository.findPermissionByFlowIdAndUserId(flowId, userId)
            .map("admin"::equals)
            .orElse(false);
    }

    private static int permissionRank(String permission) {
        return PERMISSION_RANK.getOrDefault(permission, 0);
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
