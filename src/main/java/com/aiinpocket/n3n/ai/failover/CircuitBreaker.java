package com.aiinpocket.n3n.ai.failover;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 簡單的熔斷器實作
 *
 * 狀態：
 * - CLOSED: 正常運行
 * - OPEN: 熔斷開啟，拒絕請求
 * - HALF_OPEN: 半開狀態，允許一個測試請求
 */
@Slf4j
public class CircuitBreaker {

    private final String name;
    private final int threshold;
    private final long resetTimeoutMs;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private volatile State state = State.CLOSED;

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public CircuitBreaker(String name, int threshold, long resetTimeoutMs) {
        this.name = name;
        this.threshold = threshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    /**
     * 檢查熔斷器是否開啟
     */
    public boolean isOpen() {
        if (state == State.CLOSED) {
            return false;
        }

        if (state == State.OPEN) {
            long elapsed = System.currentTimeMillis() - openedAt.get();
            if (elapsed > resetTimeoutMs) {
                // 轉換到半開狀態
                state = State.HALF_OPEN;
                log.info("Circuit breaker [{}] transitioning to HALF_OPEN after {}ms", name, elapsed);
                return false;
            }
            return true;
        }

        // HALF_OPEN: 允許一個請求
        return false;
    }

    /**
     * 記錄成功
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            // 半開狀態下成功，重置熔斷器
            reset();
            log.info("Circuit breaker [{}] reset to CLOSED after successful request", name);
        } else if (state == State.CLOSED) {
            // 連續成功時重置失敗計數
            failureCount.set(0);
            successCount.incrementAndGet();
        }
    }

    /**
     * 記錄失敗
     */
    public void recordFailure() {
        if (state == State.HALF_OPEN) {
            // 半開狀態下失敗，重新開啟熔斷器
            trip();
            log.warn("Circuit breaker [{}] re-opened after failed test request", name);
        } else if (state == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= threshold) {
                trip();
                log.warn("Circuit breaker [{}] opened after {} consecutive failures", name, failures);
            }
        }
    }

    /**
     * 觸發熔斷
     */
    private void trip() {
        state = State.OPEN;
        openedAt.set(System.currentTimeMillis());
        failureCount.set(0);
    }

    /**
     * 重置熔斷器
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        openedAt.set(0);
    }

    /**
     * 取得當前狀態
     */
    public State getState() {
        // 檢查是否需要轉換狀態
        isOpen();
        return state;
    }

    /**
     * 取得失敗次數
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 取得成功次數
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    @Override
    public String toString() {
        return String.format("CircuitBreaker[name=%s, state=%s, failures=%d, threshold=%d]",
            name, state, failureCount.get(), threshold);
    }
}
