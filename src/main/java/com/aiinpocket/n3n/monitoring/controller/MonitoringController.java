package com.aiinpocket.n3n.monitoring.controller;

import com.aiinpocket.n3n.monitoring.dto.FlowExecutionStatsResponse;
import com.aiinpocket.n3n.monitoring.dto.HealthStatusResponse;
import com.aiinpocket.n3n.monitoring.dto.SystemMetricsResponse;
import com.aiinpocket.n3n.monitoring.service.MetricsAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "System monitoring and metrics")
public class MonitoringController {

    private final MetricsAggregationService metricsAggregationService;

    @Operation(summary = "Get JVM and system metrics")
    @GetMapping("/system")
    public SystemMetricsResponse getSystemMetrics() {
        return metricsAggregationService.getSystemMetrics();
    }

    @Operation(summary = "Get flow execution statistics")
    @GetMapping("/flows")
    public FlowExecutionStatsResponse getFlowStats() {
        return metricsAggregationService.getFlowExecutionStats();
    }

    @Operation(summary = "Get health status of database and Redis")
    @GetMapping("/health")
    public HealthStatusResponse getHealthStatus() {
        return metricsAggregationService.getHealthStatus();
    }
}
