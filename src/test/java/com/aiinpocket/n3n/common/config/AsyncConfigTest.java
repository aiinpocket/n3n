package com.aiinpocket.n3n.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the flow-execution {@code taskExecutor} runs work on virtual threads,
 * so blocking node I/O no longer consumes scarce pooled platform threads.
 */
class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void taskExecutor_runsTasksOnVirtualThreads() throws Exception {
        AsyncTaskExecutor executor = config.taskExecutor();

        CompletableFuture<Boolean> isVirtual = new CompletableFuture<>();
        executor.execute(() -> isVirtual.complete(Thread.currentThread().isVirtual()));

        assertThat(isVirtual.get()).isTrue();
    }

    @Test
    void cloudSyncExecutor_remainsPlatformThreadPool() throws Exception {
        Executor executor = config.cloudSyncExecutor();

        CompletableFuture<Boolean> isVirtual = new CompletableFuture<>();
        executor.execute(() -> isVirtual.complete(Thread.currentThread().isVirtual()));

        // cloud sync stays on a bounded platform-thread pool, isolated from flow execution
        assertThat(isVirtual.get()).isFalse();
    }
}
