package com.aiinpocket.n3n.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowExecutionStatsResponse {

    private long total24h;
    private long running;
    private long completed;
    private long failed;
    private long cancelled;
    private Double avgDurationMs;
    private long totalAllTime;
}
