package com.aiinpocket.n3n.gateway.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeRegistryTest {

    private NodeRegistry nodeRegistry;
    private UUID userId;

    @BeforeEach
    void setUp() {
        nodeRegistry = new NodeRegistry();
        userId = UUID.randomUUID();
    }

    private NodeConnection createConnection(String connectionId, UUID userId, String deviceId,
                                            String platform, List<String> capabilities) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        return NodeConnection.builder()
                .connectionId(connectionId)
                .userId(userId)
                .device(NodeConnection.DeviceInfo.builder()
                        .id(deviceId)
                        .displayName("Test Device")
                        .platform(platform)
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
    @DisplayName("Register")
    class Register {

        @Test
        void register_newConnection_addedToRegistry() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture", "system.run"));

            nodeRegistry.register(conn);

            assertThat(nodeRegistry.getConnection("conn-1")).isPresent();
            assertThat(nodeRegistry.getConnectionByDevice("dev-1")).isPresent();
        }

        @Test
        void register_multipleConnections_allAccessible() {
            UUID user2 = UUID.randomUUID();
            NodeConnection conn1 = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture"));
            NodeConnection conn2 = createConnection("conn-2", user2, "dev-2",
                    "windows", List.of("system.run"));

            nodeRegistry.register(conn1);
            nodeRegistry.register(conn2);

            assertThat(nodeRegistry.getAllConnections()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Unregister")
    class Unregister {

        @Test
        void unregister_existingConnection_removesFromAllMaps() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture"));
            nodeRegistry.register(conn);

            nodeRegistry.unregister("conn-1");

            assertThat(nodeRegistry.getConnection("conn-1")).isEmpty();
            assertThat(nodeRegistry.getConnectionByDevice("dev-1")).isEmpty();
            assertThat(nodeRegistry.getConnectionsForUser(userId)).isEmpty();
        }

        @Test
        void unregister_nonExisting_doesNothing() {
            // Should not throw
            nodeRegistry.unregister("non-existing");

            assertThat(nodeRegistry.getAllConnections()).isEmpty();
        }

        @Test
        void unregister_lastConnectionForUser_removesUserEntry() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of());
            nodeRegistry.register(conn);

            nodeRegistry.unregister("conn-1");

            assertThat(nodeRegistry.getConnectionsForUser(userId)).isEmpty();
        }

        @Test
        void unregister_oneOfMultipleUserConnections_keepsOthers() {
            NodeConnection conn1 = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture"));
            NodeConnection conn2 = createConnection("conn-2", userId, "dev-2",
                    "windows", List.of("system.run"));
            nodeRegistry.register(conn1);
            nodeRegistry.register(conn2);

            nodeRegistry.unregister("conn-1");

            assertThat(nodeRegistry.getConnectionsForUser(userId)).hasSize(1);
            assertThat(nodeRegistry.getConnection("conn-2")).isPresent();
        }
    }

    @Nested
    @DisplayName("Get Connection")
    class GetConnection {

        @Test
        void getConnection_existing_returnsConnection() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of());
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.getConnection("conn-1");

            assertThat(result).isPresent();
            assertThat(result.get().getConnectionId()).isEqualTo("conn-1");
        }

        @Test
        void getConnection_nonExisting_returnsEmpty() {
            Optional<NodeConnection> result = nodeRegistry.getConnection("missing");

            assertThat(result).isEmpty();
        }

        @Test
        void getConnectionByDevice_existing_returnsConnection() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of());
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.getConnectionByDevice("dev-1");

            assertThat(result).isPresent();
        }

        @Test
        void getConnectionByDevice_nonExisting_returnsEmpty() {
            Optional<NodeConnection> result = nodeRegistry.getConnectionByDevice("missing-dev");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Connections For User")
    class GetConnectionsForUser {

        @Test
        void getConnectionsForUser_withConnections_returnsActiveConnections() {
            NodeConnection conn1 = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture"));
            NodeConnection conn2 = createConnection("conn-2", userId, "dev-2",
                    "windows", List.of("system.run"));
            nodeRegistry.register(conn1);
            nodeRegistry.register(conn2);

            List<NodeConnection> result = nodeRegistry.getConnectionsForUser(userId);

            assertThat(result).hasSize(2);
        }

        @Test
        void getConnectionsForUser_noConnections_returnsEmptyList() {
            List<NodeConnection> result = nodeRegistry.getConnectionsForUser(UUID.randomUUID());

            assertThat(result).isEmpty();
        }

        @Test
        void getConnectionsForUser_filtersInactiveConnections() {
            // Create an inactive connection (session closed)
            WebSocketSession closedSession = mock(WebSocketSession.class);
            when(closedSession.isOpen()).thenReturn(false);

            NodeConnection inactive = NodeConnection.builder()
                    .connectionId("conn-inactive")
                    .userId(userId)
                    .device(NodeConnection.DeviceInfo.builder()
                            .id("dev-inactive").displayName("Closed").platform("macos")
                            .version("1.0").arch("arm64").build())
                    .capabilities(List.of())
                    .session(closedSession)
                    .status(NodeConnection.ConnectionStatus.CONNECTED)
                    .connectedAt(Instant.now())
                    .build();

            NodeConnection active = createConnection("conn-active", userId, "dev-active",
                    "macos", List.of());

            nodeRegistry.register(inactive);
            nodeRegistry.register(active);

            List<NodeConnection> result = nodeRegistry.getConnectionsForUser(userId);

            // Only active connections
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConnectionId()).isEqualTo("conn-active");
        }
    }

    @Nested
    @DisplayName("Find Node With Capability")
    class FindNodeWithCapability {

        @Test
        void findNodeWithCapability_hasCapability_returnsConnection() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture", "system.run"));
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.findNodeWithCapability(userId, "screen.capture");

            assertThat(result).isPresent();
            assertThat(result.get().getConnectionId()).isEqualTo("conn-1");
        }

        @Test
        void findNodeWithCapability_noCapability_returnsEmpty() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("system.run"));
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.findNodeWithCapability(userId, "screen.capture");

            assertThat(result).isEmpty();
        }

        @Test
        void findNodesWithCapability_multipleNodes_returnsAll() {
            NodeConnection conn1 = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of("screen.capture"));
            NodeConnection conn2 = createConnection("conn-2", userId, "dev-2",
                    "windows", List.of("screen.capture"));
            nodeRegistry.register(conn1);
            nodeRegistry.register(conn2);

            List<NodeConnection> result = nodeRegistry.findNodesWithCapability(userId, "screen.capture");

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Find Node By Platform")
    class FindNodeByPlatform {

        @Test
        void findNodeByPlatform_matchingPlatform_returnsConnection() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of());
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.findNodeByPlatform(userId, "macos");

            assertThat(result).isPresent();
        }

        @Test
        void findNodeByPlatform_caseInsensitive_returnsConnection() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macOS", List.of());
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.findNodeByPlatform(userId, "macos");

            assertThat(result).isPresent();
        }

        @Test
        void findNodeByPlatform_noMatch_returnsEmpty() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "windows", List.of());
            nodeRegistry.register(conn);

            Optional<NodeConnection> result = nodeRegistry.findNodeByPlatform(userId, "macos");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Stats")
    class GetStats {

        @Test
        void getStats_emptyRegistry_returnsZeros() {
            Map<String, Object> stats = nodeRegistry.getStats();

            assertThat(stats.get("totalConnections")).isEqualTo(0);
            assertThat(stats.get("uniqueUsers")).isEqualTo(0);
            assertThat(stats.get("uniqueDevices")).isEqualTo(0);
        }

        @Test
        void getStats_withConnections_returnsCorrectCounts() {
            UUID user2 = UUID.randomUUID();
            nodeRegistry.register(createConnection("c1", userId, "d1", "macos",
                    List.of("screen.capture", "system.run")));
            nodeRegistry.register(createConnection("c2", userId, "d2", "windows",
                    List.of("system.run")));
            nodeRegistry.register(createConnection("c3", user2, "d3", "macos",
                    List.of("screen.capture")));

            Map<String, Object> stats = nodeRegistry.getStats();

            assertThat(stats.get("totalConnections")).isEqualTo(3);
            assertThat(stats.get("uniqueUsers")).isEqualTo(2);
            assertThat(stats.get("uniqueDevices")).isEqualTo(3);

            @SuppressWarnings("unchecked")
            Map<String, Long> byPlatform = (Map<String, Long>) stats.get("byPlatform");
            assertThat(byPlatform.get("macos")).isEqualTo(2L);
            assertThat(byPlatform.get("windows")).isEqualTo(1L);

            @SuppressWarnings("unchecked")
            Map<String, Long> capCounts = (Map<String, Long>) stats.get("capabilityCounts");
            assertThat(capCounts.get("screen.capture")).isEqualTo(2L);
            assertThat(capCounts.get("system.run")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Touch")
    class Touch {

        @Test
        void touch_existingConnection_updatesLastActive() {
            NodeConnection conn = createConnection("conn-1", userId, "dev-1",
                    "macos", List.of());
            Instant before = conn.getLastActiveAt();
            nodeRegistry.register(conn);

            // Small delay to ensure different timestamp
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            nodeRegistry.touch("conn-1");

            NodeConnection updated = nodeRegistry.getConnection("conn-1").orElseThrow();
            assertThat(updated.getLastActiveAt()).isAfterOrEqualTo(before);
        }

        @Test
        void touch_nonExistingConnection_doesNothing() {
            // Should not throw
            nodeRegistry.touch("non-existing");
        }
    }

    @Nested
    @DisplayName("NodeConnection Methods")
    class NodeConnectionMethods {

        @Test
        void hasCapability_withCapability_returnsTrue() {
            NodeConnection conn = createConnection("c1", userId, "d1", "macos",
                    List.of("screen.capture", "system.run"));
            assertThat(conn.hasCapability("screen.capture")).isTrue();
        }

        @Test
        void hasCapability_withoutCapability_returnsFalse() {
            NodeConnection conn = createConnection("c1", userId, "d1", "macos",
                    List.of("system.run"));
            assertThat(conn.hasCapability("screen.capture")).isFalse();
        }

        @Test
        void hasCapability_nullCapabilities_returnsFalse() {
            NodeConnection conn = NodeConnection.builder()
                    .connectionId("c1").userId(userId).capabilities(null).build();
            assertThat(conn.hasCapability("anything")).isFalse();
        }

        @Test
        void isActive_connectedAndOpenSession_returnsTrue() {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);

            NodeConnection conn = NodeConnection.builder()
                    .connectionId("c1").userId(userId)
                    .session(session)
                    .status(NodeConnection.ConnectionStatus.CONNECTED)
                    .build();
            assertThat(conn.isActive()).isTrue();
        }

        @Test
        void isActive_closedSession_returnsFalse() {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(false);

            NodeConnection conn = NodeConnection.builder()
                    .connectionId("c1").userId(userId)
                    .session(session)
                    .status(NodeConnection.ConnectionStatus.CONNECTED)
                    .build();
            assertThat(conn.isActive()).isFalse();
        }

        @Test
        void isActive_disconnectedStatus_returnsFalse() {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.isOpen()).thenReturn(true);

            NodeConnection conn = NodeConnection.builder()
                    .connectionId("c1").userId(userId)
                    .session(session)
                    .status(NodeConnection.ConnectionStatus.DISCONNECTED)
                    .build();
            assertThat(conn.isActive()).isFalse();
        }

        @Test
        void isActive_nullSession_returnsFalse() {
            NodeConnection conn = NodeConnection.builder()
                    .connectionId("c1").userId(userId)
                    .session(null)
                    .status(NodeConnection.ConnectionStatus.CONNECTED)
                    .build();
            assertThat(conn.isActive()).isFalse();
        }
    }
}
