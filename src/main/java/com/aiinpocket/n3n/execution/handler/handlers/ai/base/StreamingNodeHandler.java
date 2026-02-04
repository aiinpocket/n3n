package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import reactor.core.publisher.Flux;

/**
 * 支援串流輸出的節點介面
 * AI 節點可以實現此介面以支援串流回應
 */
public interface StreamingNodeHandler {

    /**
     * 是否支援串流輸出
     */
    boolean supportsStreaming();

    /**
     * 串流執行
     * 返回 Flux 串流，每個元素為一個 StreamChunk
     *
     * @param context 執行上下文
     * @return 串流回應
     */
    Flux<StreamChunk> executeStream(NodeExecutionContext context);
}
