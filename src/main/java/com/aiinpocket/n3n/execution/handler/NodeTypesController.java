package com.aiinpocket.n3n.execution.handler;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for node type information.
 */
@RestController
@RequestMapping("/api/node-types")
@RequiredArgsConstructor
@Tag(name = "Node Types", description = "Node type registry and information")
public class NodeTypesController {

    private final NodeHandlerRegistry registry;

    /**
     * List all available node types.
     */
    @GetMapping
    public ResponseEntity<List<NodeHandlerInfo>> listNodeTypes() {
        return ResponseEntity.ok(registry.listHandlerInfo());
    }

    /**
     * Get a specific node type by type name.
     */
    @GetMapping("/{type}")
    public ResponseEntity<NodeHandlerInfo> getNodeType(@PathVariable String type) {
        return registry.findHandler(type)
            .map(handler -> NodeHandlerInfo.builder()
                .type(handler.getType())
                .displayName(handler.getDisplayName())
                .description(handler.getDescription())
                .category(handler.getCategory())
                .icon(handler.getIcon())
                .isTrigger(handler.isTrigger())
                .supportsAsync(handler.supportsAsync())
                .configSchema(handler.getConfigSchema())
                .interfaceDefinition(handler.getInterfaceDefinition())
                .build())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the configuration schema for a node type.
     */
    @GetMapping("/{type}/schema")
    public ResponseEntity<Map<String, Object>> getNodeSchema(@PathVariable String type) {
        return registry.findHandler(type)
            .map(NodeHandler::getConfigSchema)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all registered type names.
     */
    @GetMapping("/types")
    public ResponseEntity<List<String>> listTypes() {
        return ResponseEntity.ok(registry.getRegisteredTypes());
    }

    /**
     * List node types by category.
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<NodeHandlerInfo>> listByCategory(@PathVariable String category) {
        List<NodeHandlerInfo> handlers = registry.getHandlersByCategory(category).stream()
            .map(handler -> NodeHandlerInfo.builder()
                .type(handler.getType())
                .displayName(handler.getDisplayName())
                .description(handler.getDescription())
                .category(handler.getCategory())
                .icon(handler.getIcon())
                .isTrigger(handler.isTrigger())
                .supportsAsync(handler.supportsAsync())
                .configSchema(handler.getConfigSchema())
                .interfaceDefinition(handler.getInterfaceDefinition())
                .build())
            .toList();
        return ResponseEntity.ok(handlers);
    }

    /**
     * List all trigger node types.
     */
    @GetMapping("/triggers")
    public ResponseEntity<List<NodeHandlerInfo>> listTriggers() {
        List<NodeHandlerInfo> triggers = registry.getTriggerHandlers().stream()
            .map(handler -> NodeHandlerInfo.builder()
                .type(handler.getType())
                .displayName(handler.getDisplayName())
                .description(handler.getDescription())
                .category(handler.getCategory())
                .icon(handler.getIcon())
                .isTrigger(handler.isTrigger())
                .supportsAsync(handler.supportsAsync())
                .configSchema(handler.getConfigSchema())
                .interfaceDefinition(handler.getInterfaceDefinition())
                .build())
            .toList();
        return ResponseEntity.ok(triggers);
    }
}
