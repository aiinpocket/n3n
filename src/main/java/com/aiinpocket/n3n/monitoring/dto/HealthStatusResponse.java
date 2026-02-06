package com.aiinpocket.n3n.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthStatusResponse {

    private String database;
    private long dbResponseMs;
    private String redis;
    private long redisResponseMs;
    private String overall;
}
