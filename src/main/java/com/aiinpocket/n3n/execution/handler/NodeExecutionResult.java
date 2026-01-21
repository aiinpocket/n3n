package com.aiinpocket.n3n.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Result of node execution returned by NodeHandler.execute().
 */
@Data
@Builder
public class NodeExecutionResult {

    /**
     * Whether the execution was successful.
     */
    private boolean success;

    /**
     * Output data from the node execution.
     * This will be available to subsequent nodes.
     */
    private Map<String, Object> output;

    /**
     * Error message if execution failed.
     */
    private String errorMessage;

    /**
     * Error stack trace if execution failed.
     */
    private String errorStack;

    /**
     * For condition/switch nodes: which branches to follow.
     * If null or empty, follows all outgoing edges.
     */
    private List<String> branchesToFollow;

    /**
     * Time taken to execute this node.
     */
    private Duration executionTime;

    /**
     * Optional metadata about the execution (for debugging/logging).
     */
    private Map<String, Object> metadata;

    /**
     * Create a successful result with output.
     */
    public static NodeExecutionResult success(Map<String, Object> output) {
        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    /**
     * Create a successful result with output and metadata.
     */
    public static NodeExecutionResult success(Map<String, Object> output, Map<String, Object> metadata) {
        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .metadata(metadata)
            .build();
    }

    /**
     * Create a failed result with error message.
     */
    public static NodeExecutionResult failure(String errorMessage) {
        return NodeExecutionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * Create a failed result with error message and stack trace.
     */
    public static NodeExecutionResult failure(String errorMessage, String errorStack) {
        return NodeExecutionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorStack(errorStack)
            .build();
    }

    /**
     * Create a failed result from an exception.
     */
    public static NodeExecutionResult failure(Throwable exception) {
        return NodeExecutionResult.builder()
            .success(false)
            .errorMessage(exception.getMessage())
            .errorStack(getStackTraceAsString(exception))
            .build();
    }

    /**
     * Create a result for condition nodes with branch selection.
     */
    public static NodeExecutionResult withBranches(Map<String, Object> output, List<String> branches) {
        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .branchesToFollow(branches)
            .build();
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
