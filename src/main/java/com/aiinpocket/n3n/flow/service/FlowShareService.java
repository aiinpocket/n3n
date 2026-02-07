package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.common.constant.Status;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.common.service.EmailService;
import com.aiinpocket.n3n.flow.dto.FlowShareRequest;
import com.aiinpocket.n3n.flow.dto.FlowShareResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowShare;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowShareService {

    private final FlowShareRepository flowShareRepository;
    private final FlowRepository flowRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * 分享流程給用戶
     */
    @Transactional
    public FlowShareResponse shareFlow(UUID flowId, FlowShareRequest request, UUID sharedBy) {
        // 驗證流程存在且有權限分享
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        // 檢查是否為流程擁有者或有 admin 權限
        if (!flow.getCreatedBy().equals(sharedBy) && !hasAdminPermission(flowId, sharedBy)) {
            throw new IllegalArgumentException("You don't have permission to share this flow");
        }

        FlowShare share;

        if (request.getUserId() != null) {
            // 分享給已註冊用戶
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

            // 檢查是否已經分享
            if (flowShareRepository.findByFlowIdAndUserId(flowId, request.getUserId()).isPresent()) {
                throw new IllegalArgumentException("Flow already shared with this user");
            }

            share = FlowShare.builder()
                .flowId(flowId)
                .userId(request.getUserId())
                .permission(request.getPermission())
                .sharedBy(sharedBy)
                .acceptedAt(Instant.now()) // 直接接受
                .build();

        } else if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // 透過 Email 邀請
            // 先檢查是否為已註冊用戶
            User existingUser = userRepository.findByEmail(request.getEmail()).orElse(null);

            if (existingUser != null) {
                // 已註冊用戶，直接分享
                if (flowShareRepository.findByFlowIdAndUserId(flowId, existingUser.getId()).isPresent()) {
                    throw new IllegalArgumentException("Flow already shared with this user");
                }

                share = FlowShare.builder()
                    .flowId(flowId)
                    .userId(existingUser.getId())
                    .permission(request.getPermission())
                    .sharedBy(sharedBy)
                    .acceptedAt(Instant.now())
                    .build();
            } else {
                // 未註冊用戶，記錄 Email 邀請
                if (flowShareRepository.findByFlowIdAndInvitedEmail(flowId, request.getEmail()).isPresent()) {
                    throw new IllegalArgumentException("Invitation already sent to this email");
                }

                share = FlowShare.builder()
                    .flowId(flowId)
                    .invitedEmail(request.getEmail())
                    .permission(request.getPermission())
                    .sharedBy(sharedBy)
                    .build();

                // Send invitation email
                String sharedByName = userRepository.findById(sharedBy)
                    .map(User::getName)
                    .orElse("A user");
                emailService.sendFlowInvitation(request.getEmail(), flow.getName(), sharedByName);
            }
        } else {
            throw new IllegalArgumentException("Either userId or email is required");
        }

        share = flowShareRepository.save(share);
        log.info("Flow {} shared with user/email: {}/{}, permission: {}",
            flowId, share.getUserId(), share.getInvitedEmail(), share.getPermission());

        return enrichShareResponse(share);
    }

    /**
     * 取得流程的所有分享記錄
     */
    public List<FlowShareResponse> getFlowShares(UUID flowId, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        // 檢查是否為流程擁有者或有權限
        if (!flow.getCreatedBy().equals(userId) && !hasAnyPermission(flowId, userId)) {
            throw new IllegalArgumentException("You don't have permission to view shares");
        }

        return flowShareRepository.findByFlowId(flowId).stream()
            .map(this::enrichShareResponse)
            .toList();
    }

    /**
     * 更新分享權限
     */
    @Transactional
    public FlowShareResponse updateSharePermission(UUID flowId, UUID shareId, String permission, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        // 檢查是否為流程擁有者或有 admin 權限
        if (!flow.getCreatedBy().equals(userId) && !hasAdminPermission(flowId, userId)) {
            throw new IllegalArgumentException("You don't have permission to update shares");
        }

        FlowShare share = flowShareRepository.findById(shareId)
            .orElseThrow(() -> new ResourceNotFoundException("Share not found: " + shareId));

        if (!share.getFlowId().equals(flowId)) {
            throw new IllegalArgumentException("Share does not belong to this flow");
        }

        share.setPermission(permission);
        share = flowShareRepository.save(share);

        log.info("Share {} permission updated to: {}", shareId, permission);

        return enrichShareResponse(share);
    }

    /**
     * 移除分享
     */
    @Transactional
    public void removeShare(UUID flowId, UUID shareId, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
            .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        // 檢查是否為流程擁有者或有 admin 權限
        if (!flow.getCreatedBy().equals(userId) && !hasAdminPermission(flowId, userId)) {
            throw new IllegalArgumentException("You don't have permission to remove shares");
        }

        FlowShare share = flowShareRepository.findById(shareId)
            .orElseThrow(() -> new ResourceNotFoundException("Share not found: " + shareId));

        if (!share.getFlowId().equals(flowId)) {
            throw new IllegalArgumentException("Share does not belong to this flow");
        }

        flowShareRepository.delete(share);
        log.info("Share {} removed from flow {}", shareId, flowId);
    }

    /**
     * 接受 Email 邀請（用戶註冊後呼叫）
     */
    @Transactional
    public void acceptPendingInvitations(UUID userId, String email) {
        List<FlowShare> pendingShares = flowShareRepository.findByInvitedEmailAndAcceptedAtIsNull(email);

        for (FlowShare share : pendingShares) {
            share.setUserId(userId);
            share.setAcceptedAt(Instant.now());
            flowShareRepository.save(share);
            log.info("User {} accepted invitation for flow {}", userId, share.getFlowId());
        }
    }

    /**
     * 取得用戶被分享的流程
     */
    public List<FlowShareResponse> getSharedWithMe(UUID userId) {
        return flowShareRepository.findByUserId(userId).stream()
            .map(this::enrichShareResponse)
            .toList();
    }

    /**
     * 檢查用戶是否有流程的存取權
     */
    public boolean hasAccess(UUID flowId, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId).orElse(null);
        if (flow == null) {
            return false;
        }

        // 擁有者有權限
        if (flow.getCreatedBy().equals(userId)) {
            return true;
        }

        // 公開流程
        if (Status.Visibility.PUBLIC.equals(flow.getVisibility())) {
            return true;
        }

        // 檢查分享記錄
        return flowShareRepository.findByFlowIdAndUserId(flowId, userId).isPresent();
    }

    /**
     * 檢查用戶是否有編輯權限
     */
    public boolean hasEditAccess(UUID flowId, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId).orElse(null);
        if (flow == null) {
            return false;
        }

        // 擁有者有編輯權限
        if (flow.getCreatedBy().equals(userId)) {
            return true;
        }

        // 檢查分享記錄
        return flowShareRepository.hasEditPermission(flowId, userId);
    }

    private boolean hasAdminPermission(UUID flowId, UUID userId) {
        return flowShareRepository.findPermissionByFlowIdAndUserId(flowId, userId)
            .map(p -> "admin".equals(p))
            .orElse(false);
    }

    private boolean hasAnyPermission(UUID flowId, UUID userId) {
        return flowShareRepository.findByFlowIdAndUserId(flowId, userId).isPresent();
    }

    private FlowShareResponse enrichShareResponse(FlowShare share) {
        String userName = null;
        String userEmail = null;
        String sharedByName = null;

        if (share.getUserId() != null) {
            User user = userRepository.findById(share.getUserId()).orElse(null);
            if (user != null) {
                userName = user.getName();
                userEmail = user.getEmail();
            }
        }

        User sharedByUser = userRepository.findById(share.getSharedBy()).orElse(null);
        if (sharedByUser != null) {
            sharedByName = sharedByUser.getName();
        }

        FlowShareResponse response = FlowShareResponse.from(share, userName, userEmail, sharedByName);

        // Enrich with flow name and description
        flowRepository.findById(share.getFlowId()).ifPresent(flow -> {
            response.setFlowName(flow.getName());
            response.setFlowDescription(flow.getDescription());
        });

        return response;
    }
}
