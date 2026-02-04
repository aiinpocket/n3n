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

    // ===== Pause/Resume Support =====

    /**
     * Whether the node requests to pause execution.
     * When true, the execution engine will pause and wait for external resume.
     */
    private boolean pauseRequested;

    /**
     * Reason for pausing (displayed to users).
     * Examples: "Waiting for approval", "Waiting for form submission"
     */
    private String pauseReason;

    /**
     * Conditions required to resume execution.
     * Examples:
     * - For approval: {"type": "approval", "approvalId": "uuid"}
     * - For form: {"type": "form", "formSchema": {...}}
     */
    private Map<String, Object> resumeCondition;

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

    /**
     * Create a pause result to suspend execution and wait for external event.
     * The execution will be marked as 'waiting' until resumed.
     *
     * @param reason Human-readable reason for the pause
     * @param resumeCondition Conditions that define how/when to resume
     * @return NodeExecutionResult configured to pause execution
     */
    public static NodeExecutionResult pause(String reason, Map<String, Object> resumeCondition) {
        return NodeExecutionResult.builder()
            .success(true)
            .pauseRequested(true)
            .pauseReason(reason)
            .resumeCondition(resumeCondition)
            .build();
    }

    /**
     * Create a pause result with output data preserved.
     * Useful when partial results should be available after resume.
     *
     * @param reason Human-readable reason for the pause
     * @param resumeCondition Conditions that define how/when to resume
     * @param partialOutput Output data generated before pause
     * @return NodeExecutionResult configured to pause execution with partial output
     */
    public static NodeExecutionResult pause(String reason, Map<String, Object> resumeCondition, Map<String, Object> partialOutput) {
        return NodeExecutionResult.builder()
            .success(true)
            .pauseRequested(true)
            .pauseReason(reason)
            .resumeCondition(resumeCondition)
            .output(partialOutput)
            .build();
    }

    /**
     * Check if this result requests execution pause.
     */
    public boolean isPauseRequested() {
        return pauseRequested;
    }

    private static String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
