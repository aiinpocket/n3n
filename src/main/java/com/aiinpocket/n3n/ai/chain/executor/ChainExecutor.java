package com.aiinpocket.n3n.ai.chain.executor;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainContext;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Chain 執行器
 *
 * 提供 Chain 執行的管理功能，包括超時控制、非同步執行等。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChainExecutor {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 執行 Chain
     *
     * @param chain Chain
     * @param inputs 輸入
     * @return 執行結果
     */
    public ChainResult execute(Chain chain, Map<String, Object> inputs) {
        return execute(chain, inputs, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 執行 Chain（帶超時）
     *
     * @param chain Chain
     * @param inputs 輸入
     * @param timeoutSeconds 超時秒數
     * @return 執行結果
     */
    public ChainResult execute(Chain chain, Map<String, Object> inputs, int timeoutSeconds) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("Starting chain execution: {} (id: {})", chain.getName(), executionId);

        try {
            Future<ChainResult> future = executorService.submit(() -> {
                ChainContext context = ChainContext.of(inputs);
                context.setExecutionId(executionId);
                chain.invoke(context);
                return ChainResult.fromContext(context);
            });

            ChainResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);

            Duration duration = Duration.between(startTime, Instant.now());
            log.info("Chain execution completed: {} in {}ms (id: {})",
                    chain.getName(), duration.toMillis(), executionId);

            return result;

        } catch (TimeoutException e) {
            log.error("Chain execution timed out: {} (id: {})", chain.getName(), executionId);
            return ChainResult.failure("Execution timed out after " + timeoutSeconds + " seconds");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Chain execution interrupted: {} (id: {})", chain.getName(), executionId);
            return ChainResult.failure("Execution interrupted");

        } catch (ExecutionException e) {
            log.error("Chain execution failed: {} (id: {})", chain.getName(), executionId, e.getCause());
            return ChainResult.failure("Execution failed: " + e.getCause().getMessage());
        }
    }

    /**
     * 非同步執行 Chain
     *
     * @param chain Chain
     * @param inputs 輸入
     * @return CompletableFuture
     */
    public CompletableFuture<ChainResult> executeAsync(Chain chain, Map<String, Object> inputs) {
        return CompletableFuture.supplyAsync(() -> execute(chain, inputs), executorService);
    }

    /**
     * 非同步執行 Chain（帶超時）
     *
     * @param chain Chain
     * @param inputs 輸入
     * @param timeoutSeconds 超時秒數
     * @return CompletableFuture
     */
    public CompletableFuture<ChainResult> executeAsync(Chain chain, Map<String, Object> inputs,
                                                        int timeoutSeconds) {
        return CompletableFuture.supplyAsync(() -> execute(chain, inputs, timeoutSeconds), executorService);
    }

    /**
     * 批次執行 Chain
     *
     * @param chain Chain
     * @param batchInputs 批次輸入
     * @return 批次結果
     */
    public Map<String, ChainResult> executeBatch(Chain chain, Map<String, Map<String, Object>> batchInputs) {
        Map<String, CompletableFuture<ChainResult>> futures = new ConcurrentHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : batchInputs.entrySet()) {
            futures.put(entry.getKey(), executeAsync(chain, entry.getValue()));
        }

        Map<String, ChainResult> results = new ConcurrentHashMap<>();
        for (Map.Entry<String, CompletableFuture<ChainResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                results.put(entry.getKey(), ChainResult.failure("Batch execution failed: " + e.getMessage()));
            }
        }

        return results;
    }

    /**
     * 執行 Chain（簡化版）
     *
     * @param chain Chain
     * @param input 單一輸入
     * @return 輸出文字
     */
    public String run(Chain chain, String input) {
        ChainResult result = execute(chain, Map.of("input", input));
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getError());
        }
        return result.getOutput();
    }
}
