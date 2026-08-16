package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DagParser {

    /** 節點類型與必填設定都以實際註冊的 handler 為準（@Lazy 避開 handler → 執行引擎 → 本類的循環） */
    private final NodeHandlerRegistry nodeHandlerRegistry;

    public DagParser(@Lazy NodeHandlerRegistry nodeHandlerRegistry) {
        this.nodeHandlerRegistry = nodeHandlerRegistry;
    }

    @Data
    public static class ParseResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> entryPoints = new ArrayList<>();
        private List<String> exitPoints = new ArrayList<>();
        private List<String> executionOrder = new ArrayList<>();
        private Map<String, Set<String>> dependencies = new HashMap<>();

        public void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }

        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }

    @Data
    public static class FlowNode {
        private String id;
        private String type;
        private Map<String, Object> position;
        private Map<String, Object> data;
    }

    @Data
    public static class FlowEdge {
        private String id;
        private String source;
        private String target;
        private String sourceHandle;
        private String targetHandle;
        /** Edge type: success (default), error (for error handling), always (always execute) */
        private String edgeType;
        /** Optional label for the edge */
        private String label;

        /**
         * Check if this is an error handling edge
         */
        public boolean isErrorEdge() {
            return "error".equals(edgeType);
        }

        /**
         * Check if this edge should always be followed regardless of success/failure
         */
        public boolean isAlwaysEdge() {
            return "always".equals(edgeType);
        }

        /**
         * Check if this is a success-only edge (default)
         */
        public boolean isSuccessEdge() {
            return edgeType == null || "success".equals(edgeType);
        }
    }

    @Data
    public static class FlowDefinition {
        private List<FlowNode> nodes = new ArrayList<>();
        private List<FlowEdge> edges = new ArrayList<>();
    }

    public ParseResult parse(Map<String, Object> definition) {
        ParseResult result = new ParseResult();
        result.setValid(true);

        if (definition == null) {
            result.addError("Flow definition is null");
            return result;
        }

        // Extract nodes and edges
        List<FlowNode> nodes = extractNodes(definition);
        List<FlowEdge> edges = extractEdges(definition);

        if (nodes.isEmpty()) {
            result.addError("Flow has no nodes");
            return result;
        }

        if (nodes.size() > 1000) {
            result.addError("Flow exceeds maximum node limit of 1000");
            return result;
        }

        if (edges.size() > 5000) {
            result.addError("Flow exceeds maximum edge limit of 5000");
            return result;
        }

        // Build adjacency list and in-degree map
        Map<String, Set<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();

        for (FlowNode node : nodes) {
            nodeIds.add(node.getId());
            adjacency.put(node.getId(), new HashSet<>());
            inDegree.put(node.getId(), 0);
        }

        // Validate edges and build graph
        for (FlowEdge edge : edges) {
            if (!nodeIds.contains(edge.getSource())) {
                result.addError("Edge references non-existent source node: " + edge.getSource());
                continue;
            }
            if (!nodeIds.contains(edge.getTarget())) {
                result.addError("Edge references non-existent target node: " + edge.getTarget());
                continue;
            }
            if (edge.getSource().equals(edge.getTarget())) {
                result.addError("Self-loop detected on node: " + edge.getSource());
                continue;
            }

            // Only increment inDegree if this is a new edge (Set.add returns true)
            // This prevents duplicate edges (e.g., success + error edge between same nodes)
            // from inflating inDegree, which would cause false cycle detection
            boolean isNewEdge = adjacency.get(edge.getSource()).add(edge.getTarget());
            if (isNewEdge) {
                inDegree.put(edge.getTarget(), inDegree.get(edge.getTarget()) + 1);
            }
        }

        // Find entry points (nodes with no incoming edges)
        for (String nodeId : nodeIds) {
            if (inDegree.get(nodeId) == 0) {
                result.getEntryPoints().add(nodeId);
            }
        }

        // Find exit points (nodes with no outgoing edges)
        for (String nodeId : nodeIds) {
            if (adjacency.get(nodeId).isEmpty()) {
                result.getExitPoints().add(nodeId);
            }
        }

        if (result.getEntryPoints().isEmpty()) {
            result.addError("Flow has no entry points (all nodes have incoming edges, possibly a cycle)");
        }

        if (result.getExitPoints().isEmpty()) {
            result.addWarning("Flow has no exit points (all nodes have outgoing edges)");
        }

        // Detect cycles using Kahn's algorithm (topological sort)
        List<String> executionOrder = topologicalSort(adjacency, inDegree, nodeIds.size());

        if (executionOrder == null) {
            result.addError("Cycle detected in flow - flow must be a DAG (Directed Acyclic Graph)");
        } else {
            result.setExecutionOrder(executionOrder);
        }

        // Build dependency map
        for (String nodeId : nodeIds) {
            result.getDependencies().put(nodeId, new HashSet<>());
        }
        for (FlowEdge edge : edges) {
            if (nodeIds.contains(edge.getSource()) && nodeIds.contains(edge.getTarget())) {
                result.getDependencies().get(edge.getTarget()).add(edge.getSource());
            }
        }

        // Validate node types
        validateNodeTypes(nodes, result);

        return result;
    }

    private List<FlowNode> extractNodes(Map<String, Object> definition) {
        List<FlowNode> nodes = new ArrayList<>();
        Object nodesObj = definition.get("nodes");

        if (nodesObj instanceof List<?> nodeList) {
            for (Object obj : nodeList) {
                if (obj instanceof Map<?, ?> nodeMap) {
                    FlowNode node = new FlowNode();
                    node.setId((String) nodeMap.get("id"));
                    node.setType((String) nodeMap.get("type"));
                    node.setPosition(nodeMap.get("position") instanceof Map<?, ?> pos ?
                        (Map<String, Object>) pos : new HashMap<>());
                    node.setData(nodeMap.get("data") instanceof Map<?, ?> data ?
                        (Map<String, Object>) data : new HashMap<>());
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }

    private List<FlowEdge> extractEdges(Map<String, Object> definition) {
        List<FlowEdge> edges = new ArrayList<>();
        Object edgesObj = definition.get("edges");

        if (edgesObj instanceof List<?> edgeList) {
            for (Object obj : edgeList) {
                if (obj instanceof Map<?, ?> edgeMap) {
                    FlowEdge edge = new FlowEdge();
                    edge.setId((String) edgeMap.get("id"));
                    edge.setSource((String) edgeMap.get("source"));
                    edge.setTarget((String) edgeMap.get("target"));
                    edge.setSourceHandle((String) edgeMap.get("sourceHandle"));
                    edge.setTargetHandle((String) edgeMap.get("targetHandle"));
                    edge.setEdgeType((String) edgeMap.get("edgeType"));
                    edge.setLabel((String) edgeMap.get("label"));
                    edges.add(edge);
                }
            }
        }

        return edges;
    }

    /**
     * Get all edges of a flow definition (used by the engine for branch-aware routing).
     */
    public List<FlowEdge> getAllEdges(Map<String, Object> definition) {
        return extractEdges(definition);
    }

    /**
     * Get edges by type for a specific source node
     */
    public List<FlowEdge> getEdgesByType(Map<String, Object> definition, String sourceNodeId, String edgeType) {
        List<FlowEdge> allEdges = extractEdges(definition);
        return allEdges.stream()
            .filter(e -> e.getSource().equals(sourceNodeId))
            .filter(e -> {
                if ("error".equals(edgeType)) return e.isErrorEdge();
                if ("always".equals(edgeType)) return e.isAlwaysEdge();
                return e.isSuccessEdge();
            })
            .toList();
    }

    /**
     * Get all outgoing edges for a node, grouped by type
     */
    public Map<String, List<FlowEdge>> getOutgoingEdgesByType(Map<String, Object> definition, String sourceNodeId) {
        List<FlowEdge> allEdges = extractEdges(definition);
        Map<String, List<FlowEdge>> result = new HashMap<>();
        result.put("success", new ArrayList<>());
        result.put("error", new ArrayList<>());
        result.put("always", new ArrayList<>());

        for (FlowEdge edge : allEdges) {
            if (edge.getSource().equals(sourceNodeId)) {
                if (edge.isErrorEdge()) {
                    result.get("error").add(edge);
                } else if (edge.isAlwaysEdge()) {
                    result.get("always").add(edge);
                } else {
                    result.get("success").add(edge);
                }
            }
        }

        return result;
    }

    private List<String> topologicalSort(Map<String, Set<String>> adjacency,
                                          Map<String, Integer> inDegree,
                                          int nodeCount) {
        // Create a copy of inDegree to avoid modifying the original
        Map<String, Integer> inDegreeCopy = new HashMap<>(inDegree);

        Queue<String> queue = new LinkedList<>();
        List<String> result = new ArrayList<>();

        // Add all nodes with in-degree 0 to the queue
        for (Map.Entry<String, Integer> entry : inDegreeCopy.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);

            for (String neighbor : adjacency.get(node)) {
                inDegreeCopy.put(neighbor, inDegreeCopy.get(neighbor) - 1);
                if (inDegreeCopy.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // If not all nodes are in the result, there's a cycle
        return result.size() == nodeCount ? result : null;
    }

    /**
     * 檢查每個節點的類型裝得起來、且必填設定都填了。
     *
     * 之前這裡比對的是一份寫死的舊類型清單（trigger/action/...），
     * 和實際註冊的節點類型完全對不上，導致每個真實流程都被報「unknown type」，
     * 真正會讓執行失敗的「必填設定沒填」卻完全不檢查——使用者驗證通過、一跑就掛。
     */
    private void validateNodeTypes(List<FlowNode> nodes, ParseResult result) {
        for (FlowNode node : nodes) {
            String type = node.getType();
            if (type == null || type.isBlank()) {
                result.addWarning("Node " + node.getId() + " has no type specified");
                continue;
            }
            if (!nodeHandlerRegistry.hasHandler(type)) {
                result.addWarning("Node " + node.getId() + " has unknown type: " + type);
                continue;
            }
            validateRequiredConfig(node, result);
        }
    }

    /** 必填設定沒填時提早警告，讓使用者在按下執行之前就知道還缺什麼 */
    @SuppressWarnings("unchecked")
    private void validateRequiredConfig(FlowNode node, ParseResult result) {
        NodeHandler handler = nodeHandlerRegistry.findHandler(node.getType()).orElse(null);
        if (handler == null) {
            return;
        }

        Map<String, Object> schema = handler.getConfigSchema();
        if (schema == null || !(schema.get("required") instanceof List<?> requiredList)) {
            return;
        }

        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        List<String> missing = ((List<Object>) requiredList).stream()
            .map(String::valueOf)
            .filter(field -> isBlankValue(data.get(field)))
            .toList();

        if (!missing.isEmpty()) {
            String label = data.get("label") instanceof String s && !s.isBlank() ? s : node.getId();
            result.addWarning("Node " + node.getId() + " (" + label
                + ") is missing required settings: " + String.join(", ", missing));
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    public boolean isValidDag(Map<String, Object> definition) {
        ParseResult result = parse(definition);
        return result.isValid();
    }

    public List<String> getExecutionOrder(Map<String, Object> definition) {
        ParseResult result = parse(definition);
        return result.isValid() ? result.getExecutionOrder() : Collections.emptyList();
    }
}
