package com.aiinpocket.n3n.logging.controller;

import com.aiinpocket.n3n.logging.InMemoryLogBuffer;
import com.aiinpocket.n3n.logging.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogViewerControllerTest {

    @Mock
    private InMemoryLogBuffer logBuffer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private LogViewerController logViewerController;

    private LogEntry sampleLogEntry(String level, String message) {
        return LogEntry.builder()
                .timestamp(Instant.now())
                .level(level)
                .logger("com.aiinpocket.n3n.test")
                .message(message)
                .traceId("trace-123")
                .executionId("exec-456")
                .flowId("flow-789")
                .nodeId("node-1")
                .userId("user-abc")
                .threadName("main")
                .build();
    }

    // ========== getLogs ==========

    @Test
    void getLogs_noFilters_returnsAllLogs() {
        var entry1 = sampleLogEntry("INFO", "Test message 1");
        var entry2 = sampleLogEntry("ERROR", "Test error");
        when(logBuffer.query(null, null, 100)).thenReturn(List.of(entry1, entry2));

        var result = logViewerController.getLogs(null, null, 100);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessage()).isEqualTo("Test message 1");
        assertThat(result.get(1).getMessage()).isEqualTo("Test error");
        verify(logBuffer).query(null, null, 100);
    }

    @Test
    void getLogs_withLevelFilter_filtersLogs() {
        var entry = sampleLogEntry("ERROR", "Error occurred");
        when(logBuffer.query("ERROR", null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("ERROR", null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo("ERROR");
        verify(logBuffer).query("ERROR", null, 100);
    }

    @Test
    void getLogs_withSearchFilter_filtersLogs() {
        var entry = sampleLogEntry("INFO", "Found matching result");
        when(logBuffer.query(null, "matching", 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs(null, "matching", 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).contains("matching");
        verify(logBuffer).query(null, "matching", 100);
    }

    @Test
    void getLogs_withLevelAndSearch_appliesBothFilters() {
        var entry = sampleLogEntry("WARN", "Warning about connection");
        when(logBuffer.query("WARN", "connection", 50)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("WARN", "connection", 50);

        assertThat(result).hasSize(1);
        verify(logBuffer).query("WARN", "connection", 50);
    }

    @Test
    void getLogs_customLimit_respectsLimit() {
        var entries = List.of(
                sampleLogEntry("INFO", "msg 1"),
                sampleLogEntry("INFO", "msg 2"),
                sampleLogEntry("INFO", "msg 3")
        );
        when(logBuffer.query(null, null, 3)).thenReturn(entries);

        var result = logViewerController.getLogs(null, null, 3);

        assertThat(result).hasSize(3);
        verify(logBuffer).query(null, null, 3);
    }

    @Test
    void getLogs_empty_returnsEmptyList() {
        when(logBuffer.query(null, null, 100)).thenReturn(List.of());

        var result = logViewerController.getLogs(null, null, 100);

        assertThat(result).isEmpty();
    }

    @Test
    void getLogs_defaultLimit_uses100() {
        when(logBuffer.query(null, null, 100)).thenReturn(List.of());

        logViewerController.getLogs(null, null, 100);

        verify(logBuffer).query(null, null, 100);
    }

    @Test
    void getLogs_logsContainContextInfo() {
        var entry = LogEntry.builder()
                .timestamp(Instant.now())
                .level("INFO")
                .logger("com.test.Service")
                .message("Processing flow")
                .traceId("trace-abc")
                .executionId("exec-def")
                .flowId("flow-ghi")
                .nodeId("node-jkl")
                .userId("user-mno")
                .threadName("virtual-thread-1")
                .build();
        when(logBuffer.query(null, null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs(null, null, 100);

        assertThat(result).hasSize(1);
        var logEntry = result.get(0);
        assertThat(logEntry.getTraceId()).isEqualTo("trace-abc");
        assertThat(logEntry.getExecutionId()).isEqualTo("exec-def");
        assertThat(logEntry.getFlowId()).isEqualTo("flow-ghi");
        assertThat(logEntry.getNodeId()).isEqualTo("node-jkl");
        assertThat(logEntry.getUserId()).isEqualTo("user-mno");
        assertThat(logEntry.getThreadName()).isEqualTo("virtual-thread-1");
    }

    private UserDetails mockUser(String userId) {
        return new User(userId, "", Collections.emptyList());
    }

    // ========== streamLogs ==========

    @Test
    void streamLogs_returnsEmitter() {
        var userDetails = mockUser(UUID.randomUUID().toString());
        var emitter = logViewerController.streamLogs(userDetails);

        assertThat(emitter).isNotNull();
        assertThat(emitter).isInstanceOf(SseEmitter.class);
    }

    @Test
    void streamLogs_registersListener() {
        var userDetails = mockUser(UUID.randomUUID().toString());
        logViewerController.streamLogs(userDetails);

        verify(logBuffer).addListener(any());
    }

    @Test
    void streamLogs_multipleClients_registersMultipleListeners() {
        var user1 = mockUser(UUID.randomUUID().toString());
        var user2 = mockUser(UUID.randomUUID().toString());
        logViewerController.streamLogs(user1);
        logViewerController.streamLogs(user2);

        verify(logBuffer, times(2)).addListener(any());
    }

    @Test
    void streamLogs_exceedsMaxConnections_throwsException() {
        var userId = UUID.randomUUID().toString();
        var userDetails = mockUser(userId);

        // Open max connections
        logViewerController.streamLogs(userDetails);
        logViewerController.streamLogs(userDetails);
        logViewerController.streamLogs(userDetails);

        // Fourth connection should fail
        assertThatThrownBy(() -> logViewerController.streamLogs(userDetails))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many concurrent SSE connections");
    }

    // ========== getLogs with different log levels ==========

    @Test
    void getLogs_debugLevel_returnsDebugLogs() {
        var entry = sampleLogEntry("DEBUG", "Debug info");
        when(logBuffer.query("DEBUG", null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("DEBUG", null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo("DEBUG");
    }

    @Test
    void getLogs_infoLevel_returnsInfoLogs() {
        var entry = sampleLogEntry("INFO", "Info message");
        when(logBuffer.query("INFO", null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("INFO", null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo("INFO");
    }

    @Test
    void getLogs_warnLevel_returnsWarnLogs() {
        var entry = sampleLogEntry("WARN", "Warning message");
        when(logBuffer.query("WARN", null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("WARN", null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo("WARN");
    }

    @Test
    void getLogs_errorLevel_returnsErrorLogs() {
        var entry = sampleLogEntry("ERROR", "Error message");
        when(logBuffer.query("ERROR", null, 100)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs("ERROR", null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo("ERROR");
    }

    @Test
    void getLogs_maxLimit_accepts1000() {
        when(logBuffer.query(null, null, 1000)).thenReturn(List.of());

        var result = logViewerController.getLogs(null, null, 1000);

        assertThat(result).isEmpty();
        verify(logBuffer).query(null, null, 1000);
    }

    @Test
    void getLogs_minLimit_accepts1() {
        var entry = sampleLogEntry("INFO", "Single entry");
        when(logBuffer.query(null, null, 1)).thenReturn(List.of(entry));

        var result = logViewerController.getLogs(null, null, 1);

        assertThat(result).hasSize(1);
        verify(logBuffer).query(null, null, 1);
    }
}
