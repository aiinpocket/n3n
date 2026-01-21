package com.aiinpocket.n3n.execution.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all node handlers.
 * Handlers are automatically registered via Spring autowiring.
 */
@Component
@Slf4j
public class NodeHandlerRegistry {

    private final Map<String, NodeHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Constructor that auto-registers all NodeHandler beans.
     */
    public NodeHandlerRegistry(List<NodeHandler> handlerList) {
        for (NodeHandler handler : handlerList) {
            register(handler);
        }
        log.info("NodeHandlerRegistry initialized with {} handlers: {}",
            handlers.size(), handlers.keySet());
    }

    /**
     * Register a new handler.
     */
    public void register(NodeHandler handler) {
        String type = handler.getType();
        if (handlers.containsKey(type)) {
            log.warn("Overwriting existing handler for type: {}", type);
        }
        handlers.put(type, handler);
        log.debug("Registered handler: {} ({})", type, handler.getDisplayName());
    }

    /**
     * Get a handler by type.
     *
     * @param type the node type
     * @return the handler
     * @throws IllegalArgumentException if no handler found for type
     */
    public NodeHandler getHandler(String type) {
        NodeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for node type: " + type);
        }
        return handler;
    }

    /**
     * Get a handler by type, or empty if not found.
     */
    public Optional<NodeHandler> findHandler(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    /**
     * Check if a handler exists for the given type.
     */
    public boolean hasHandler(String type) {
        return handlers.containsKey(type);
    }

    /**
     * Get all registered handler types.
     */
    public List<String> getRegisteredTypes() {
        return List.copyOf(handlers.keySet());
    }

    /**
     * Get all handlers.
     */
    public List<NodeHandler> getAllHandlers() {
        return List.copyOf(handlers.values());
    }

    /**
     * Get handlers by category.
     */
    public List<NodeHandler> getHandlersByCategory(String category) {
        return handlers.values().stream()
            .filter(h -> category.equals(h.getCategory()))
            .collect(Collectors.toList());
    }

    /**
     * Get all trigger handlers.
     */
    public List<NodeHandler> getTriggerHandlers() {
        return handlers.values().stream()
            .filter(NodeHandler::isTrigger)
            .collect(Collectors.toList());
    }

    /**
     * Get handler info list for API responses.
     */
    public List<NodeHandlerInfo> listHandlerInfo() {
        return handlers.values().stream()
            .map(h -> NodeHandlerInfo.builder()
                .type(h.getType())
                .displayName(h.getDisplayName())
                .description(h.getDescription())
                .category(h.getCategory())
                .icon(h.getIcon())
                .isTrigger(h.isTrigger())
                .supportsAsync(h.supportsAsync())
                .configSchema(h.getConfigSchema())
                .interfaceDefinition(h.getInterfaceDefinition())
                .build())
            .collect(Collectors.toList());
    }
}
