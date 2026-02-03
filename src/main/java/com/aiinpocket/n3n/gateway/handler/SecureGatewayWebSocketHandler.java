package com.aiinpocket.n3n.gateway.handler;

import com.aiinpocket.n3n.agent.service.AgentRegistrationService;
import com.aiinpocket.n3n.gateway.node.NodeConnection;
import com.aiinpocket.n3n.gateway.node.NodeRegistry;
import com.aiinpocket.n3n.gateway.protocol.GatewayEvent;
import com.aiinpocket.n3n.gateway.protocol.GatewayMessage;
import com.aiinpocket.n3n.gateway.protocol.GatewayRequest;
import com.aiinpocket.n3n.gateway.protocol.GatewayResponse;
import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
import com.aiinpocket.n3n.gateway.security.SecureMessageService;
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
 * Secure WebSocket handler with end-to-end encryption.
 * All messages are encrypted using AES-256-GCM after device pairing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecureGatewayWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final NodeRegistry nodeRegistry;
    private final SecureMessageService secureMessageService;
    private final DeviceKeyStore deviceKeyStore;
    private final AgentPairingService pairingService;
    private final AgentRegistrationService agentRegistrationService;

    /**
     * Pending requests waiting for responses
     */
    private final Map<String, CompletableFuture<GatewayResponse>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Session to device ID mapping
     */
    private final Map<String, String> sessionToDevice = new ConcurrentHashMap<>();

    /**
     * Session to connection ID mapping
     */
    private final Map<String, String> sessionToConnection = new ConcurrentHashMap<>();

    /**
     * Sessions in handshake phase (not yet encrypted)
     */
    private final Set<String> handshakeSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        log.info("New secure WebSocket connection: {} (isOpen={})", sessionId, session.isOpen());

        // Mark as handshake phase
        handshakeSessions.add(sessionId);

        // Send handshake challenge (unencrypted)
        GatewayEvent challenge = GatewayEvent.create("handshake.challenge", Map.of(
            "protocolVersion", 1,
            "ts", System.currentTimeMillis(),
            "nonce", UUID.randomUUID().toString()
        ));

        try {
            log.debug("Sending handshake challenge to {}", sessionId);
            sendPlainMessage(session, challenge);
            log.info("Handshake challenge sent to {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to send handshake challenge to {}: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();

        // Check if session is in handshake phase
        if (handshakeSessions.contains(sessionId)) {
            handleHandshakeMessage(session, payload);
            return;
        }

        // Encrypted session - decrypt first
        String deviceId = sessionToDevice.get(sessionId);
        if (deviceId == null) {
            log.warn("Received message from unknown session: {}", sessionId);
            sendPlainError(session, "SESSION_ERROR", "Session not authenticated");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        try {
            // Verify and decrypt
            SecureMessageService.VerificationResult verification = secureMessageService.verify(payload);
            if (!verification.valid()) {
                log.warn("Message verification failed: {}", verification.error());
                sendEncryptedError(session, deviceId, "VERIFY_ERROR", verification.error());
                return;
            }

            // Verify device ID matches session
            if (!deviceId.equals(verification.deviceId())) {
                log.warn("Device ID mismatch: expected={}, got={}", deviceId, verification.deviceId());
                sendEncryptedError(session, deviceId, "DEVICE_MISMATCH", "Device ID mismatch");
                return;
            }

            // Decrypt payload
            SecureMessageService.DecryptedMessage<GatewayMessage> decrypted =
                secureMessageService.decrypt(payload, GatewayMessage.class);

            // Process message
            handleGatewayMessage(session, decrypted.payload());

            // Update last activity
            String connectionId = sessionToConnection.get(sessionId);
            if (connectionId != null) {
                nodeRegistry.touch(connectionId);
            }

        } catch (SecureMessageService.SecureMessageException e) {
            log.error("Decryption failed for session {}: {}", sessionId, e.getMessage());
            sendEncryptedError(session, deviceId, "DECRYPT_ERROR", "Decryption failed");
        } catch (Exception e) {
            log.error("Error processing secure message from {}: {}", sessionId, e.getMessage());
            sendEncryptedError(session, deviceId, "PROCESS_ERROR", "Failed to process message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();

        String connectionId = sessionToConnection.remove(sessionId);
        if (connectionId != null) {
            nodeRegistry.unregister(connectionId);
        }

        sessionToDevice.remove(sessionId);
        handshakeSessions.remove(sessionId);

        log.info("Secure WebSocket connection closed: {} (status: {})", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Handle handshake messages (unencrypted phase)
     */
    private void handleHandshakeMessage(WebSocketSession session, String payload) throws IOException {
        String sessionId = session.getId();

        try {
            GatewayMessage message = objectMapper.readValue(payload, GatewayMessage.class);

            if (!"req".equals(message.getType())) {
                sendPlainError(session, "HANDSHAKE_ERROR", "Expected request message");
                return;
            }

            GatewayRequest request = objectMapper.readValue(payload, GatewayRequest.class);

            if (!"handshake.auth".equals(request.getMethod())) {
                sendPlainError(session, "HANDSHAKE_ERROR", "Expected handshake.auth request");
                return;
            }

            Map<String, Object> params = request.getParams();

            // Get device token
            String deviceToken = (String) params.get("deviceToken");
            if (deviceToken == null || deviceToken.isEmpty()) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Device token required"));
                return;
            }

            // Validate device token
            Optional<UUID> userIdOpt = pairingService.validateDeviceToken(deviceToken);
            if (userIdOpt.isEmpty()) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Invalid device token"));
                return;
            }

            // Get device ID from params
            String deviceId = (String) params.get("deviceId");
            if (deviceId == null || deviceId.isEmpty()) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Device ID required"));
                return;
            }

            // Verify device exists and belongs to user
            Optional<DeviceKeyStore.DeviceKey> deviceKey = deviceKeyStore.getDeviceKey(deviceId);
            if (deviceKey.isEmpty()) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Unknown device"));
                return;
            }

            if (!deviceKey.get().getUserId().equals(userIdOpt.get())) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Device mismatch"));
                return;
            }

            if (deviceKey.get().isRevoked()) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "Device has been revoked"));
                return;
            }

            // Check if agent is blocked in database
            if (agentRegistrationService.isDeviceBlocked(deviceId)) {
                sendPlainResponse(session, GatewayResponse.error(
                    request.getId(), "AUTH_ERROR", "This agent has been blocked"));
                return;
            }

            // Handshake successful - switch to encrypted mode
            handshakeSessions.remove(sessionId);
            sessionToDevice.put(sessionId, deviceId);

            // Create connection
            @SuppressWarnings("unchecked")
            Map<String, Object> client = (Map<String, Object>) params.get("client");
            @SuppressWarnings("unchecked")
            List<String> caps = (List<String>) params.get("capabilities");

            String connectionId = UUID.randomUUID().toString();

            NodeConnection.DeviceInfo deviceInfo = NodeConnection.DeviceInfo.builder()
                .id(deviceId)
                .displayName(deviceKey.get().getDeviceName())
                .version(client != null ? (String) client.get("version") : "1.0.0")
                .platform(deviceKey.get().getPlatform())
                .arch(client != null ? (String) client.get("arch") : "unknown")
                .instanceId(sessionId)
                .build();

            NodeConnection connection = NodeConnection.builder()
                .connectionId(connectionId)
                .userId(userIdOpt.get())
                .device(deviceInfo)
                .capabilities(caps != null ? caps : Collections.emptyList())
                .session(session)
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .lastActiveAt(Instant.now())
                .deviceToken(deviceToken)
                .build();

            nodeRegistry.register(connection);
            sessionToConnection.put(sessionId, connectionId);

            log.info("Secure handshake completed: deviceId={}, connectionId={}", deviceId, connectionId);

            // Update last seen timestamp
            agentRegistrationService.updateLastSeen(deviceId);

            // Send success response (still unencrypted, as client expects it)
            sendPlainResponse(session, GatewayResponse.success(request.getId(), Map.of(
                "connectionId", connectionId,
                "encrypted", true,
                "scopes", List.of("node.invoke", "node.register")
            )));

        } catch (Exception e) {
            log.error("Handshake error: {}", e.getMessage());
            sendPlainError(session, "HANDSHAKE_ERROR", "Handshake failed: " + e.getMessage());
            session.close(CloseStatus.PROTOCOL_ERROR);
        }
    }

    /**
     * Handle decrypted gateway messages
     */
    private void handleGatewayMessage(WebSocketSession session, GatewayMessage message) throws Exception {
        switch (message.getType()) {
            case "req" -> handleRequest(session, objectMapper.convertValue(message, GatewayRequest.class));
            case "res" -> handleResponse(objectMapper.convertValue(message, GatewayResponse.class));
            case "event" -> handleEvent(session, objectMapper.convertValue(message, GatewayEvent.class));
            default -> log.warn("Unknown message type: {}", message.getType());
        }
    }

    private void handleRequest(WebSocketSession session, GatewayRequest request) throws Exception {
        String sessionId = session.getId();
        String deviceId = sessionToDevice.get(sessionId);
        String method = request.getMethod();

        log.debug("Received encrypted request: method={}, id={}", method, request.getId());

        try {
            GatewayResponse response = switch (method) {
                case "node.register" -> handleNodeRegister(session, request.getParams(), request.getId());
                case "node.invoke.result" -> handleInvokeResult(request.getParams(), request.getId());
                case "ping" -> GatewayResponse.success(request.getId(), Map.of("pong", System.currentTimeMillis()));
                default -> {
                    log.warn("Unknown method: {}", method);
                    yield GatewayResponse.error(request.getId(), "UNKNOWN_METHOD", "Unknown method: " + method);
                }
            };

            sendEncryptedMessage(session, deviceId, response);

        } catch (Exception e) {
            log.error("Error handling request {}: {}", method, e.getMessage());
            sendEncryptedError(session, deviceId, "INTERNAL_ERROR", e.getMessage());
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
        log.debug("Received encrypted event: {}", event.getEvent());
        // Handle events as needed
    }

    private GatewayResponse handleNodeRegister(WebSocketSession session, Map<String, Object> params, String requestId) {
        String sessionId = session.getId();
        String connectionId = sessionToConnection.get(sessionId);

        if (connectionId == null) {
            return GatewayResponse.error(requestId, "NOT_CONNECTED", "Not connected");
        }

        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) params.get("capabilities");

        nodeRegistry.getConnection(connectionId).ifPresent(conn -> {
            if (capabilities != null) {
                conn.setCapabilities(capabilities);
            }
        });

        log.info("Node capabilities registered: connectionId={}, capabilities={}", connectionId, capabilities);

        return GatewayResponse.success(requestId, Map.of(
            "nodeId", connectionId,
            "registered", true
        ));
    }

    private GatewayResponse handleInvokeResult(Map<String, Object> params, String requestId) {
        String invokeId = (String) params.get("invokeId");

        CompletableFuture<GatewayResponse> future = pendingRequests.remove(invokeId);
        if (future != null) {
            future.complete(GatewayResponse.success(invokeId, params));
        }

        return GatewayResponse.success(requestId, Map.of("received", true));
    }

    /**
     * Invoke a capability on a connected node (encrypted)
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

                // Find device ID for this connection
                String deviceId = findDeviceIdForConnection(connectionId);
                if (deviceId == null) {
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error("", "DEVICE_NOT_FOUND", "Device not found for connection")
                    );
                }

                GatewayRequest request = GatewayRequest.create("node.invoke", Map.of(
                    "capability", capability,
                    "args", args
                ));

                CompletableFuture<GatewayResponse> future = new CompletableFuture<>();
                pendingRequests.put(request.getId(), future);

                try {
                    sendEncryptedMessage(connection.getSession(), deviceId, request);
                } catch (Exception e) {
                    pendingRequests.remove(request.getId());
                    return CompletableFuture.<GatewayResponse>completedFuture(
                        GatewayResponse.error(request.getId(), "SEND_ERROR", e.getMessage())
                    );
                }

                return future.orTimeout(30, TimeUnit.SECONDS)
                    .exceptionally(e -> GatewayResponse.error(request.getId(), "TIMEOUT", "Request timed out"));
            })
            .orElse(CompletableFuture.completedFuture(
                GatewayResponse.error("", "CONNECTION_NOT_FOUND", "Connection not found: " + connectionId)
            ));
    }

    /**
     * Send encrypted message to a device
     */
    private void sendEncryptedMessage(WebSocketSession session, String deviceId, Object message) throws Exception {
        if (!session.isOpen()) {
            log.warn("Cannot send to closed session");
            return;
        }

        String encryptedPayload = secureMessageService.encrypt(deviceId, message);
        session.sendMessage(new TextMessage(encryptedPayload));
    }

    /**
     * Send encrypted error
     */
    private void sendEncryptedError(WebSocketSession session, String deviceId, String code, String message) {
        try {
            GatewayResponse response = GatewayResponse.error("", code, message);
            sendEncryptedMessage(session, deviceId, response);
        } catch (Exception e) {
            log.error("Failed to send encrypted error: {}", e.getMessage());
        }
    }

    /**
     * Send plain (unencrypted) message during handshake
     */
    private void sendPlainMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Send plain response during handshake
     */
    private void sendPlainResponse(WebSocketSession session, GatewayResponse response) throws IOException {
        sendPlainMessage(session, response);
    }

    /**
     * Send plain error during handshake
     */
    private void sendPlainError(WebSocketSession session, String code, String message) throws IOException {
        sendPlainMessage(session, GatewayResponse.error("", code, message));
    }

    /**
     * Find device ID for a connection
     */
    private String findDeviceIdForConnection(String connectionId) {
        for (Map.Entry<String, String> entry : sessionToConnection.entrySet()) {
            if (connectionId.equals(entry.getValue())) {
                return sessionToDevice.get(entry.getKey());
            }
        }
        return null;
    }

    /**
     * Broadcast encrypted event to all devices of a user
     */
    public void broadcastToUser(UUID userId, GatewayEvent event) {
        for (NodeConnection connection : nodeRegistry.getConnectionsForUser(userId)) {
            try {
                if (connection.isActive()) {
                    String deviceId = findDeviceIdForConnection(connection.getConnectionId());
                    if (deviceId != null) {
                        sendEncryptedMessage(connection.getSession(), deviceId, event);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to broadcast to connection {}: {}",
                    connection.getConnectionId(), e.getMessage());
            }
        }
    }
}
