package com.aiinpocket.n3n.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StateManager {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String EXECUTION_PREFIX = "execution:";
    private static final String NODE_STATE_PREFIX = "node_state:";
    private static final String EXECUTION_OUTPUT_PREFIX = "execution_output:";
    private static final Duration STATE_TTL = Duration.ofHours(24);

    // Execution state management

    public void initExecution(UUID executionId, Map<String, Object> definition) {
        String key = EXECUTION_PREFIX + executionId;
        Map<String, Object> state = new HashMap<>();
        state.put("status", "pending");
        state.put("definition", definition);
        state.put("currentNodes", new java.util.ArrayList<>());
        state.put("completedNodes", new java.util.ArrayList<>());
        state.put("failedNodes", new java.util.ArrayList<>());

        redisTemplate.opsForHash().putAll(key, state);
        redisTemplate.expire(key, STATE_TTL);

        log.debug("Initialized execution state: {}", executionId);
    }

    public void updateExecutionStatus(UUID executionId, String status) {
        String key = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().put(key, "status", status);
        log.debug("Updated execution status: {} -> {}", executionId, status);
    }

    public String getExecutionStatus(UUID executionId) {
        String key = EXECUTION_PREFIX + executionId;
        Object status = redisTemplate.opsForHash().get(key, "status");
        return status != null ? status.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionState(UUID executionId) {
        String key = EXECUTION_PREFIX + executionId;
        Map<Object, Object> rawState = redisTemplate.opsForHash().entries(key);
        Map<String, Object> state = new HashMap<>();
        rawState.forEach((k, v) -> state.put(k.toString(), v));
        return state;
    }

    public void markNodeStarted(UUID executionId, String nodeId) {
        String key = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().put(key + ":current_nodes", nodeId, System.currentTimeMillis());
        log.debug("Node started: execution={}, node={}", executionId, nodeId);
    }

    public void markNodeCompleted(UUID executionId, String nodeId, Map<String, Object> output) {
        String execKey = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().delete(execKey + ":current_nodes", nodeId);
        redisTemplate.opsForHash().put(execKey + ":completed_nodes", nodeId, System.currentTimeMillis());

        // Store node output
        String outputKey = NODE_STATE_PREFIX + executionId + ":" + nodeId;
        redisTemplate.opsForHash().putAll(outputKey, output != null ? output : Map.of());
        redisTemplate.expire(outputKey, STATE_TTL);

        log.debug("Node completed: execution={}, node={}", executionId, nodeId);
    }

    public void markNodeFailed(UUID executionId, String nodeId, String errorMessage) {
        String execKey = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().delete(execKey + ":current_nodes", nodeId);
        redisTemplate.opsForHash().put(execKey + ":failed_nodes", nodeId, errorMessage);
        log.debug("Node failed: execution={}, node={}, error={}", executionId, nodeId, errorMessage);
    }

    /**
     * Mark a node as waiting (paused for external input).
     */
    public void markNodeWaiting(UUID executionId, String nodeId, String pauseReason) {
        String execKey = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().delete(execKey + ":current_nodes", nodeId);
        redisTemplate.opsForHash().put(execKey + ":waiting_nodes", nodeId, pauseReason);
        log.debug("Node waiting: execution={}, node={}, reason={}", executionId, nodeId, pauseReason);
    }

    /**
     * Get list of completed node IDs for an execution.
     */
    @SuppressWarnings("unchecked")
    public java.util.Set<String> getCompletedNodes(UUID executionId) {
        String key = EXECUTION_PREFIX + executionId + ":completed_nodes";
        java.util.Set<Object> keys = redisTemplate.opsForHash().keys(key);
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        if (keys != null) {
            keys.forEach(k -> nodeIds.add(k.toString()));
        }
        return nodeIds;
    }

    /**
     * Get the waiting node ID for an execution, if any.
     */
    @SuppressWarnings("unchecked")
    public String getWaitingNodeId(UUID executionId) {
        String key = EXECUTION_PREFIX + executionId + ":waiting_nodes";
        java.util.Set<Object> keys = redisTemplate.opsForHash().keys(key);
        if (keys != null && !keys.isEmpty()) {
            return keys.iterator().next().toString();
        }
        return null;
    }

    /**
     * Clear waiting status for a node (when resuming).
     */
    public void clearNodeWaiting(UUID executionId, String nodeId) {
        String execKey = EXECUTION_PREFIX + executionId;
        redisTemplate.opsForHash().delete(execKey + ":waiting_nodes", nodeId);
        log.debug("Cleared waiting status: execution={}, node={}", executionId, nodeId);
    }

    // Resume data management

    private static final String RESUME_DATA_PREFIX = "resume_data:";

    /**
     * Store resume data for a paused execution.
     */
    public void setResumeData(UUID executionId, String nodeId, Map<String, Object> resumeData) {
        String key = RESUME_DATA_PREFIX + executionId + ":" + nodeId;
        redisTemplate.opsForHash().putAll(key, resumeData);
        redisTemplate.expire(key, STATE_TTL);
        log.debug("Stored resume data: execution={}, node={}", executionId, nodeId);
    }

    /**
     * Retrieve resume data for a paused execution.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getResumeData(UUID executionId, String nodeId) {
        String key = RESUME_DATA_PREFIX + executionId + ":" + nodeId;
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(key);
        Map<String, Object> data = new HashMap<>();
        rawData.forEach((k, v) -> data.put(k.toString(), v));
        return data;
    }

    /**
     * Clear resume data after successful resume.
     */
    public void clearResumeData(UUID executionId, String nodeId) {
        String key = RESUME_DATA_PREFIX + executionId + ":" + nodeId;
        redisTemplate.delete(key);
        log.debug("Cleared resume data: execution={}, node={}", executionId, nodeId);
    }

    /**
     * Store partial node outputs when pausing (to restore context on resume).
     */
    public void setPartialNodeOutputs(UUID executionId, Map<String, Object> nodeOutputs) {
        String key = EXECUTION_PREFIX + executionId + ":partial_outputs";
        if (nodeOutputs != null && !nodeOutputs.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, nodeOutputs);
            redisTemplate.expire(key, STATE_TTL);
        }
        log.debug("Stored partial outputs: execution={}, nodeCount={}", executionId, nodeOutputs != null ? nodeOutputs.size() : 0);
    }

    /**
     * Retrieve partial node outputs for resume.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPartialNodeOutputs(UUID executionId) {
        String key = EXECUTION_PREFIX + executionId + ":partial_outputs";
        Map<Object, Object> rawOutputs = redisTemplate.opsForHash().entries(key);
        Map<String, Object> outputs = new HashMap<>();
        rawOutputs.forEach((k, v) -> outputs.put(k.toString(), v));
        return outputs;
    }

    // Node output management

    public void setNodeOutput(UUID executionId, String nodeId, Map<String, Object> output) {
        String key = NODE_STATE_PREFIX + executionId + ":" + nodeId;
        redisTemplate.opsForHash().putAll(key, output);
        redisTemplate.expire(key, STATE_TTL);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getNodeOutput(UUID executionId, String nodeId) {
        String key = NODE_STATE_PREFIX + executionId + ":" + nodeId;
        Map<Object, Object> rawOutput = redisTemplate.opsForHash().entries(key);
        Map<String, Object> output = new HashMap<>();
        rawOutput.forEach((k, v) -> output.put(k.toString(), v));
        return output;
    }

    // Execution result management

    public void setExecutionOutput(UUID executionId, Map<String, Object> output) {
        String key = EXECUTION_OUTPUT_PREFIX + executionId;
        redisTemplate.opsForHash().putAll(key, output);
        redisTemplate.expire(key, STATE_TTL);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionOutput(UUID executionId) {
        String key = EXECUTION_OUTPUT_PREFIX + executionId;
        Map<Object, Object> rawOutput = redisTemplate.opsForHash().entries(key);
        Map<String, Object> output = new HashMap<>();
        rawOutput.forEach((k, v) -> output.put(k.toString(), v));
        return output;
    }

    // Cleanup

    public void cleanupExecution(UUID executionId) {
        String pattern = "*:" + executionId + "*";
        // Note: In production, use SCAN instead of KEYS for large datasets
        var keys = redisTemplate.keys(EXECUTION_PREFIX + executionId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys(NODE_STATE_PREFIX + executionId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys(EXECUTION_OUTPUT_PREFIX + executionId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.debug("Cleaned up execution state: {}", executionId);
    }
}
