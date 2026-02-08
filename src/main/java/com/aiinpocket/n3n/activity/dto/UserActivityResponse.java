package com.aiinpocket.n3n.activity.dto;

import com.aiinpocket.n3n.activity.entity.UserActivity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserActivityResponse(
        UUID id,
        UUID userId,
        String activityType,
        String resourceType,
        UUID resourceId,
        String resourceName,
        Map<String, Object> details,
        Instant createdAt
) {
    public static UserActivityResponse from(UserActivity entity) {
        return new UserActivityResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getActivityType(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getResourceName(),
                entity.getDetails(),
                entity.getCreatedAt()
        );
    }
}
