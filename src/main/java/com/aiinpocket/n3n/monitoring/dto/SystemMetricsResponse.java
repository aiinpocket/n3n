package com.aiinpocket.n3n.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemMetricsResponse {

    // JVM Memory
    private long heapUsed;
    private long heapMax;
    private long nonHeapUsed;

    // JVM Threads
    private int threadCount;
    private int threadPeak;

    // CPU
    private double cpuUsage;

    // GC
    private long gcCount;
    private long gcTimeMs;

    // System
    private long uptimeMs;
    private int availableProcessors;
    private long totalMemory;
    private long freeMemory;
}
