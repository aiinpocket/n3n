package com.aiinpocket.n3n.gateway.controller;

import com.aiinpocket.n3n.gateway.node.NodeConnection;
import com.aiinpocket.n3n.gateway.node.NodeInvoker;
import com.aiinpocket.n3n.gateway.node.NodeRegistry;
import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayControllerTest {

    @Mock
    private NodeRegistry nodeRegistry;

    @Mock
    private NodeInvoker nodeInvoker;

    @Mock
    private AgentPairingService agentPairingService;

    @InjectMocks
    private GatewayController gatewayController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testAdmin() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_ADMIN")
                .build();
    }

    private NodeConnection sampleNodeConnection(UUID userId) {
        return NodeConnection.builder()
                .connectionId("conn-123")
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id("device-abc")
                        .displayName("My MacBook Pro")
                        .platform("macos")
                        .version("1.0.0")
                        .arch("arm64")
                        .instanceId("inst-001")
                        .build())
                .capabilities(List.of("screen_capture", "file_access", "clipboard"))
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(Instant.parse("2026-01-15T10:00:00Z"))
                .lastActiveAt(Instant.parse("2026-01-15T10:05:00Z"))
                .latencyMs(42L)
                .build();
    }

    // ===== getNodes (GET /api/gateway/nodes) =====

    @Test
    void getNodes_returnsOkWithList() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(userId);
        when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(List.of(conn));

        var result = gatewayController.getNodes(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).connectionId()).isEqualTo("conn-123");
        assertThat(result.getBody().get(0).displayName()).isEqualTo("My MacBook Pro");
        assertThat(result.getBody().get(0).platform()).isEqualTo("macos");
        assertThat(result.getBody().get(0).version()).isEqualTo("1.0.0");
        assertThat(result.getBody().get(0).capabilities()).containsExactly("screen_capture", "file_access", "clipboard");
        assertThat(result.getBody().get(0).status()).isEqualTo("CONNECTED");
        assertThat(result.getBody().get(0).latencyMs()).isEqualTo(42L);
        verify(nodeRegistry).getConnectionsForUser(userId);
    }

    @Test
    void getNodes_emptyList_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(Collections.emptyList());

        var result = gatewayController.getNodes(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getNodes_multipleNodes_returnsAll() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn1 = sampleNodeConnection(userId);
        var conn2 = NodeConnection.builder()
                .connectionId("conn-456")
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id("device-def")
                        .displayName("My Windows PC")
                        .platform("windows")
                        .version("1.0.0")
                        .arch("x64")
                        .instanceId("inst-002")
                        .build())
                .capabilities(List.of("file_access"))
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .lastActiveAt(Instant.now())
                .latencyMs(85L)
                .build();
        when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(List.of(conn1, conn2));

        var result = gatewayController.getNodes(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
    }

    @Test
    void getNodes_parsesUserIdCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(Collections.emptyList());

        gatewayController.getNodes(user);

        verify(nodeRegistry).getConnectionsForUser(eq(userId));
    }

    // ===== getNode (GET /api/gateway/nodes/{connectionId}) =====

    @Test
    void getNode_found_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(userId);
        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));

        var result = gatewayController.getNode("conn-123", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().connectionId()).isEqualTo("conn-123");
        assertThat(result.getBody().displayName()).isEqualTo("My MacBook Pro");
        assertThat(result.getBody().platform()).isEqualTo("macos");
        verify(nodeRegistry).getConnection("conn-123");
    }

    @Test
    void getNode_notFound_returnsNotFound() {
        var user = testUser();
        when(nodeRegistry.getConnection("nonexistent")).thenReturn(Optional.empty());

        var result = gatewayController.getNode("nonexistent", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void getNode_belongsToDifferentUser_returnsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(otherUserId);
        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));

        var result = gatewayController.getNode("conn-123", user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void getNode_returnsAllNodeInfoFields() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Instant connectedAt = Instant.parse("2026-02-01T08:00:00Z");
        Instant lastActive = Instant.parse("2026-02-01T08:30:00Z");
        var conn = NodeConnection.builder()
                .connectionId("conn-full")
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id("dev-full")
                        .displayName("Full Info Device")
                        .platform("linux")
                        .version("2.0.0")
                        .arch("x64")
                        .instanceId("inst-full")
                        .build())
                .capabilities(List.of("cap1", "cap2"))
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(connectedAt)
                .lastActiveAt(lastActive)
                .latencyMs(100L)
                .build();
        when(nodeRegistry.getConnection("conn-full")).thenReturn(Optional.of(conn));

        var result = gatewayController.getNode("conn-full", user);

        assertThat(result.getBody()).isNotNull();
        var body = result.getBody();
        assertThat(body.connectionId()).isEqualTo("conn-full");
        assertThat(body.displayName()).isEqualTo("Full Info Device");
        assertThat(body.platform()).isEqualTo("linux");
        assertThat(body.version()).isEqualTo("2.0.0");
        assertThat(body.capabilities()).containsExactly("cap1", "cap2");
        assertThat(body.status()).isEqualTo("CONNECTED");
        assertThat(body.connectedAt()).isEqualTo(connectedAt.toString());
        assertThat(body.lastActiveAt()).isEqualTo(lastActive.toString());
        assertThat(body.latencyMs()).isEqualTo(100L);
    }

    // ===== invoke (POST /api/gateway/nodes/{connectionId}/invoke) =====

    @Test
    void invoke_success_returnsOkWithData() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(userId);
        var request = new GatewayController.InvokeRequest("screen_capture", Map.of("format", "png"));
        var invokeResult = NodeInvoker.InvokeResult.success(Map.of("imageData", "base64data"));

        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));
        when(nodeInvoker.invoke("conn-123", "screen_capture", Map.of("format", "png")))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invoke("conn-123", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isTrue();
        assertThat(result.getBody().data()).containsEntry("imageData", "base64data");
        assertThat(result.getBody().error()).isNull();
    }

    @Test
    void invoke_failure_returnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(userId);
        var request = new GatewayController.InvokeRequest("screen_capture", Map.of());
        var invokeResult = NodeInvoker.InvokeResult.error("PERMISSION_DENIED", "Screen recording permission not granted");

        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));
        when(nodeInvoker.invoke("conn-123", "screen_capture", Map.of()))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invoke("conn-123", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isFalse();
        assertThat(result.getBody().data()).isNull();
        assertThat(result.getBody().error()).isEqualTo("Screen recording permission not granted");
    }

    @Test
    void invoke_connectionNotFound_returnsNotFound() throws Exception {
        var user = testUser();
        var request = new GatewayController.InvokeRequest("screen_capture", Map.of());

        when(nodeRegistry.getConnection("nonexistent")).thenReturn(Optional.empty());

        var future = gatewayController.invoke("nonexistent", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
        verify(nodeInvoker, never()).invoke(any(), any(), any());
    }

    @Test
    void invoke_connectionBelongsToDifferentUser_returnsNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(otherUserId);
        var request = new GatewayController.InvokeRequest("screen_capture", Map.of());

        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));

        var future = gatewayController.invoke("conn-123", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
        verify(nodeInvoker, never()).invoke(any(), any(), any());
    }

    @Test
    void invoke_withNullArgs_passesNullToInvoker() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(userId);
        var request = new GatewayController.InvokeRequest("clipboard_read", null);
        var invokeResult = NodeInvoker.InvokeResult.success(Map.of("text", "clipboard content"));

        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));
        when(nodeInvoker.invoke("conn-123", "clipboard_read", null))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invoke("conn-123", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().success()).isTrue();
    }

    // ===== invokeAny (POST /api/gateway/invoke) =====

    @Test
    void invokeAny_success_returnsOkWithData() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new GatewayController.InvokeRequest("file_access", Map.of("path", "/tmp/test.txt"));
        var invokeResult = NodeInvoker.InvokeResult.success(Map.of("content", "file content"));

        when(nodeInvoker.invokeForUser(userId, "file_access", Map.of("path", "/tmp/test.txt")))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invokeAny(request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isTrue();
        assertThat(result.getBody().data()).containsEntry("content", "file content");
        assertThat(result.getBody().error()).isNull();
        verify(nodeInvoker).invokeForUser(userId, "file_access", Map.of("path", "/tmp/test.txt"));
    }

    @Test
    void invokeAny_noNodeAvailable_returnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new GatewayController.InvokeRequest("missing_capability", Map.of());
        var invokeResult = NodeInvoker.InvokeResult.error("NO_NODE_AVAILABLE", "No connected node with capability: missing_capability");

        when(nodeInvoker.invokeForUser(userId, "missing_capability", Map.of()))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invokeAny(request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isFalse();
        assertThat(result.getBody().error()).contains("No connected node with capability");
    }

    @Test
    void invokeAny_failure_returnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new GatewayController.InvokeRequest("screen_capture", Map.of());
        var invokeResult = NodeInvoker.InvokeResult.error("TIMEOUT", "Request timed out");

        when(nodeInvoker.invokeForUser(userId, "screen_capture", Map.of()))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        var future = gatewayController.invokeAny(request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().success()).isFalse();
        assertThat(result.getBody().error()).isEqualTo("Request timed out");
    }

    @Test
    void invokeAny_parsesUserIdCorrectly() throws Exception {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var request = new GatewayController.InvokeRequest("test_cap", Map.of());
        var invokeResult = NodeInvoker.InvokeResult.success(Map.of());

        when(nodeInvoker.invokeForUser(eq(userId), eq("test_cap"), any()))
                .thenReturn(CompletableFuture.completedFuture(invokeResult));

        gatewayController.invokeAny(request, user);

        verify(nodeInvoker).invokeForUser(eq(userId), eq("test_cap"), any());
    }

    // ===== getCapabilities (GET /api/gateway/capabilities) =====

    @Test
    void getCapabilities_returnsOkWithMap() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Map<String, Object> capabilities = Map.of(
                "conn-123", Map.of(
                        "deviceName", "My MacBook",
                        "platform", "macos",
                        "capabilities", List.of("screen_capture", "file_access")
                )
        );
        when(nodeInvoker.getAvailableCapabilities(userId)).thenReturn(capabilities);

        var result = gatewayController.getCapabilities(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsKey("conn-123");
        verify(nodeInvoker).getAvailableCapabilities(userId);
    }

    @Test
    void getCapabilities_noNodes_returnsEmptyMap() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        when(nodeInvoker.getAvailableCapabilities(userId)).thenReturn(Map.of());

        var result = gatewayController.getCapabilities(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getCapabilities_multipleNodes_returnsAll() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Map<String, Object> capabilities = Map.of(
                "conn-1", Map.of("deviceName", "Mac", "platform", "macos", "capabilities", List.of("cap1")),
                "conn-2", Map.of("deviceName", "PC", "platform", "windows", "capabilities", List.of("cap2"))
        );
        when(nodeInvoker.getAvailableCapabilities(userId)).thenReturn(capabilities);

        var result = gatewayController.getCapabilities(user);

        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody()).containsKey("conn-1");
        assertThat(result.getBody()).containsKey("conn-2");
    }

    // ===== getStats (GET /api/gateway/stats) =====

    @Test
    void getStats_returnsOkWithStatsMap() {
        Map<String, Object> stats = Map.of(
                "totalConnections", 5,
                "uniqueUsers", 3,
                "uniqueDevices", 4,
                "byPlatform", Map.of("macos", 2L, "windows", 3L),
                "capabilityCounts", Map.of("screen_capture", 3L, "file_access", 5L)
        );
        when(nodeRegistry.getStats()).thenReturn(stats);

        var result = gatewayController.getStats();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("totalConnections", 5);
        assertThat(result.getBody()).containsEntry("uniqueUsers", 3);
        assertThat(result.getBody()).containsEntry("uniqueDevices", 4);
        assertThat(result.getBody()).containsKey("byPlatform");
        assertThat(result.getBody()).containsKey("capabilityCounts");
        verify(nodeRegistry).getStats();
    }

    @Test
    void getStats_emptyStats_returnsOk() {
        Map<String, Object> stats = Map.of(
                "totalConnections", 0,
                "uniqueUsers", 0,
                "uniqueDevices", 0
        );
        when(nodeRegistry.getStats()).thenReturn(stats);

        var result = gatewayController.getStats();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("totalConnections", 0);
    }

    // ===== generatePairingCode (POST /api/gateway/pairing-code) =====

    @Test
    void generatePairingCode_success_returnsOk() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var pairing = new AgentPairingService.PairingInitiation("ABC123", Instant.now().plusSeconds(300));
        when(agentPairingService.initiatePairing(userId)).thenReturn(pairing);

        var result = gatewayController.generatePairingCode(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("ABC123");
        assertThat(result.getBody().expiresInSeconds()).isEqualTo(300);
        verify(agentPairingService).initiatePairing(userId);
    }

    @Test
    void generatePairingCode_differentUser_usesCorrectUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        var user1 = testUserWithId(userId1);
        var user2 = testUserWithId(userId2);
        var pairing1 = new AgentPairingService.PairingInitiation("CODE1", Instant.now().plusSeconds(300));
        var pairing2 = new AgentPairingService.PairingInitiation("CODE2", Instant.now().plusSeconds(300));
        when(agentPairingService.initiatePairing(userId1)).thenReturn(pairing1);
        when(agentPairingService.initiatePairing(userId2)).thenReturn(pairing2);

        var result1 = gatewayController.generatePairingCode(user1);
        var result2 = gatewayController.generatePairingCode(user2);

        assertThat(result1.getBody().code()).isEqualTo("CODE1");
        assertThat(result2.getBody().code()).isEqualTo("CODE2");
        verify(agentPairingService).initiatePairing(userId1);
        verify(agentPairingService).initiatePairing(userId2);
    }

    @Test
    void generatePairingCode_returnsExpiresInSeconds300() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var pairing = new AgentPairingService.PairingInitiation("XYZ", Instant.now().plusSeconds(300));
        when(agentPairingService.initiatePairing(userId)).thenReturn(pairing);

        var result = gatewayController.generatePairingCode(user);

        assertThat(result.getBody().expiresInSeconds()).isEqualTo(300);
    }

    // ===== getUserId fallback (non-UUID username) =====

    @Test
    void getNodes_nonUuidUsername_usesNameUuidFallback() {
        var user = User.withUsername("admin@example.com")
                .password("test")
                .authorities("ROLE_USER")
                .build();
        UUID expectedId = UUID.nameUUIDFromBytes("admin@example.com".getBytes());
        when(nodeRegistry.getConnectionsForUser(expectedId)).thenReturn(Collections.emptyList());

        var result = gatewayController.getNodes(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(nodeRegistry).getConnectionsForUser(expectedId);
    }

    // ===== InvokeRequest record =====

    @Test
    void invokeRequest_recordFieldsCorrect() {
        var request = new GatewayController.InvokeRequest("test_cap", Map.of("key", "value"));

        assertThat(request.capability()).isEqualTo("test_cap");
        assertThat(request.args()).containsEntry("key", "value");
    }

    // ===== InvokeResponse record =====

    @Test
    void invokeResponse_successFields() {
        var response = new GatewayController.InvokeResponse(true, Map.of("result", "ok"), null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("result", "ok");
        assertThat(response.error()).isNull();
    }

    @Test
    void invokeResponse_errorFields() {
        var response = new GatewayController.InvokeResponse(false, null, "Something went wrong");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo("Something went wrong");
    }

    // ===== PairingCodeResponse record =====

    @Test
    void pairingCodeResponse_fields() {
        var response = new GatewayController.PairingCodeResponse("CODE123", 300);

        assertThat(response.code()).isEqualTo("CODE123");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    // ===== NodeInfo record =====

    @Test
    void nodeInfo_allFields() {
        var nodeInfo = new GatewayController.NodeInfo(
                "conn-1", "MacBook Pro", "macos", "1.2.3",
                List.of("cap1", "cap2"), "CONNECTED",
                "2026-01-01T00:00:00Z", "2026-01-01T01:00:00Z", 50L
        );

        assertThat(nodeInfo.connectionId()).isEqualTo("conn-1");
        assertThat(nodeInfo.displayName()).isEqualTo("MacBook Pro");
        assertThat(nodeInfo.platform()).isEqualTo("macos");
        assertThat(nodeInfo.version()).isEqualTo("1.2.3");
        assertThat(nodeInfo.capabilities()).containsExactly("cap1", "cap2");
        assertThat(nodeInfo.status()).isEqualTo("CONNECTED");
        assertThat(nodeInfo.connectedAt()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(nodeInfo.lastActiveAt()).isEqualTo("2026-01-01T01:00:00Z");
        assertThat(nodeInfo.latencyMs()).isEqualTo(50L);
    }

    // ===== Edge cases =====

    @Test
    void getNodes_connectedAtAndLastActiveAt_convertedToString() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);
        Instant connAt = Instant.parse("2026-02-10T12:00:00Z");
        Instant lastActive = Instant.parse("2026-02-10T12:30:00Z");
        var conn = NodeConnection.builder()
                .connectionId("conn-time")
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id("dev-t")
                        .displayName("Time Test")
                        .platform("linux")
                        .version("1.0")
                        .arch("x64")
                        .instanceId("inst-t")
                        .build())
                .capabilities(List.of("test"))
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(connAt)
                .lastActiveAt(lastActive)
                .latencyMs(10L)
                .build();
        when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(List.of(conn));

        var result = gatewayController.getNodes(user);

        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).connectedAt()).isEqualTo("2026-02-10T12:00:00Z");
        assertThat(result.getBody().get(0).lastActiveAt()).isEqualTo("2026-02-10T12:30:00Z");
    }

    @Test
    void invoke_verifyUserOwnership_beforeInvocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var user = testUserWithId(userId);
        var conn = sampleNodeConnection(otherUserId);
        var request = new GatewayController.InvokeRequest("cap", Map.of());

        when(nodeRegistry.getConnection("conn-123")).thenReturn(Optional.of(conn));

        var future = gatewayController.invoke("conn-123", request, user);
        var result = future.get();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(nodeInvoker, never()).invoke(any(), any(), any());
    }

    @Test
    void getNode_differentConnectionStatuses_mappedCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = testUserWithId(userId);

        for (NodeConnection.ConnectionStatus status : NodeConnection.ConnectionStatus.values()) {
            var conn = NodeConnection.builder()
                    .connectionId("conn-status-" + status.name())
                    .userId(userId)
                    .device(NodeConnection.DeviceInfo.builder()
                            .id("dev-s")
                            .displayName("Status Test")
                            .platform("macos")
                            .version("1.0")
                            .arch("arm64")
                            .instanceId("inst-s")
                            .build())
                    .capabilities(List.of())
                    .status(status)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .latencyMs(0L)
                    .build();
            when(nodeRegistry.getConnection("conn-status-" + status.name())).thenReturn(Optional.of(conn));

            var result = gatewayController.getNode("conn-status-" + status.name(), user);

            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().status()).isEqualTo(status.name());
        }
    }
}
