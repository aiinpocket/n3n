package com.aiinpocket.n3n.ai.failover;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 閾值 3 次，重置時間 100ms
        circuitBreaker = new CircuitBreaker("test", 3, 100);
    }

    @Test
    void newCircuitBreaker_shouldBeClosed() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.isOpen()).isFalse();
    }

    @Test
    void recordSuccess_shouldResetFailureCount() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);

        circuitBreaker.recordSuccess();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    void recordFailure_atThreshold_shouldOpenCircuit() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertThat(circuitBreaker.isOpen()).isFalse();

        circuitBreaker.recordFailure(); // 第 3 次，達到閾值
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.isOpen()).isTrue();
    }

    @Test
    void openCircuit_afterResetTimeout_shouldTransitionToHalfOpen() throws InterruptedException {
        // 觸發熔斷
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 等待超過重置時間
        Thread.sleep(150);

        // 檢查狀態應該轉換為 HALF_OPEN
        assertThat(circuitBreaker.isOpen()).isFalse();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void halfOpenCircuit_onSuccess_shouldClose() throws InterruptedException {
        // 觸發熔斷
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        // 等待轉換為 HALF_OPEN
        Thread.sleep(150);
        circuitBreaker.isOpen(); // 觸發狀態轉換

        // 記錄成功
        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    void halfOpenCircuit_onFailure_shouldReopen() throws InterruptedException {
        // 觸發熔斷
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        // 等待轉換為 HALF_OPEN
        Thread.sleep(150);
        circuitBreaker.isOpen(); // 觸發狀態轉換
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 記錄失敗
        circuitBreaker.recordFailure();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void reset_shouldCloseCircuit() {
        // 觸發熔斷
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 重置
        circuitBreaker.reset();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }
}
