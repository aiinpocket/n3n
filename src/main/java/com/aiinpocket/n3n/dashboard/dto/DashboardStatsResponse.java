package com.aiinpocket.n3n.dashboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalFlows;
    private long totalExecutions;
    private long successfulExecutions;
    private long failedExecutions;
    private long runningExecutions;
}
