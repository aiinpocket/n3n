package com.aiinpocket.n3n.gateway.controller;

import com.aiinpocket.n3n.gateway.node.NodeConnection;
import com.aiinpocket.n3n.gateway.node.NodeInvoker;
import com.aiinpocket.n3n.gateway.node.NodeRegistry;
import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST API for managing connected local agents.
 */
@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
@Tag(name = "Gateway", description = "Local agent gateway management")
public class GatewayController {

    private final NodeRegistry nodeRegistry;
    private final NodeInvoker nodeInvoker;
    private final AgentPairingService agentPairingService;

    /**
     * Get all connected nodes for the current user
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<NodeInfo>> getNodes(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = getUserId(userDetails);

        List<NodeInfo> nodes = nodeRegistry.getConnectionsForUser(userId).stream()
            .map(this::toNodeInfo)
            .collect(Collectors.toList());

        return ResponseEntity.ok(nodes);
    }

    /**
     * Get a specific node by connection ID
     */
    @GetMapping("/nodes/{connectionId}")
    public ResponseEntity<NodeInfo> getNode(
            @PathVariable String connectionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);

        return nodeRegistry.getConnection(connectionId)
            .filter(conn -> conn.getUserId().equals(userId))
            .map(this::toNodeInfo)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Invoke a capability on a connected node
     */
    @PostMapping("/nodes/{connectionId}/invoke")
    public CompletableFuture<ResponseEntity<InvokeResponse>> invoke(
            @PathVariable String connectionId,
            @Valid @RequestBody InvokeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);

        // Verify node belongs to user
        return nodeRegistry.getConnection(connectionId)
            .filter(conn -> conn.getUserId().equals(userId))
            .map(conn -> nodeInvoker.invoke(connectionId, request.capability(), request.args())
                .thenApply(result -> {
                    if (result.success()) {
                        return ResponseEntity.ok(new InvokeResponse(true, result.data(), null));
                    } else {
                        return ResponseEntity.badRequest()
                            .body(new InvokeResponse(false, null, result.errorMessage()));
                    }
                }))
            .orElse(CompletableFuture.completedFuture(ResponseEntity.notFound().build()));
    }

    /**
     * Invoke a capability on any available node
     */
    @PostMapping("/invoke")
    public CompletableFuture<ResponseEntity<InvokeResponse>> invokeAny(
            @Valid @RequestBody InvokeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = getUserId(userDetails);

        return nodeInvoker.invokeForUser(userId, request.capability(), request.args())
            .thenApply(result -> {
                if (result.success()) {
                    return ResponseEntity.ok(new InvokeResponse(true, result.data(), null));
                } else {
                    return ResponseEntity.badRequest()
                        .body(new InvokeResponse(false, null, result.errorMessage()));
                }
            });
    }

    /**
     * Get available capabilities for the current user
     */
    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getCapabilities(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = getUserId(userDetails);
        return ResponseEntity.ok(nodeInvoker.getAvailableCapabilities(userId));
    }

    /**
     * Get gateway statistics (admin only)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(nodeRegistry.getStats());
    }

    /**
     * Generate a pairing code for connecting a new device
     */
    @PostMapping("/pairing-code")
    public ResponseEntity<PairingCodeResponse> generatePairingCode(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = getUserId(userDetails);

        AgentPairingService.PairingInitiation pairing = agentPairingService.initiatePairing(userId);

        return ResponseEntity.ok(new PairingCodeResponse(pairing.pairingCode(), 300));
    }

    private NodeInfo toNodeInfo(NodeConnection conn) {
        return new NodeInfo(
            conn.getConnectionId(),
            conn.getDevice().getDisplayName(),
            conn.getDevice().getPlatform(),
            conn.getDevice().getVersion(),
            conn.getCapabilities(),
            conn.getStatus().name(),
            conn.getConnectedAt().toString(),
            conn.getLastActiveAt().toString(),
            conn.getLatencyMs()
        );
    }

    private UUID getUserId(UserDetails userDetails) {
        // In production, extract UUID from UserDetails
        // For now, use username as UUID source
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            // Generate deterministic UUID from username
            return UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes());
        }
    }

    // DTOs
    public record NodeInfo(
        String connectionId,
        String displayName,
        String platform,
        String version,
        List<String> capabilities,
        String status,
        String connectedAt,
        String lastActiveAt,
        long latencyMs
    ) {}

    public record InvokeRequest(
        String capability,
        Map<String, Object> args
    ) {}

    public record InvokeResponse(
        boolean success,
        Map<String, Object> data,
        String error
    ) {}

    public record PairingCodeResponse(
        String code,
        int expiresInSeconds
    ) {}
}
