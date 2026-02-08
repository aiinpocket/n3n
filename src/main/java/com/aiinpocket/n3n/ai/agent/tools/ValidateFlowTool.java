package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 驗證流程工具
 * 檢查流程是否有效、完整、可執行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateFlowTool implements AgentTool {

    private final NodeHandlerRegistry nodeHandlerRegistry;

    @Override
    public String getName() {
        return "validate_flow";
    }

    @Override
    public String getDescription() {
        return "Validate whether a flow is valid. Checks node types, connections, and required configuration.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "checkMissingNodes", Map.of(
                    "type", "boolean",
                    "description", "Whether to check for missing node types, default true"
                ),
                "checkConnectivity", Map.of(
                    "type", "boolean",
                    "description", "Whether to check node connectivity, default true"
                ),
                "checkConfig", Map.of(
                    "type", "boolean",
                    "description", "Whether to check required configuration, default true"
                )
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        boolean checkMissingNodes = parameters.get("checkMissingNodes") != Boolean.FALSE;
        boolean checkConnectivity = parameters.get("checkConnectivity") != Boolean.FALSE;
        boolean checkConfig = parameters.get("checkConfig") != Boolean.FALSE;

        log.debug("Validating flow: checkMissing={}, checkConnectivity={}, checkConfig={}",
            checkMissingNodes, checkConnectivity, checkConfig);

        try {
            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return ToolResult.builder()
                    .toolName(getName())
                    .success(true)
                    .data(Map.of(
                        "valid", false,
                        "errors", List.of("Flow is empty or does not exist"),
                        "warnings", List.of(),
                        "missingNodes", List.of()
                    ))
                    .build();
            }

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> missingNodeTypes = new ArrayList<>();

            // 1. 檢查節點類型是否存在
            if (checkMissingNodes) {
                for (WorkingFlowDraft.Node node : draft.getNodes()) {
                    if (!nodeHandlerRegistry.hasHandler(node.type())) {
                        missingNodeTypes.add(node.type());
                        errors.add("Node '" + node.label() + "' uses uninstalled type: " + node.type());
                    }
                }
            }

            // 2. 檢查是否有觸發器
            boolean hasTrigger = draft.getNodes().stream()
                .anyMatch(n -> {
                    Optional<NodeHandler> handler = nodeHandlerRegistry.findHandler(n.type());
                    return handler.map(NodeHandler::isTrigger).orElse(false);
                });

            if (!hasTrigger) {
                warnings.add("Flow has no trigger node and requires manual execution");
            }

            // 3. 檢查連接性
            if (checkConnectivity) {
                Set<String> connectedNodes = new HashSet<>();
                for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
                    connectedNodes.add(edge.source());
                    connectedNodes.add(edge.target());
                }

                // 找孤立節點
                List<String> isolatedNodes = draft.getNodes().stream()
                    .filter(n -> !connectedNodes.contains(n.id()))
                    .map(WorkingFlowDraft.Node::label)
                    .collect(Collectors.toList());

                if (!isolatedNodes.isEmpty() && draft.getNodeCount() > 1) {
                    warnings.add("The following nodes are not connected: " + String.join(", ", isolatedNodes));
                }

                // 檢查是否有循環（簡單檢測）
                if (hasCycle(draft)) {
                    errors.add("Flow contains circular dependencies");
                }
            }

            // 4. 檢查必要配置
            if (checkConfig) {
                for (WorkingFlowDraft.Node node : draft.getNodes()) {
                    List<String> missingConfigs = checkRequiredConfig(node);
                    if (!missingConfigs.isEmpty()) {
                        warnings.add("Node '" + node.label() + "' missing configuration: " +
                            String.join(", ", missingConfigs));
                    }
                }
            }

            boolean isValid = errors.isEmpty();

            // 儲存驗證結果到工作記憶
            context.setInMemory("validationResult", Map.of(
                "valid", isValid,
                "errors", errors,
                "warnings", warnings,
                "missingNodes", missingNodeTypes
            ));

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "valid", isValid,
                    "errors", errors,
                    "warnings", warnings,
                    "missingNodes", missingNodeTypes,
                    "nodeCount", draft.getNodeCount(),
                    "edgeCount", draft.getEdgeCount()
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to validate flow", e);
            return ToolResult.failure(getName(), "Failed to validate flow");
        }
    }

    private boolean hasCycle(WorkingFlowDraft draft) {
        // 使用 DFS 檢測循環
        Map<String, Integer> state = new HashMap<>(); // 0: 未訪問, 1: 訪問中, 2: 已完成
        Map<String, List<String>> graph = new HashMap<>();

        // 建立鄰接表
        for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
            graph.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge.target());
        }

        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            state.put(node.id(), 0);
        }

        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            if (state.get(node.id()) == 0) {
                if (hasCycleDFS(node.id(), graph, state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCycleDFS(String node, Map<String, List<String>> graph, Map<String, Integer> state) {
        state.put(node, 1); // 訪問中

        List<String> neighbors = graph.getOrDefault(node, List.of());
        for (String neighbor : neighbors) {
            int neighborState = state.getOrDefault(neighbor, 0);
            if (neighborState == 1) {
                return true; // 發現循環
            }
            if (neighborState == 0 && hasCycleDFS(neighbor, graph, state)) {
                return true;
            }
        }

        state.put(node, 2); // 已完成
        return false;
    }

    private List<String> checkRequiredConfig(WorkingFlowDraft.Node node) {
        List<String> missing = new ArrayList<>();

        // 根據節點類型檢查必要配置
        String type = node.type();
        Map<String, Object> config = node.config();

        switch (type) {
            case "httpRequest":
                if (!config.containsKey("url") || isEmptyString(config.get("url"))) {
                    missing.add("url");
                }
                break;
            case "sendEmail":
                if (!config.containsKey("to") || isEmptyString(config.get("to"))) {
                    missing.add("to");
                }
                break;
            case "database":
            case "postgres":
            case "mysql":
                if (!config.containsKey("query") || isEmptyString(config.get("query"))) {
                    missing.add("query");
                }
                break;
            case "code":
                if (!config.containsKey("code") || isEmptyString(config.get("code"))) {
                    missing.add("code");
                }
                break;
            case "scheduleTrigger":
                if (!config.containsKey("cronExpression") || isEmptyString(config.get("cronExpression"))) {
                    missing.add("cronExpression");
                }
                break;
            // 其他節點類型...
        }

        return missing;
    }

    private boolean isEmptyString(Object value) {
        if (value == null) return true;
        if (value instanceof String) {
            return ((String) value).isBlank();
        }
        return false;
    }
}
