package com.aiinpocket.n3n.gateway.node;

import com.aiinpocket.n3n.gateway.handler.GatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.protocol.GatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for invoking capabilities on connected local agents.
 * Used by flow execution to call node.invoke on local agents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NodeInvoker {

    private final NodeRegistry nodeRegistry;
    private final GatewayWebSocketHandler gatewayHandler;

    /**
     * Invoke a capability on a specific connection
     */
    public CompletableFuture<InvokeResult> invoke(String connectionId, String capability, Map<String, Object> args) {
        return gatewayHandler.invokeCapability(connectionId, capability, args)
            .thenApply(this::toInvokeResult);
    }

    /**
     * Invoke a capability on a specific connection with timeout
     */
    public CompletableFuture<InvokeResult> invoke(String connectionId, String capability, Map<String, Object> args, long timeoutMs) {
        return gatewayHandler.invokeCapability(connectionId, capability, args)
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .thenApply(this::toInvokeResult)
            .exceptionally(e -> InvokeResult.error("TIMEOUT", "Request timed out after " + timeoutMs + "ms"));
    }

    /**
     * Invoke a capability on any node of a user that has the capability
     */
    public CompletableFuture<InvokeResult> invokeForUser(UUID userId, String capability, Map<String, Object> args) {
        Optional<NodeConnection> connection = nodeRegistry.findNodeWithCapability(userId, capability);

        if (connection.isEmpty()) {
            return CompletableFuture.completedFuture(
                InvokeResult.error("NO_NODE_AVAILABLE", "No connected node with capability: " + capability)
            );
        }

        return invoke(connection.get().getConnectionId(), capability, args);
    }

    /**
     * Invoke a capability on a specific platform node of a user
     */
    public CompletableFuture<InvokeResult> invokeOnPlatform(UUID userId, String platform, String capability, Map<String, Object> args) {
        Optional<NodeConnection> connection = nodeRegistry.findNodeByPlatform(userId, platform);

        if (connection.isEmpty()) {
            return CompletableFuture.completedFuture(
                InvokeResult.error("NO_NODE_AVAILABLE", "No connected " + platform + " node")
            );
        }

        if (!connection.get().hasCapability(capability)) {
            return CompletableFuture.completedFuture(
                InvokeResult.error("CAPABILITY_NOT_FOUND", "Node does not have capability: " + capability)
            );
        }

        return invoke(connection.get().getConnectionId(), capability, args);
    }

    /**
     * Check if a user has a node with a specific capability
     */
    public boolean hasCapability(UUID userId, String capability) {
        return nodeRegistry.findNodeWithCapability(userId, capability).isPresent();
    }

    /**
     * Get available capabilities for a user
     */
    public Map<String, Object> getAvailableCapabilities(UUID userId) {
        return nodeRegistry.getConnectionsForUser(userId).stream()
            .collect(java.util.stream.Collectors.toMap(
                conn -> conn.getConnectionId(),
                conn -> Map.of(
                    "deviceName", conn.getDevice().getDisplayName(),
                    "platform", conn.getDevice().getPlatform(),
                    "capabilities", conn.getCapabilities()
                )
            ));
    }

    private InvokeResult toInvokeResult(GatewayResponse response) {
        if (response.isOk()) {
            return InvokeResult.success(response.getPayload());
        } else {
            GatewayResponse.GatewayError error = response.getError();
            return InvokeResult.error(
                error != null ? error.getCode() : "UNKNOWN",
                error != null ? error.getMessage() : "Unknown error"
            );
        }
    }

    /**
     * Result of a node invocation
     */
    public record InvokeResult(
        boolean success,
        Map<String, Object> data,
        String errorCode,
        String errorMessage
    ) {
        public static InvokeResult success(Map<String, Object> data) {
            return new InvokeResult(true, data, null, null);
        }

        public static InvokeResult error(String code, String message) {
            return new InvokeResult(false, null, code, message);
        }
    }
}
