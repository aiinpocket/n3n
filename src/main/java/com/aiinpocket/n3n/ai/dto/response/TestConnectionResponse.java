package com.aiinpocket.n3n.ai.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 測試連線回應
 */
@Data
@Builder
public class TestConnectionResponse {

    private boolean success;
    private String message;
    private Long latencyMs;

    public static TestConnectionResponse success(long latencyMs) {
        return TestConnectionResponse.builder()
                .success(true)
                .message("連線成功")
                .latencyMs(latencyMs)
                .build();
    }

    public static TestConnectionResponse failed(String message) {
        return TestConnectionResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
