package com.aiinpocket.n3n.activity.controller;

import com.aiinpocket.n3n.activity.dto.UserActivityResponse;
import com.aiinpocket.n3n.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Tag(name = "Activities", description = "User activity and audit log management")
public class ActivityController {

    private final ActivityService activityService;

    /**
     * List all activities (admin only).
     * Supports optional ?type=XXX filter for activity type.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserActivityResponse>> listAllActivities(
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(activityService.getActivitiesByType(type, pageable)
                    .map(UserActivityResponse::from));
        }
        return ResponseEntity.ok(activityService.getAllActivities(pageable)
                .map(UserActivityResponse::from));
    }

    /**
     * List current user's activities.
     * Supports optional ?type=XXX filter for activity type.
     */
    @GetMapping("/my")
    public ResponseEntity<Page<UserActivityResponse>> listMyActivities(
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(activityService.getUserActivitiesByType(userId, type, pageable)
                    .map(UserActivityResponse::from));
        }
        return ResponseEntity.ok(activityService.getUserActivities(userId, pageable)
                .map(UserActivityResponse::from));
    }

    /**
     * Get activities for a specific resource (only current user's activities on that resource).
     */
    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<Page<UserActivityResponse>> getResourceActivities(
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(activityService.getUserResourceActivities(userId, resourceType, resourceId, pageable)
                .map(UserActivityResponse::from));
    }
}
