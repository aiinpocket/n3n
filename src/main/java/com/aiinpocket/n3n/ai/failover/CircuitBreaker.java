package com.aiinpocket.n3n.ai.failover;

import lombok.extern.slf4j.Slf4j;

// Thread-safe via synchronized(lock) on all state access

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

    private int failureCount = 0;
    private int successCount = 0;
    private long openedAt = 0;
    private State state = State.CLOSED;
    private final Object lock = new Object();

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
        synchronized (lock) {
            if (state == State.CLOSED) {
                return false;
            }

            if (state == State.OPEN) {
                long elapsed = System.currentTimeMillis() - openedAt;
                if (elapsed > resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    log.info("Circuit breaker [{}] transitioning to HALF_OPEN after {}ms", name, elapsed);
                    return false;
                }
                return true;
            }

            // HALF_OPEN: 允許一個請求
            return false;
        }
    }

    /**
     * 記錄成功
     */
    public void recordSuccess() {
        synchronized (lock) {
            if (state == State.HALF_OPEN) {
                resetInternal();
                log.info("Circuit breaker [{}] reset to CLOSED after successful request", name);
            } else if (state == State.CLOSED) {
                failureCount = 0;
                successCount++;
            }
        }
    }

    /**
     * 記錄失敗
     */
    public void recordFailure() {
        synchronized (lock) {
            if (state == State.HALF_OPEN) {
                trip();
                log.warn("Circuit breaker [{}] re-opened after failed test request", name);
            } else if (state == State.CLOSED) {
                failureCount++;
                if (failureCount >= threshold) {
                    trip();
                    log.warn("Circuit breaker [{}] opened after {} consecutive failures", name, failureCount);
                }
            }
        }
    }

    /**
     * 觸發熔斷
     */
    private void trip() {
        state = State.OPEN;
        openedAt = System.currentTimeMillis();
        failureCount = 0;
    }

    /**
     * 重置熔斷器
     */
    public void reset() {
        synchronized (lock) {
            resetInternal();
        }
    }

    private void resetInternal() {
        state = State.CLOSED;
        failureCount = 0;
        successCount = 0;
        openedAt = 0;
    }

    /**
     * 取得當前狀態
     */
    public State getState() {
        synchronized (lock) {
            // 檢查是否需要轉換狀態
            isOpen();
            return state;
        }
    }

    /**
     * 取得失敗次數
     */
    public int getFailureCount() {
        synchronized (lock) {
            return failureCount;
        }
    }

    /**
     * 取得成功次數
     */
    public int getSuccessCount() {
        synchronized (lock) {
            return successCount;
        }
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return String.format("CircuitBreaker[name=%s, state=%s, failures=%d, threshold=%d]",
                name, state, failureCount, threshold);
        }
    }
}
