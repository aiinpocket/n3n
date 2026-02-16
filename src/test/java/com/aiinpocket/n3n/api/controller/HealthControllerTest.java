package com.aiinpocket.n3n.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private HealthController controller;

    @Test
    void health_allUp_returnsUp() throws SQLException {
        Connection dbConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void health_dbDown_returnsDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    void health_redisDown_returnsDown() throws SQLException {
        Connection dbConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(true);

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenThrow(new RuntimeException("Redis connection failed"));

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    void health_bothDown_returnsDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("DB down"));

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenThrow(new RuntimeException("Redis down"));

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }

    @Test
    void health_dbConnectionInvalid_returnsDown() throws SQLException {
        Connection dbConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(dbConnection);
        when(dbConnection.isValid(2)).thenReturn(false);

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
    }
}
