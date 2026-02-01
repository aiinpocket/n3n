package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.plugin.entity.Plugin;
import com.aiinpocket.n3n.plugin.entity.PluginInstallation;
import com.aiinpocket.n3n.plugin.entity.PluginVersion;
import com.aiinpocket.n3n.plugin.handler.DynamicPluginNodeHandler;
import com.aiinpocket.n3n.plugin.repository.PluginInstallationRepository;
import com.aiinpocket.n3n.plugin.repository.PluginRepository;
import com.aiinpocket.n3n.plugin.repository.PluginVersionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic registration of plugin node handlers.
 * When a plugin is installed, its node definitions are registered as available node types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginNodeRegistrar {

    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final PluginRepository pluginRepository;
    private final PluginVersionRepository pluginVersionRepository;
    private final PluginInstallationRepository pluginInstallationRepository;

    // Track registered plugin handlers per user
    // Key: userId, Value: Map<nodeType, handler>
    private final Map<UUID, Map<String, DynamicPluginNodeHandler>> userPluginHandlers = new ConcurrentHashMap<>();

    // Track global plugin handlers (for system-wide plugins)
    private final Map<String, DynamicPluginNodeHandler> globalPluginHandlers = new ConcurrentHashMap<>();

    /**
     * Initialize plugin nodes on startup.
     * Loads all enabled plugin installations and registers their node handlers.
     */
    @PostConstruct
    public void initializePluginNodes() {
        log.info("Initializing plugin node handlers...");

        // For now, register sample plugins as global handlers
        List<Plugin> plugins = pluginRepository.findAll();
        for (Plugin plugin : plugins) {
            PluginVersion latestVersion = pluginVersionRepository.findLatestByPluginId(plugin.getId())
                    .orElse(null);
            if (latestVersion != null) {
                registerPluginNodesGlobal(plugin, latestVersion);
            }
        }

        log.info("Plugin node handlers initialized. {} global handlers registered.", globalPluginHandlers.size());
    }

    /**
     * Register plugin nodes globally (available to all users).
     */
    public void registerPluginNodesGlobal(Plugin plugin, PluginVersion version) {
        Map<String, Object> nodeDefinitions = version.getNodeDefinitions();
        if (nodeDefinitions == null || !nodeDefinitions.containsKey("nodes")) {
            log.warn("Plugin {} has no node definitions", plugin.getName());
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodeDefinitions.get("nodes");

        for (Map<String, Object> nodeDef : nodes) {
            String nodeType = (String) nodeDef.get("type");
            if (nodeType == null) continue;

            // Create dynamic handler for this node type
            DynamicPluginNodeHandler handler = new DynamicPluginNodeHandler(
                    plugin.getId(),
                    nodeType,
                    nodeDef,
                    version.getConfigSchema()
            );

            globalPluginHandlers.put(nodeType, handler);
            nodeHandlerRegistry.register(handler);

            log.info("Registered global plugin node: {} from plugin {}", nodeType, plugin.getName());
        }
    }

    /**
     * Register plugin nodes for a specific user.
     */
    public void registerPluginNodes(Plugin plugin, PluginVersion version, UUID userId) {
        Map<String, Object> nodeDefinitions = version.getNodeDefinitions();
        if (nodeDefinitions == null || !nodeDefinitions.containsKey("nodes")) {
            log.warn("Plugin {} has no node definitions", plugin.getName());
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodeDefinitions.get("nodes");

        Map<String, DynamicPluginNodeHandler> userHandlers = userPluginHandlers.computeIfAbsent(
                userId, k -> new ConcurrentHashMap<>());

        for (Map<String, Object> nodeDef : nodes) {
            String nodeType = (String) nodeDef.get("type");
            if (nodeType == null) continue;

            // User-specific type includes user prefix (for isolation if needed)
            // For now, we use the same type globally
            DynamicPluginNodeHandler handler = new DynamicPluginNodeHandler(
                    plugin.getId(),
                    nodeType,
                    nodeDef,
                    version.getConfigSchema()
            );

            userHandlers.put(nodeType, handler);

            // Also register globally if not already registered
            if (!globalPluginHandlers.containsKey(nodeType)) {
                globalPluginHandlers.put(nodeType, handler);
                nodeHandlerRegistry.register(handler);
            }

            log.info("Registered plugin node: {} for user {} from plugin {}", nodeType, userId, plugin.getName());
        }
    }

    /**
     * Unregister plugin nodes for a specific user.
     */
    public void unregisterPluginNodes(Plugin plugin, UUID userId) {
        Map<String, DynamicPluginNodeHandler> userHandlers = userPluginHandlers.get(userId);
        if (userHandlers == null) return;

        // Get node types from this plugin
        PluginVersion latestVersion = pluginVersionRepository.findLatestByPluginId(plugin.getId()).orElse(null);
        if (latestVersion == null) return;

        Map<String, Object> nodeDefinitions = latestVersion.getNodeDefinitions();
        if (nodeDefinitions == null || !nodeDefinitions.containsKey("nodes")) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodeDefinitions.get("nodes");

        for (Map<String, Object> nodeDef : nodes) {
            String nodeType = (String) nodeDef.get("type");
            if (nodeType == null) continue;

            userHandlers.remove(nodeType);

            // Check if any other users still have this plugin installed
            boolean stillInUse = pluginInstallationRepository.findAll().stream()
                    .anyMatch(inst -> inst.getPluginId().equals(plugin.getId()) && !inst.getUserId().equals(userId));

            if (!stillInUse) {
                globalPluginHandlers.remove(nodeType);
                // Note: NodeHandlerRegistry doesn't have unregister method,
                // but the handler will no longer be in our tracking map
                log.info("Unregistered global plugin node: {} from plugin {}", nodeType, plugin.getName());
            }

            log.info("Unregistered plugin node: {} for user {} from plugin {}", nodeType, userId, plugin.getName());
        }
    }

    /**
     * Get all plugin node types available for a user.
     */
    public Set<String> getAvailablePluginNodeTypes(UUID userId) {
        Set<String> types = new HashSet<>(globalPluginHandlers.keySet());

        Map<String, DynamicPluginNodeHandler> userHandlers = userPluginHandlers.get(userId);
        if (userHandlers != null) {
            types.addAll(userHandlers.keySet());
        }

        return types;
    }

    /**
     * Get handler for a specific plugin node type.
     */
    public Optional<DynamicPluginNodeHandler> getPluginHandler(String nodeType, UUID userId) {
        // First check user-specific handlers
        Map<String, DynamicPluginNodeHandler> userHandlers = userPluginHandlers.get(userId);
        if (userHandlers != null && userHandlers.containsKey(nodeType)) {
            return Optional.of(userHandlers.get(nodeType));
        }

        // Fall back to global handlers
        return Optional.ofNullable(globalPluginHandlers.get(nodeType));
    }

    /**
     * Check if a node type is from a plugin.
     */
    public boolean isPluginNodeType(String nodeType) {
        return globalPluginHandlers.containsKey(nodeType);
    }

    /**
     * Get plugin ID for a node type.
     */
    public Optional<UUID> getPluginIdForNodeType(String nodeType) {
        DynamicPluginNodeHandler handler = globalPluginHandlers.get(nodeType);
        return handler != null ? Optional.of(handler.getPluginId()) : Optional.empty();
    }
}
