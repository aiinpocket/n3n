package com.aiinpocket.n3n.monitoring.controller;

import com.aiinpocket.n3n.monitoring.dto.FlowExecutionStatsResponse;
import com.aiinpocket.n3n.monitoring.dto.HealthStatusResponse;
import com.aiinpocket.n3n.monitoring.dto.SystemMetricsResponse;
import com.aiinpocket.n3n.monitoring.service.MetricsAggregationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringControllerTest {

    @Mock
    private MetricsAggregationService metricsAggregationService;

    @InjectMocks
    private MonitoringController monitoringController;

    // ========== getSystemMetrics ==========

    @Test
    void getSystemMetrics_returnsMetrics() {
        var metrics = SystemMetricsResponse.builder()
                .heapUsed(256_000_000L)
                .heapMax(512_000_000L)
                .nonHeapUsed(64_000_000L)
                .threadCount(50)
                .threadPeak(75)
                .cpuUsage(0.35)
                .gcCount(100)
                .gcTimeMs(5000)
                .uptimeMs(3_600_000)
                .availableProcessors(8)
                .totalMemory(16_000_000_000L)
                .freeMemory(8_000_000_000L)
                .build();

        when(metricsAggregationService.getSystemMetrics()).thenReturn(metrics);

        var result = monitoringController.getSystemMetrics();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getHeapUsed()).isEqualTo(256_000_000L);
        assertThat(result.getBody().getHeapMax()).isEqualTo(512_000_000L);
        assertThat(result.getBody().getNonHeapUsed()).isEqualTo(64_000_000L);
        assertThat(result.getBody().getThreadCount()).isEqualTo(50);
        assertThat(result.getBody().getThreadPeak()).isEqualTo(75);
        assertThat(result.getBody().getCpuUsage()).isEqualTo(0.35);
        assertThat(result.getBody().getGcCount()).isEqualTo(100);
        assertThat(result.getBody().getGcTimeMs()).isEqualTo(5000);
        assertThat(result.getBody().getUptimeMs()).isEqualTo(3_600_000);
        assertThat(result.getBody().getAvailableProcessors()).isEqualTo(8);
        assertThat(result.getBody().getTotalMemory()).isEqualTo(16_000_000_000L);
        assertThat(result.getBody().getFreeMemory()).isEqualTo(8_000_000_000L);
        verify(metricsAggregationService).getSystemMetrics();
    }

    @Test
    void getSystemMetrics_delegatesToService() {
        var metrics = SystemMetricsResponse.builder()
                .heapUsed(100L)
                .heapMax(200L)
                .build();
        when(metricsAggregationService.getSystemMetrics()).thenReturn(metrics);

        monitoringController.getSystemMetrics();

        verify(metricsAggregationService, times(1)).getSystemMetrics();
    }

    // ========== getFlowStats ==========

    @Test
    void getFlowStats_returnsStats() {
        var stats = FlowExecutionStatsResponse.builder()
                .total24h(150)
                .running(5)
                .completed(120)
                .failed(20)
                .cancelled(5)
                .avgDurationMs(1500.0)
                .totalAllTime(10000)
                .build();

        when(metricsAggregationService.getFlowExecutionStats()).thenReturn(stats);

        var result = monitoringController.getFlowStats();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotal24h()).isEqualTo(150);
        assertThat(result.getBody().getRunning()).isEqualTo(5);
        assertThat(result.getBody().getCompleted()).isEqualTo(120);
        assertThat(result.getBody().getFailed()).isEqualTo(20);
        assertThat(result.getBody().getCancelled()).isEqualTo(5);
        assertThat(result.getBody().getAvgDurationMs()).isEqualTo(1500.0);
        assertThat(result.getBody().getTotalAllTime()).isEqualTo(10000);
        verify(metricsAggregationService).getFlowExecutionStats();
    }

    @Test
    void getFlowStats_zeroStats_returnsZeroValues() {
        var stats = FlowExecutionStatsResponse.builder()
                .total24h(0)
                .running(0)
                .completed(0)
                .failed(0)
                .cancelled(0)
                .avgDurationMs(null)
                .totalAllTime(0)
                .build();

        when(metricsAggregationService.getFlowExecutionStats()).thenReturn(stats);

        var result = monitoringController.getFlowStats();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotal24h()).isEqualTo(0);
        assertThat(result.getBody().getAvgDurationMs()).isNull();
    }

    @Test
    void getFlowStats_delegatesToService() {
        var stats = FlowExecutionStatsResponse.builder().build();
        when(metricsAggregationService.getFlowExecutionStats()).thenReturn(stats);

        monitoringController.getFlowStats();

        verify(metricsAggregationService, times(1)).getFlowExecutionStats();
    }

    // ========== getHealthStatus ==========

    @Test
    void getHealthStatus_allHealthy_returnsUp() {
        var health = HealthStatusResponse.builder()
                .database("UP")
                .dbResponseMs(5)
                .redis("UP")
                .redisResponseMs(2)
                .overall("UP")
                .build();

        when(metricsAggregationService.getHealthStatus()).thenReturn(health);

        var result = monitoringController.getHealthStatus();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDatabase()).isEqualTo("UP");
        assertThat(result.getBody().getDbResponseMs()).isEqualTo(5);
        assertThat(result.getBody().getRedis()).isEqualTo("UP");
        assertThat(result.getBody().getRedisResponseMs()).isEqualTo(2);
        assertThat(result.getBody().getOverall()).isEqualTo("UP");
        verify(metricsAggregationService).getHealthStatus();
    }

    @Test
    void getHealthStatus_databaseDown_returnsDown() {
        var health = HealthStatusResponse.builder()
                .database("DOWN")
                .dbResponseMs(-1)
                .redis("UP")
                .redisResponseMs(3)
                .overall("DOWN")
                .build();

        when(metricsAggregationService.getHealthStatus()).thenReturn(health);

        var result = monitoringController.getHealthStatus();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDatabase()).isEqualTo("DOWN");
        assertThat(result.getBody().getOverall()).isEqualTo("DOWN");
    }

    @Test
    void getHealthStatus_redisDown_returnsDown() {
        var health = HealthStatusResponse.builder()
                .database("UP")
                .dbResponseMs(4)
                .redis("DOWN")
                .redisResponseMs(-1)
                .overall("DOWN")
                .build();

        when(metricsAggregationService.getHealthStatus()).thenReturn(health);

        var result = monitoringController.getHealthStatus();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getRedis()).isEqualTo("DOWN");
        assertThat(result.getBody().getOverall()).isEqualTo("DOWN");
    }

    @Test
    void getHealthStatus_delegatesToService() {
        var health = HealthStatusResponse.builder()
                .overall("UP")
                .build();
        when(metricsAggregationService.getHealthStatus()).thenReturn(health);

        monitoringController.getHealthStatus();

        verify(metricsAggregationService, times(1)).getHealthStatus();
    }

    // ========== service exception propagation ==========

    @Test
    void getSystemMetrics_serviceThrows_propagatesException() {
        when(metricsAggregationService.getSystemMetrics()).thenThrow(new RuntimeException("Metrics unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> monitoringController.getSystemMetrics());
    }

    @Test
    void getFlowStats_serviceThrows_propagatesException() {
        when(metricsAggregationService.getFlowExecutionStats()).thenThrow(new RuntimeException("Stats unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> monitoringController.getFlowStats());
    }

    @Test
    void getHealthStatus_serviceThrows_propagatesException() {
        when(metricsAggregationService.getHealthStatus()).thenThrow(new RuntimeException("Health check failed"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> monitoringController.getHealthStatus());
    }
}
