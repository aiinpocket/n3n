package com.aiinpocket.n3n.common.config;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 流程 / 節點執行的預設 @Async 執行器。
     *
     * <p>改用「每任務一個虛擬執行緒」（virtual-thread-per-task）：節點的阻塞式 I/O
     * （HTTP / DB / AI 呼叫）不再佔用稀少的平台執行緒，彼此獨立的執行也不會爭搶固定的
     * 執行緒池槽位；避免舊有 ThreadPoolTaskExecutor（core 4 / max 16 / queue 100 +
     * CallerRunsPolicy）在飽和時把執行任務丟回呼叫端（HTTP / 排程執行緒）而拖垮請求處理。</p>
     *
     * <p><b>並行上限不受影響：</b>每使用者（10）與全域（100）的並行執行上限由
     * {@code ExecutionService} 以 DB 計數（{@code countByStatus} /
     * {@code countByStatusAndTriggeredBy}）在建立執行前主動檢查，並非依賴執行緒池拒絕任務
     * 產生的背壓，因此改用不設上限的虛擬執行緒執行器仍能維持相同的限制。</p>
     *
     * <p>Bean 名稱維持 {@code taskExecutor}（Spring @Async 的預設執行器名稱），
     * 所有既有的 @Async 參照皆可繼續解析。{@code SimpleAsyncTaskExecutor} 設定
     * {@code taskTerminationTimeout} 後，於容器關閉時會等待進行中的任務完成，維持優雅關閉。</p>
     */
    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("n3n-flow-exec-");
        executor.setVirtualThreads(true);
        // 優雅關閉：關閉時等待進行中的任務完成（最多 60 秒）
        executor.setTaskTerminationTimeout(60_000L);
        return executor;
    }

    /**
     * 雲端同步專用執行器（保留為受限的平台執行緒池，與流程執行隔離）。
     */
    @Bean(name = "cloudSyncExecutor")
    public Executor cloudSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("n3n-cloud-sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
