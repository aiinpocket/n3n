package com.aiinpocket.n3n.gateway.node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing connected local agents (nodes).
 */
@Component
@Slf4j
public class NodeRegistry {

    /**
     * Connected nodes by connection ID
     */
    private final Map<String, NodeConnection> connectionById = new ConcurrentHashMap<>();

    /**
     * Connected nodes by user ID
     */
    private final Map<UUID, Set<String>> connectionsByUser = new ConcurrentHashMap<>();

    /**
     * Connected nodes by device ID
     */
    private final Map<String, String> connectionByDevice = new ConcurrentHashMap<>();

    /**
     * Register a new node connection
     */
    public void register(NodeConnection connection) {
        String connectionId = connection.getConnectionId();
        UUID userId = connection.getUserId();
        String deviceId = connection.getDevice().getId();

        connectionById.put(connectionId, connection);

        connectionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(connectionId);

        connectionByDevice.put(deviceId, connectionId);

        log.info("Node registered: connectionId={}, deviceId={}, platform={}, capabilities={}",
            connectionId, deviceId, connection.getDevice().getPlatform(),
            connection.getCapabilities());
    }

    /**
     * Unregister a node connection
     */
    public void unregister(String connectionId) {
        NodeConnection connection = connectionById.remove(connectionId);
        if (connection != null) {
            UUID userId = connection.getUserId();
            String deviceId = connection.getDevice().getId();

            Set<String> userConnections = connectionsByUser.get(userId);
            if (userConnections != null) {
                userConnections.remove(connectionId);
                if (userConnections.isEmpty()) {
                    connectionsByUser.remove(userId);
                }
            }

            connectionByDevice.remove(deviceId);

            log.info("Node unregistered: connectionId={}, deviceId={}",
                connectionId, deviceId);
        }
    }

    /**
     * Get a connection by ID
     */
    public Optional<NodeConnection> getConnection(String connectionId) {
        return Optional.ofNullable(connectionById.get(connectionId));
    }

    /**
     * Get a connection by device ID
     */
    public Optional<NodeConnection> getConnectionByDevice(String deviceId) {
        String connectionId = connectionByDevice.get(deviceId);
        if (connectionId != null) {
            return getConnection(connectionId);
        }
        return Optional.empty();
    }

    /**
     * Get all connections for a user
     */
    public List<NodeConnection> getConnectionsForUser(UUID userId) {
        Set<String> connectionIds = connectionsByUser.get(userId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return connectionIds.stream()
            .map(connectionById::get)
            .filter(Objects::nonNull)
            .filter(NodeConnection::isActive)
            .collect(Collectors.toList());
    }

    /**
     * Find a node with a specific capability for a user
     */
    public Optional<NodeConnection> findNodeWithCapability(UUID userId, String capability) {
        return getConnectionsForUser(userId).stream()
            .filter(conn -> conn.hasCapability(capability))
            .findFirst();
    }

    /**
     * Find all nodes with a specific capability for a user
     */
    public List<NodeConnection> findNodesWithCapability(UUID userId, String capability) {
        return getConnectionsForUser(userId).stream()
            .filter(conn -> conn.hasCapability(capability))
            .collect(Collectors.toList());
    }

    /**
     * Find a node by platform for a user
     */
    public Optional<NodeConnection> findNodeByPlatform(UUID userId, String platform) {
        return getConnectionsForUser(userId).stream()
            .filter(conn -> platform.equalsIgnoreCase(conn.getDevice().getPlatform()))
            .findFirst();
    }

    /**
     * Get all active connections
     */
    public List<NodeConnection> getAllConnections() {
        return connectionById.values().stream()
            .filter(NodeConnection::isActive)
            .collect(Collectors.toList());
    }

    /**
     * Get statistics about connected nodes
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        List<NodeConnection> activeConnections = getAllConnections();

        stats.put("totalConnections", activeConnections.size());
        stats.put("uniqueUsers", connectionsByUser.size());
        stats.put("uniqueDevices", connectionByDevice.size());

        // Count by platform
        Map<String, Long> byPlatform = activeConnections.stream()
            .collect(Collectors.groupingBy(
                conn -> conn.getDevice().getPlatform(),
                Collectors.counting()
            ));
        stats.put("byPlatform", byPlatform);

        // Count capabilities
        Map<String, Long> capabilityCounts = new HashMap<>();
        for (NodeConnection conn : activeConnections) {
            for (String cap : conn.getCapabilities()) {
                capabilityCounts.merge(cap, 1L, Long::sum);
            }
        }
        stats.put("capabilityCounts", capabilityCounts);

        return stats;
    }

    /**
     * Update connection's last activity
     */
    public void touch(String connectionId) {
        NodeConnection connection = connectionById.get(connectionId);
        if (connection != null) {
            connection.touch();
        }
    }
}
