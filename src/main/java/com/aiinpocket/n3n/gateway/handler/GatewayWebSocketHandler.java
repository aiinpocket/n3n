package com.aiinpocket.n3n.gateway.handler;

import com.aiinpocket.n3n.auth.service.JwtService;
import com.aiinpocket.n3n.gateway.node.NodeConnection;
import com.aiinpocket.n3n.gateway.node.NodeRegistry;
import com.aiinpocket.n3n.gateway.protocol.GatewayEvent;
import com.aiinpocket.n3n.gateway.protocol.GatewayMessage;
import com.aiinpocket.n3n.gateway.protocol.GatewayRequest;
import com.aiinpocket.n3n.gateway.protocol.GatewayResponse;
import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for the Gateway protocol.
 * Handles connections from local agents and routes messages.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GatewayWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final NodeRegistry nodeRegistry;
    private final JwtService jwtService;
    private final AgentPairingService agentPairingService;
    private final DeviceKeyStore deviceKeyStore;

    /**
     * Pending requests waiting for responses (bounded to prevent memory leaks)
     */
    private static final int MAX_PENDING_REQUESTS = 10_000;
    private final Map<String, CompletableFuture<GatewayResponse>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Session to connection ID mapping
     */
    private final Map<String, String> sessionToConnection = new ConcurrentHashMap<>();

    /**
     * Authentication challenges in progress
     */
    private final Map<String, AuthChallenge> authChallenges = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        log.info("New WebSocket connection: {}", sessionId);

        // Send authentication challenge
        String nonce = UUID.randomUUID().toString();
        authChallenges.put(sessionId, new AuthChallenge(nonce, Instant.now()));

        GatewayEvent challenge = GatewayEvent.create(GatewayEvent.CONNECT_CHALLENGE, Map.of(
            "nonce", nonce,
            "ts", System.currentTimeMillis()
        ));

        sendMessage(session, challenge);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        try {
            GatewayMessage gatewayMessage = objectMapper.readValue(payload, GatewayMessage.class);

            switch (gatewayMessage.getType()) {
                case "req" -> handleRequest(session, (GatewayRequest) gatewayMessage);
                case "res" -> handleResponse((GatewayResponse) gatewayMessage);
                case "event" -> handleEvent(session, (GatewayEvent) gatewayMessage);
                default -> log.warn("Unknown message type: {}", gatewayMessage.getType());
            }

            // Update last activity
            String connectionId = sessionToConnection.get(sessionId);
            if (connectionId != null) {
                nodeRegistry.touch(connectionId);
            }

        } catch (Exception e) {
            log.error("Error processing message from {}: {}", sessionId, e.getMessage());
            sendError(session, null, "PARSE_ERROR", "Failed to parse message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String connectionId = sessionToConnection.remove(sessionId);

        if (connectionId != null) {
            nodeRegistry.unregister(connectionId);
        }

        authChallenges.remove(sessionId);
        log.info("WebSocket connection closed: {} (status: {})", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
    }

    private void handleRequest(WebSocketSession session, GatewayRequest request) throws IOException {
        String method = request.getMethod();
        Map<String, Object> params = request.getParams();

        log.debug("Received request: method={}, id={}", method, request.getId());

        try {
            GatewayResponse response = switch (method) {
                case "connect" -> handleConnect(session, params, request.getId());
                case "node.register" -> handleNodeRegister(session, params, request.getId());
                case "node.invoke.result" -> handleInvokeResult(session, params, request.getId());
                case "ping" -> GatewayResponse.success(request.getId(), Map.of("pong", System.currentTimeMillis()));
                default -> {
                    log.warn("Unknown method: {}", method);
                    yield GatewayResponse.error(request.getId(), "UNKNOWN_METHOD", "Unknown method: " + method);
                }
            };

            sendMessage(session, response);

        } catch (Exception e) {
            log.error("Error handling request {}: {}", method, e.getMessage());
            sendError(session, request.getId(), "INTERNAL_ERROR", "An internal error occurred");
        }
    }

    private void handleResponse(GatewayResponse response) {
        String requestId = response.getId();
        CompletableFuture<GatewayResponse> future = pendingRequests.remove(requestId);

        if (future != null) {
            future.complete(response);
        } else {
            log.warn("Received response for unknown request: {}", requestId);
        }
    }

    private void handleEvent(WebSocketSession session, GatewayEvent event) {
        log.debug("Received event: {}", event.getEvent());

        // Handle specific events if needed
        if (GatewayEvent.PONG.equals(event.getEvent())) {
            String connectionId = sessionToConnection.get(session.getId());
            if (connectionId != null) {
                // Calculate latency
                Long sentTs = (Long) event.getPayload().get("sentTs");
                if (sentTs != null) {
                    long latency = System.currentTimeMillis() - sentTs;
                    nodeRegistry.getConnection(connectionId)
                        .ifPresent(conn -> conn.setLatencyMs(latency));
                }
            }
        }
    }

    private GatewayResponse handleConnect(WebSocketSession session, Map<String, Object> params, String requestId) {
        String sessionId = session.getId();

        // Validate challenge
        AuthChallenge challenge = authChallenges.remove(sessionId);
        if (challenge == null) {
            return GatewayResponse.error(requestId, "AUTH_ERROR", "No authentication challenge found");
        }

        // Check challenge timeout (5 minutes)
        if (challenge.createdAt.plusSeconds(300).isBefore(Instant.now())) {
            return GatewayResponse.error(requestId, "AUTH_TIMEOUT", "Authentication challenge expired");
        }

        // Extract client info
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) params.get("client");
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) params.get("auth");
        @SuppressWarnings("unchecked")
        List<String> caps = (List<String>) params.get("caps");

        if (client == null) {
            return GatewayResponse.error(requestId, "INVALID_PARAMS", "Missing client information");
        }

        // Authenticate
        UUID userId = null;
        String deviceToken = null;

        if (auth != null) {
            // Try device token first
            deviceToken = (String) auth.get("deviceToken");
            if (deviceToken != null) {
                // Validate device token (simplified - in production, verify with database)
                userId = validateDeviceToken(deviceToken);
            }

            // Try user token
            if (userId == null) {
                String userToken = (String) auth.get("userToken");
                if (userToken != null) {
                    userId = validateUserToken(userToken);
                }
            }

            // Try pairing code
            if (userId == null) {
                String pairingCode = (String) auth.get("pairingCode");
                if (pairingCode != null) {
                    userId = validatePairingCode(pairingCode);
                }
            }
        }

        if (userId == null) {
            return GatewayResponse.error(requestId, "AUTH_FAILED", "Authentication failed");
        }

        // Create connection
        String connectionId = UUID.randomUUID().toString();

        NodeConnection.DeviceInfo deviceInfo = NodeConnection.DeviceInfo.builder()
            .id((String) client.get("id"))
            .displayName((String) client.get("displayName"))
            .version((String) client.get("version"))
            .platform((String) client.get("platform"))
            .arch((String) client.get("arch"))
            .instanceId((String) client.get("instanceId"))
            .build();

        NodeConnection connection = NodeConnection.builder()
            .connectionId(connectionId)
            .userId(userId)
            .device(deviceInfo)
            .capabilities(caps != null ? caps : Collections.emptyList())
            .session(session)
            .status(NodeConnection.ConnectionStatus.CONNECTED)
            .connectedAt(Instant.now())
            .lastActiveAt(Instant.now())
            .build();

        // Generate new device token if needed
        if (deviceToken == null) {
            deviceToken = generateDeviceToken(userId, deviceInfo.getId());
        }
        connection.setDeviceToken(deviceToken);

        nodeRegistry.register(connection);
        sessionToConnection.put(sessionId, connectionId);

        return GatewayResponse.success(requestId, Map.of(
            "deviceToken", deviceToken,
            "connectionId", connectionId,
            "role", "agent",
            "scopes", List.of("node.invoke", "node.register")
        ));
    }

    private GatewayResponse handleNodeRegister(WebSocketSession session, Map<String, Object> params, String requestId) {
        String sessionId = session.getId();
        String connectionId = sessionToConnection.get(sessionId);

        if (connectionId == null) {
            return GatewayResponse.error(requestId, "NOT_CONNECTED", "Not connected");
        }

        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) params.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) params.get("permissions");

        nodeRegistry.getConnection(connectionId).ifPresent(conn -> {
            if (capabilities != null) {
                conn.setCapabilities(capabilities);
            }
            // Update permissions if provided
        });

        log.info("Node capabilities registered: connectionId={}, capabilities={}", connectionId, capabilities);

        return GatewayResponse.success(requestId, Map.of(
            "nodeId", connectionId,
            "registered", true
        ));
    }

    private GatewayResponse handleInvokeResult(WebSocketSession session, Map<String, Object> params, String requestId) {
        String invokeId = (String) params.get("invokeId");

        CompletableFuture<GatewayResponse> future = pendingRequests.remove(invokeId);
        if (future != null) {
            future.complete(GatewayResponse.success(invokeId, params));
        }

        return GatewayResponse.success(requestId, Map.of("received", true));
    }

    /**
     * Invoke a capability on a connected node
     */
    public CompletableFuture<GatewayResponse> invokeCapability(String connectionId, String capability, Map<String, Object> args) {
        return nodeRegistry.getConnection(connectionId)
            .map(connection -> {
                if (!connection.isActive()) {
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error("", "CONNECTION_INACTIVE", "Node connection is not active")
                    );
                }

                if (!connection.hasCapability(capability)) {
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error("", "CAPABILITY_NOT_FOUND", "Node does not have capability: " + capability)
                    );
                }

                // Reject if too many pending requests to prevent memory exhaustion
                if (pendingRequests.size() >= MAX_PENDING_REQUESTS) {
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error("", "TOO_MANY_REQUESTS", "Too many pending requests")
                    );
                }

                GatewayRequest request = GatewayRequest.create("node.invoke", Map.of(
                    "capability", capability,
                    "args", args
                ));

                CompletableFuture<GatewayResponse> future = new CompletableFuture<>();
                pendingRequests.put(request.getId(), future);

                try {
                    sendMessage(connection.getSession(), request);
                } catch (IOException e) {
                    pendingRequests.remove(request.getId());
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error(request.getId(), "SEND_ERROR", e.getMessage())
                    );
                }

                // Set timeout and clean up pending entry on completion
                return future.orTimeout(30, TimeUnit.SECONDS)
                    .whenComplete((res, ex) -> pendingRequests.remove(request.getId()))
                    .exceptionally(e -> GatewayResponse.error(request.getId(), "TIMEOUT", "Request timed out"));
            })
            .orElse(CompletableFuture.completedFuture(
                GatewayResponse.error("", "CONNECTION_NOT_FOUND", "Connection not found: " + connectionId)
            ));
    }

    /**
     * Send a message to a WebSocket session
     */
    public void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Send an error response
     */
    private void sendError(WebSocketSession session, String requestId, String code, String message) throws IOException {
        GatewayResponse response = GatewayResponse.error(requestId != null ? requestId : "", code, message);
        sendMessage(session, response);
    }

    /**
     * Broadcast an event to all connected nodes
     */
    public void broadcastEvent(GatewayEvent event) {
        for (NodeConnection connection : nodeRegistry.getAllConnections()) {
            try {
                if (connection.isActive()) {
                    sendMessage(connection.getSession(), event);
                }
            } catch (IOException e) {
                log.warn("Failed to broadcast to connection {}: {}", connection.getConnectionId(), e.getMessage());
            }
        }
    }

    /**
     * Broadcast an event to all nodes of a specific user
     */
    public void broadcastToUser(UUID userId, GatewayEvent event) {
        for (NodeConnection connection : nodeRegistry.getConnectionsForUser(userId)) {
            try {
                if (connection.isActive()) {
                    sendMessage(connection.getSession(), event);
                }
            } catch (IOException e) {
                log.warn("Failed to broadcast to connection {}: {}", connection.getConnectionId(), e.getMessage());
            }
        }
    }

    private UUID validateDeviceToken(String token) {
        try {
            return agentPairingService.validateDeviceToken(token).orElse(null);
        } catch (Exception e) {
            log.debug("Device token validation failed: {}", e.getMessage());
            return null;
        }
    }

    private UUID validateUserToken(String token) {
        try {
            if (jwtService.validateToken(token)) {
                return jwtService.extractUserId(token);
            }
        } catch (Exception e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
        }
        return null;
    }

    private UUID validatePairingCode(String code) {
        try {
            Optional<DeviceKeyStore.PairingRequest> pairing = deviceKeyStore.consumePairing(code);
            if (pairing.isPresent() && pairing.get().getExpiresAt().isAfter(Instant.now())) {
                return pairing.get().getUserId();
            }
        } catch (Exception e) {
            log.debug("Pairing code validation failed: {}", e.getMessage());
        }
        return null;
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private String generateDeviceToken(UUID userId, String deviceId) {
        long timestamp = System.currentTimeMillis();
        // Use cryptographically secure random bytes instead of UUID
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String signature = java.util.HexFormat.of().formatHex(randomBytes);
        String tokenData = userId.toString() + ":" + deviceId + ":" + timestamp + ":" + signature;
        return Base64.getEncoder().encodeToString(tokenData.getBytes());
    }

    private record AuthChallenge(String nonce, Instant createdAt) {}
}
