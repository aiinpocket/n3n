package com.aiinpocket.n3n.monitoring.service;

import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.monitoring.dto.FlowExecutionStatsResponse;
import com.aiinpocket.n3n.monitoring.dto.HealthStatusResponse;
import com.aiinpocket.n3n.monitoring.dto.SystemMetricsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAggregationService {

    private final MeterRegistry meterRegistry;
    private final ExecutionRepository executionRepository;
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    /**
     * Collect JVM and system metrics from Micrometer and JMX.
     */
    public SystemMetricsResponse getSystemMetrics() {
        long heapUsed = getGaugeValue("jvm.memory.used", "area", "heap");
        long heapMax = getGaugeValue("jvm.memory.max", "area", "heap");
        long nonHeapUsed = getGaugeValue("jvm.memory.used", "area", "nonheap");

        int threadCount = (int) getGaugeValue("jvm.threads.live");
        int threadPeak = (int) getGaugeValue("jvm.threads.peak");

        double cpuUsage = getGaugeValueDouble("process.cpu.usage");

        // GC stats from JMX (more reliable than Micrometer for cumulative counts)
        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            if (count >= 0) gcCount += count;
            if (time >= 0) gcTimeMs += time;
        }

        long uptimeMs = (long) getGaugeValue("process.uptime");
        if (uptimeMs == 0) {
            // Fallback: process.uptime may be reported in seconds
            double uptimeSeconds = getGaugeValueDouble("process.uptime");
            uptimeMs = (long) (uptimeSeconds * 1000);
        }

        Runtime runtime = Runtime.getRuntime();

        return SystemMetricsResponse.builder()
                .heapUsed(heapUsed)
                .heapMax(heapMax)
                .nonHeapUsed(nonHeapUsed)
                .threadCount(threadCount)
                .threadPeak(threadPeak)
                .cpuUsage(cpuUsage)
                .gcCount(gcCount)
                .gcTimeMs(gcTimeMs)
                .uptimeMs(uptimeMs)
                .availableProcessors(runtime.availableProcessors())
                .totalMemory(runtime.totalMemory())
                .freeMemory(runtime.freeMemory())
                .build();
    }

    /**
     * Query execution statistics from the database.
     */
    public FlowExecutionStatsResponse getFlowExecutionStats() {
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);

        long total24h = executionRepository.countByStartedAtAfter(twentyFourHoursAgo);
        long running = executionRepository.countByStatus("running");
        long completed = executionRepository.countByStatusAndStartedAtAfter("completed", twentyFourHoursAgo);
        long failed = executionRepository.countByStatusAndStartedAtAfter("failed", twentyFourHoursAgo);
        long cancelled = executionRepository.countByStatusAndStartedAtAfter("cancelled", twentyFourHoursAgo);
        Double avgDurationMs = executionRepository.findAverageDurationMsSince(twentyFourHoursAgo);
        long totalAllTime = executionRepository.count();

        return FlowExecutionStatsResponse.builder()
                .total24h(total24h)
                .running(running)
                .completed(completed)
                .failed(failed)
                .cancelled(cancelled)
                .avgDurationMs(avgDurationMs)
                .totalAllTime(totalAllTime)
                .build();
    }

    /**
     * Check database and Redis connectivity, measure response times.
     */
    public HealthStatusResponse getHealthStatus() {
        // Check database
        String dbStatus;
        long dbResponseMs;
        long dbStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            dbStatus = connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            dbStatus = "DOWN";
        }
        dbResponseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dbStart);

        // Check Redis
        String redisStatus;
        long redisResponseMs;
        long redisStart = System.nanoTime();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            redisStatus = "PONG".equals(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            redisStatus = "DOWN";
        }
        redisResponseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - redisStart);

        String overall = ("UP".equals(dbStatus) && "UP".equals(redisStatus)) ? "UP" : "DOWN";

        return HealthStatusResponse.builder()
                .database(dbStatus)
                .dbResponseMs(dbResponseMs)
                .redis(redisStatus)
                .redisResponseMs(redisResponseMs)
                .overall(overall)
                .build();
    }

    // ---- Internal helpers ----

    private long getGaugeValue(String name, String... tags) {
        try {
            var gauge = meterRegistry.find(name).tags(tags).gauge();
            if (gauge != null) {
                return (long) gauge.value();
            }
        } catch (Exception e) {
            log.trace("Metric not found: {} - {}", name, e.getMessage());
        }
        return 0L;
    }

    private double getGaugeValueDouble(String name, String... tags) {
        try {
            var gauge = meterRegistry.find(name).tags(tags).gauge();
            if (gauge != null) {
                return gauge.value();
            }
        } catch (Exception e) {
            log.trace("Metric not found: {} - {}", name, e.getMessage());
        }
        return 0.0;
    }
}
