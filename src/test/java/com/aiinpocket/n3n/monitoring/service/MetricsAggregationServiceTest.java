package com.aiinpocket.n3n.monitoring.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.monitoring.dto.FlowExecutionStatsResponse;
import com.aiinpocket.n3n.monitoring.dto.HealthStatusResponse;
import com.aiinpocket.n3n.monitoring.dto.SystemMetricsResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricsAggregationServiceTest extends BaseServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private MetricsAggregationService metricsAggregationService;

    @Test
    void getSystemMetrics_returnsValidMetrics() {
        // Given - mock MeterRegistry to return null searches (fallback to defaults)
        Search search = mock(Search.class);
        when(meterRegistry.find(anyString())).thenReturn(search);
        when(search.tags(any(String[].class))).thenReturn(search);
        when(search.gauge()).thenReturn(null);

        // When
        SystemMetricsResponse metrics = metricsAggregationService.getSystemMetrics();

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getAvailableProcessors()).isGreaterThan(0);
        assertThat(metrics.getTotalMemory()).isGreaterThan(0);
        assertThat(metrics.getFreeMemory()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getSystemMetrics_withMicrometerGauges_returnsGaugeValues() {
        // Given
        Search search = mock(Search.class);
        Gauge gauge = mock(Gauge.class);
        when(meterRegistry.find(anyString())).thenReturn(search);
        when(search.tags(any(String[].class))).thenReturn(search);
        when(search.gauge()).thenReturn(gauge);
        when(gauge.value()).thenReturn(1024.0 * 1024.0); // 1MB-ish

        // When
        SystemMetricsResponse metrics = metricsAggregationService.getSystemMetrics();

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getHeapUsed()).isGreaterThan(0);
    }

    @Test
    void getFlowExecutionStats_returnsCorrectCounts() {
        // Given
        when(executionRepository.countByStartedAtAfter(any(Instant.class))).thenReturn(100L);
        when(executionRepository.countByStatus("running")).thenReturn(5L);
        when(executionRepository.countByStatusAndStartedAtAfter(eq("completed"), any(Instant.class))).thenReturn(80L);
        when(executionRepository.countByStatusAndStartedAtAfter(eq("failed"), any(Instant.class))).thenReturn(10L);
        when(executionRepository.countByStatusAndStartedAtAfter(eq("cancelled"), any(Instant.class))).thenReturn(5L);
        when(executionRepository.findAverageDurationMsSince(any(Instant.class))).thenReturn(1500.0);
        when(executionRepository.count()).thenReturn(500L);

        // When
        FlowExecutionStatsResponse stats = metricsAggregationService.getFlowExecutionStats();

        // Then
        assertThat(stats.getTotal24h()).isEqualTo(100L);
        assertThat(stats.getRunning()).isEqualTo(5L);
        assertThat(stats.getCompleted()).isEqualTo(80L);
        assertThat(stats.getFailed()).isEqualTo(10L);
        assertThat(stats.getCancelled()).isEqualTo(5L);
        assertThat(stats.getAvgDurationMs()).isEqualTo(1500.0);
        assertThat(stats.getTotalAllTime()).isEqualTo(500L);
    }

    @Test
    void getFlowExecutionStats_withNoExecutions_returnsZeros() {
        // Given
        when(executionRepository.countByStartedAtAfter(any(Instant.class))).thenReturn(0L);
        when(executionRepository.countByStatus("running")).thenReturn(0L);
        when(executionRepository.countByStatusAndStartedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        when(executionRepository.findAverageDurationMsSince(any(Instant.class))).thenReturn(null);
        when(executionRepository.count()).thenReturn(0L);

        // When
        FlowExecutionStatsResponse stats = metricsAggregationService.getFlowExecutionStats();

        // Then
        assertThat(stats.getTotal24h()).isZero();
        assertThat(stats.getRunning()).isZero();
        assertThat(stats.getCompleted()).isZero();
        assertThat(stats.getFailed()).isZero();
        assertThat(stats.getCancelled()).isZero();
        assertThat(stats.getAvgDurationMs()).isNull();
        assertThat(stats.getTotalAllTime()).isZero();
    }

    @Test
    void getHealthStatus_allUp_returnsOverallUp() throws SQLException {
        // Given - DB
        Connection dbConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);

        // Given - Redis
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // When
        HealthStatusResponse health = metricsAggregationService.getHealthStatus();

        // Then
        assertThat(health.getDatabase()).isEqualTo("UP");
        assertThat(health.getRedis()).isEqualTo("UP");
        assertThat(health.getOverall()).isEqualTo("UP");
        assertThat(health.getDbResponseMs()).isGreaterThanOrEqualTo(0);
        assertThat(health.getRedisResponseMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getHealthStatus_dbDown_returnsOverallDown() throws SQLException {
        // Given - DB fails
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // Given - Redis OK
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // When
        HealthStatusResponse health = metricsAggregationService.getHealthStatus();

        // Then
        assertThat(health.getDatabase()).isEqualTo("DOWN");
        assertThat(health.getRedis()).isEqualTo("UP");
        assertThat(health.getOverall()).isEqualTo("DOWN");
    }

    @Test
    void getHealthStatus_redisDown_returnsOverallDown() throws SQLException {
        // Given - DB OK
        Connection dbConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);

        // Given - Redis fails
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        HealthStatusResponse health = metricsAggregationService.getHealthStatus();

        // Then
        assertThat(health.getDatabase()).isEqualTo("UP");
        assertThat(health.getRedis()).isEqualTo("DOWN");
        assertThat(health.getOverall()).isEqualTo("DOWN");
    }

    @Test
    void getHealthStatus_bothDown_returnsOverallDown() throws SQLException {
        // Given - DB fails
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // Given - Redis fails
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        HealthStatusResponse health = metricsAggregationService.getHealthStatus();

        // Then
        assertThat(health.getDatabase()).isEqualTo("DOWN");
        assertThat(health.getRedis()).isEqualTo("DOWN");
        assertThat(health.getOverall()).isEqualTo("DOWN");
    }
}
