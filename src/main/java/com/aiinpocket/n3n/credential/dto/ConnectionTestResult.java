package com.aiinpocket.n3n.credential.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Result DTO for credential connection test
 */
@Data
@Builder
public class ConnectionTestResult {

    /**
     * Whether the connection test was successful
     */
    private boolean success;

    /**
     * Human-readable message describing the result
     */
    private String message;

    /**
     * Connection latency in milliseconds
     */
    private long latencyMs;

    /**
     * Server version (if available)
     */
    private String serverVersion;

    /**
     * Timestamp when the test was performed
     */
    private Instant testedAt;

    /**
     * Create a successful result
     */
    public static ConnectionTestResult success(String message, long latencyMs, String serverVersion) {
        return ConnectionTestResult.builder()
                .success(true)
                .message(message)
                .latencyMs(latencyMs)
                .serverVersion(serverVersion)
                .testedAt(Instant.now())
                .build();
    }

    /**
     * Create a failed result
     */
    public static ConnectionTestResult failure(String message, long latencyMs) {
        return ConnectionTestResult.builder()
                .success(false)
                .message(message)
                .latencyMs(latencyMs)
                .testedAt(Instant.now())
                .build();
    }
}
