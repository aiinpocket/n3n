package com.aiinpocket.n3n.gateway.node;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.gateway.handler.GatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.protocol.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NodeInvokerTest extends BaseServiceTest {

    @Mock
    private NodeRegistry nodeRegistry;

    @Mock
    private GatewayWebSocketHandler gatewayHandler;

    @InjectMocks
    private NodeInvoker nodeInvoker;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private NodeConnection createActiveConnection(String connectionId, UUID userId, List<String> capabilities) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.isOpen()).thenReturn(true);

        return NodeConnection.builder()
                .connectionId(connectionId)
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id("dev-" + connectionId)
                        .displayName("Test Device")
                        .platform("macos")
                        .version("1.0.0")
                        .arch("arm64")
                        .build())
                .capabilities(capabilities)
                .session(session)
                .status(NodeConnection.ConnectionStatus.CONNECTED)
                .connectedAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Invoke by Connection ID")
    class InvokeByConnectionId {

        @Test
        void invoke_successfulResponse_returnsSuccessResult() throws Exception {
            // Given
            GatewayResponse response = GatewayResponse.success("req-1", Map.of("result", "data"));
            when(gatewayHandler.invokeCapability("conn-1", "screen.capture", Map.of()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invoke("conn-1", "screen.capture", Map.of()).get();

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.data()).containsEntry("result", "data");
            assertThat(result.errorCode()).isNull();
        }

        @Test
        void invoke_errorResponse_returnsErrorResult() throws Exception {
            // Given
            GatewayResponse response = GatewayResponse.error("req-1", "PERMISSION_DENIED", "No access");
            when(gatewayHandler.invokeCapability("conn-1", "screen.capture", Map.of()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invoke("conn-1", "screen.capture", Map.of()).get();

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("PERMISSION_DENIED");
            assertThat(result.errorMessage()).isEqualTo("No access");
        }

        @Test
        void invoke_errorResponseNullError_returnsUnknownError() throws Exception {
            // Given
            GatewayResponse response = new GatewayResponse();
            response.setId("req-1");
            response.setOk(false);
            response.setError(null);
            when(gatewayHandler.invokeCapability("conn-1", "system.run", Map.of("cmd", "ls")))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invoke("conn-1", "system.run", Map.of("cmd", "ls")).get();

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("Invoke with Timeout")
    class InvokeWithTimeout {

        @Test
        void invoke_completesBeforeTimeout_returnsResult() throws Exception {
            // Given
            GatewayResponse response = GatewayResponse.success("req-1", Map.of("result", "ok"));
            when(gatewayHandler.invokeCapability("conn-1", "system.run", Map.of()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invoke("conn-1", "system.run", Map.of(), 5000).get();

            // Then
            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("Invoke For User")
    class InvokeForUser {

        @Test
        void invokeForUser_hasCapableNode_invokesAndReturnsResult() throws Exception {
            // Given
            NodeConnection conn = createActiveConnection("conn-1", userId,
                    List.of("screen.capture"));
            GatewayResponse response = GatewayResponse.success("req-1", Map.of("screenshot", "base64data"));

            when(nodeRegistry.findNodeWithCapability(userId, "screen.capture"))
                    .thenReturn(Optional.of(conn));
            when(gatewayHandler.invokeCapability("conn-1", "screen.capture", Map.of()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invokeForUser(userId, "screen.capture", Map.of()).get();

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.data()).containsEntry("screenshot", "base64data");
        }

        @Test
        void invokeForUser_noCapableNode_returnsError() throws Exception {
            // Given
            when(nodeRegistry.findNodeWithCapability(userId, "screen.capture"))
                    .thenReturn(Optional.empty());

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invokeForUser(userId, "screen.capture", Map.of()).get();

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("NO_NODE_AVAILABLE");
            assertThat(result.errorMessage()).contains("screen.capture");
            verify(gatewayHandler, never()).invokeCapability(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Invoke On Platform")
    class InvokeOnPlatform {

        @Test
        void invokeOnPlatform_matchingNodeWithCapability_invokes() throws Exception {
            // Given
            NodeConnection conn = createActiveConnection("conn-1", userId,
                    List.of("system.run"));
            GatewayResponse response = GatewayResponse.success("req-1", Map.of("exit_code", 0));

            when(nodeRegistry.findNodeByPlatform(userId, "macos")).thenReturn(Optional.of(conn));
            when(gatewayHandler.invokeCapability("conn-1", "system.run", Map.of("cmd", "ls")))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invokeOnPlatform(
                    userId, "macos", "system.run", Map.of("cmd", "ls")).get();

            // Then
            assertThat(result.success()).isTrue();
        }

        @Test
        void invokeOnPlatform_noPlatformNode_returnsError() throws Exception {
            // Given
            when(nodeRegistry.findNodeByPlatform(userId, "linux")).thenReturn(Optional.empty());

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invokeOnPlatform(
                    userId, "linux", "system.run", Map.of()).get();

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("NO_NODE_AVAILABLE");
            assertThat(result.errorMessage()).contains("linux");
        }

        @Test
        void invokeOnPlatform_nodeWithoutCapability_returnsCapabilityNotFound() throws Exception {
            // Given
            NodeConnection conn = createActiveConnection("conn-1", userId,
                    List.of("system.run")); // no screen.capture
            when(nodeRegistry.findNodeByPlatform(userId, "macos")).thenReturn(Optional.of(conn));

            // When
            NodeInvoker.InvokeResult result = nodeInvoker.invokeOnPlatform(
                    userId, "macos", "screen.capture", Map.of()).get();

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("CAPABILITY_NOT_FOUND");
            assertThat(result.errorMessage()).contains("screen.capture");
        }
    }

    @Nested
    @DisplayName("Has Capability")
    class HasCapability {

        @Test
        void hasCapability_userHasCapableNode_returnsTrue() {
            NodeConnection conn = createActiveConnection("conn-1", userId,
                    List.of("screen.capture"));
            when(nodeRegistry.findNodeWithCapability(userId, "screen.capture"))
                    .thenReturn(Optional.of(conn));

            boolean result = nodeInvoker.hasCapability(userId, "screen.capture");

            assertThat(result).isTrue();
        }

        @Test
        void hasCapability_noCapableNode_returnsFalse() {
            when(nodeRegistry.findNodeWithCapability(userId, "screen.capture"))
                    .thenReturn(Optional.empty());

            boolean result = nodeInvoker.hasCapability(userId, "screen.capture");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Get Available Capabilities")
    class GetAvailableCapabilities {

        @Test
        void getAvailableCapabilities_withConnections_returnsCapabilityMap() {
            NodeConnection conn = createActiveConnection("conn-1", userId,
                    List.of("screen.capture", "system.run"));
            when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(List.of(conn));

            Map<String, Object> result = nodeInvoker.getAvailableCapabilities(userId);

            assertThat(result).containsKey("conn-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> connInfo = (Map<String, Object>) result.get("conn-1");
            assertThat(connInfo.get("platform")).isEqualTo("macos");
            assertThat(connInfo.get("deviceName")).isEqualTo("Test Device");
        }

        @Test
        void getAvailableCapabilities_noConnections_returnsEmptyMap() {
            when(nodeRegistry.getConnectionsForUser(userId)).thenReturn(List.of());

            Map<String, Object> result = nodeInvoker.getAvailableCapabilities(userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("InvokeResult Record")
    class InvokeResultRecord {

        @Test
        void success_createsSuccessResult() {
            NodeInvoker.InvokeResult result = NodeInvoker.InvokeResult.success(Map.of("key", "value"));

            assertThat(result.success()).isTrue();
            assertThat(result.data()).containsEntry("key", "value");
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        void error_createsErrorResult() {
            NodeInvoker.InvokeResult result = NodeInvoker.InvokeResult.error("ERR_CODE", "Something failed");

            assertThat(result.success()).isFalse();
            assertThat(result.data()).isNull();
            assertThat(result.errorCode()).isEqualTo("ERR_CODE");
            assertThat(result.errorMessage()).isEqualTo("Something failed");
        }
    }
}
