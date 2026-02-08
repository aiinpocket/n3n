package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Stream output chunk.
 * Used for AI node streaming responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {

    /**
     * Chunk type
     * - "text": text content
     * - "thinking": AI thinking process
     * - "tool_call": tool invocation
     * - "tool_result": tool execution result
     * - "progress": progress update
     * - "done": completed
     * - "error": error
     */
    private String type;

    /**
     * Text content (incremental delta)
     */
    private String content;

    /**
     * Tool name (used for tool_call type)
     */
    private String toolName;

    /**
     * Tool input JSON (used for tool_call type)
     */
    private String toolInput;

    /**
     * Tool call ID (used for tool_call/tool_result types)
     */
    private String toolCallId;

    /**
     * Progress percentage (0-100)
     */
    private Integer progress;

    /**
     * Progress stage description
     */
    private String stage;

    /**
     * Whether the stream is complete
     */
    @Builder.Default
    private boolean done = false;

    /**
     * Metadata
     */
    private Map<String, Object> metadata;

    // ===== Static factory methods =====

    public static StreamChunk text(String content) {
        return StreamChunk.builder()
            .type("text")
            .content(content)
            .build();
    }

    public static StreamChunk thinking(String content) {
        return StreamChunk.builder()
            .type("thinking")
            .content(content)
            .build();
    }

    public static StreamChunk toolCall(String toolCallId, String toolName, String toolInput) {
        return StreamChunk.builder()
            .type("tool_call")
            .toolCallId(toolCallId)
            .toolName(toolName)
            .toolInput(toolInput)
            .build();
    }

    public static StreamChunk toolResult(String toolCallId, String content) {
        return StreamChunk.builder()
            .type("tool_result")
            .toolCallId(toolCallId)
            .content(content)
            .build();
    }

    public static StreamChunk progress(int progress, String stage) {
        return StreamChunk.builder()
            .type("progress")
            .progress(progress)
            .stage(stage)
            .build();
    }

    public static StreamChunk done() {
        return StreamChunk.builder()
            .type("done")
            .done(true)
            .build();
    }

    public static StreamChunk done(Map<String, Object> metadata) {
        return StreamChunk.builder()
            .type("done")
            .done(true)
            .metadata(metadata)
            .build();
    }

    public static StreamChunk error(String message) {
        return StreamChunk.builder()
            .type("error")
            .content(message)
            .done(true)
            .build();
    }
}
