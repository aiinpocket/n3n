package com.aiinpocket.n3n.dashboard.controller;

import com.aiinpocket.n3n.dashboard.dto.DashboardStatsResponse;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        long totalFlows = flowRepository.countByCreatedByAndIsDeletedFalse(userId);
        long totalExecutions = executionRepository.countByTriggeredBy(userId);
        long successfulExecutions = executionRepository.countByTriggeredByAndStatus(userId, "completed");
        long failedExecutions = executionRepository.countByTriggeredByAndStatus(userId, "failed");
        long runningExecutions = executionRepository.countByTriggeredByAndStatus(userId, "running");

        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .totalFlows(totalFlows)
                .totalExecutions(totalExecutions)
                .successfulExecutions(successfulExecutions)
                .failedExecutions(failedExecutions)
                .runningExecutions(runningExecutions)
                .build();

        return ResponseEntity.ok(stats);
    }
}
