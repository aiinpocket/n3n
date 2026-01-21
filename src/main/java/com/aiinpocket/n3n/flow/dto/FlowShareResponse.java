package com.aiinpocket.n3n.flow.dto;

import com.aiinpocket.n3n.flow.entity.FlowShare;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FlowShareResponse {

    private UUID id;
    private UUID flowId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String invitedEmail;
    private String permission;
    private UUID sharedBy;
    private String sharedByName;
    private Instant sharedAt;
    private Instant acceptedAt;
    private boolean pending;

    public static FlowShareResponse from(FlowShare share) {
        return FlowShareResponse.builder()
            .id(share.getId())
            .flowId(share.getFlowId())
            .userId(share.getUserId())
            .invitedEmail(share.getInvitedEmail())
            .permission(share.getPermission())
            .sharedBy(share.getSharedBy())
            .sharedAt(share.getSharedAt())
            .acceptedAt(share.getAcceptedAt())
            .pending(share.getAcceptedAt() == null && share.getInvitedEmail() != null)
            .build();
    }

    public static FlowShareResponse from(FlowShare share, String userName, String userEmail, String sharedByName) {
        FlowShareResponse response = from(share);
        response.setUserName(userName);
        response.setUserEmail(userEmail);
        response.setSharedByName(sharedByName);
        return response;
    }
}
