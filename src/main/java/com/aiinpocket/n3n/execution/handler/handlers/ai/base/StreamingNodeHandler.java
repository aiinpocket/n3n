package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import reactor.core.publisher.Flux;

/**
 * Interface for nodes that support streaming output.
 * AI nodes can implement this interface to support streaming responses.
 */
public interface StreamingNodeHandler {

    /**
     * Whether streaming output is supported
     */
    boolean supportsStreaming();

    /**
     * Execute with streaming.
     * Returns a Flux stream where each element is a StreamChunk.
     *
     * @param context execution context
     * @return streaming response
     */
    Flux<StreamChunk> executeStream(NodeExecutionContext context);
}
