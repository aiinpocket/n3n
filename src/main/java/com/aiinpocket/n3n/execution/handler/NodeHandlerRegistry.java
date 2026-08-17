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
     * 常見節點名稱的別名 → 實際 handler 類型。
     *
     * 官方範本與 AI 生成的流程慣用這些通俗名稱（csvParser、httpResponse、template…），
     * 語意上平台早就有對應能力，只是類型名不同。在這裡對應起來，
     * 讓這些流程直接可跑，而不是報「unknown type」把使用者擋在門外。
     * 別名只影響查找（getHandler/findHandler/hasHandler），
     * 不出現在節點清單（getRegisteredTypes/listHandlerInfo）避免重複。
     */
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
        Map.entry("calendar", "googleCalendar"),
        Map.entry("cloudStorage", "googleDrive"),
        Map.entry("compress", "compression"),
        Map.entry("csvParser", "spreadsheet"),
        Map.entry("fileRead", "readFile"),
        Map.entry("fileWrite", "writeFile"),
        Map.entry("httpResponse", "respondWebhook"),
        Map.entry("xmlParser", "xml"),
        Map.entry("jsonValidator", "json"),
        Map.entry("ragSearch", "aiRag"),
        Map.entry("splitInBatches", "splitOut"),
        Map.entry("template", "text"),
        Map.entry("socialMedia", "facebook")
    );

    /** 解析別名；真正註冊過的類型優先（若日後有人實作同名 handler，別名自動讓位） */
    private String canonicalType(String type) {
        if (type == null || handlers.containsKey(type)) {
            return type;
        }
        return TYPE_ALIASES.getOrDefault(type, type);
    }

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
        NodeHandler handler = handlers.get(canonicalType(type));
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for node type: " + type);
        }
        return handler;
    }

    /**
     * Get a handler by type, or empty if not found.
     */
    public Optional<NodeHandler> findHandler(String type) {
        return Optional.ofNullable(handlers.get(canonicalType(type)));
    }

    /**
     * Check if a handler exists for the given type.
     */
    public boolean hasHandler(String type) {
        return handlers.containsKey(canonicalType(type));
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
