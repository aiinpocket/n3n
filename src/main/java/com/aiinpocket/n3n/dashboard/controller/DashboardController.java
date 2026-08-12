package com.aiinpocket.n3n.dashboard.controller;

import com.aiinpocket.n3n.dashboard.dto.DashboardStatsResponse;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics")
public class DashboardController {

    private final FlowRepository flowRepository;
    private final ExecutionRepository executionRepository;

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        long totalFlows = flowRepository.countByCreatedByAndIsDeletedFalse(userId);

        // Single aggregated query instead of 4 separate COUNT queries.
        // JPA 對單列聚合查詢會回傳 Object[]{Object[]{...}}，需先解開外層包裝
        Object[] stats = executionRepository.getUserDashboardStats(userId);
        if (stats.length == 1 && stats[0] instanceof Object[] inner) {
            stats = inner;
        }
        long totalExecutions = stats[0] != null ? ((Number) stats[0]).longValue() : 0L;
        long successfulExecutions = stats[1] != null ? ((Number) stats[1]).longValue() : 0L;
        long failedExecutions = stats[2] != null ? ((Number) stats[2]).longValue() : 0L;
        long runningExecutions = stats[3] != null ? ((Number) stats[3]).longValue() : 0L;

        DashboardStatsResponse response = DashboardStatsResponse.builder()
                .totalFlows(totalFlows)
                .totalExecutions(totalExecutions)
                .successfulExecutions(successfulExecutions)
                .failedExecutions(failedExecutions)
                .runningExecutions(runningExecutions)
                .build();

        return ResponseEntity.ok(response);
    }
}
