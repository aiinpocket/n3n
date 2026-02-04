package com.aiinpocket.n3n.ai.agent;

import lombok.Data;
import java.util.*;

/**
 * 工作中的流程草稿
 * 在多代理協作過程中逐步建構
 */
@Data
public class WorkingFlowDraft {

    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private int nodeCounter = 0;

    /**
     * 新增節點
     */
    public String addNode(String type, String label, Map<String, Object> config) {
        String id = "node_" + (++nodeCounter);
        Position position = calculatePosition();
        nodes.add(new Node(id, type, label, position, config != null ? new HashMap<>(config) : new HashMap<>()));
        return id;
    }

    /**
     * 移除節點
     */
    public boolean removeNode(String nodeId) {
        boolean removed = nodes.removeIf(n -> n.id().equals(nodeId));
        if (removed) {
            // 同時移除相關的邊
            edges.removeIf(e -> e.source().equals(nodeId) || e.target().equals(nodeId));
        }
        return removed;
    }

    /**
     * 連接節點
     */
    public void connectNodes(String sourceId, String targetId) {
        String edgeId = "edge_" + sourceId + "_" + targetId;
        // 檢查是否已存在
        boolean exists = edges.stream()
            .anyMatch(e -> e.source().equals(sourceId) && e.target().equals(targetId));
        if (!exists) {
            edges.add(new Edge(edgeId, sourceId, targetId));
        }
    }

    /**
     * 配置節點
     */
    public void configureNode(String nodeId, Map<String, Object> config) {
        nodes.stream()
            .filter(n -> n.id().equals(nodeId))
            .findFirst()
            .ifPresent(n -> n.config().putAll(config));
    }

    /**
     * 取得節點
     */
    public Optional<Node> getNode(String nodeId) {
        return nodes.stream()
            .filter(n -> n.id().equals(nodeId))
            .findFirst();
    }

    /**
     * 是否有內容
     */
    public boolean hasContent() {
        return !nodes.isEmpty();
    }

    /**
     * 取得節點數量
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * 取得邊數量
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * 轉換為流程定義 Map
     */
    public Map<String, Object> toDefinition() {
        return Map.of(
            "nodes", nodes.stream().map(Node::toMap).toList(),
            "edges", edges.stream().map(Edge::toMap).toList()
        );
    }

    /**
     * 從現有定義初始化
     */
    @SuppressWarnings("unchecked")
    public void initializeFromDefinition(Map<String, Object> definition) {
        if (definition == null) return;

        List<Map<String, Object>> nodeList = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edgeList = (List<Map<String, Object>>) definition.get("edges");

        if (nodeList != null) {
            for (Map<String, Object> nodeMap : nodeList) {
                String id = (String) nodeMap.get("id");
                String type = (String) nodeMap.get("type");
                Map<String, Object> data = (Map<String, Object>) nodeMap.get("data");
                String label = data != null ? (String) data.get("label") : type;
                Map<String, Object> positionMap = (Map<String, Object>) nodeMap.get("position");

                Position position = new Position(
                    ((Number) positionMap.get("x")).intValue(),
                    ((Number) positionMap.get("y")).intValue()
                );

                Map<String, Object> config = data != null ?
                    (Map<String, Object>) data.get("config") : new HashMap<>();

                nodes.add(new Node(id, type, label, position, config != null ? config : new HashMap<>()));

                // 更新計數器
                if (id.startsWith("node_")) {
                    try {
                        int num = Integer.parseInt(id.substring(5));
                        if (num > nodeCounter) {
                            nodeCounter = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (edgeList != null) {
            for (Map<String, Object> edgeMap : edgeList) {
                String id = (String) edgeMap.get("id");
                String source = (String) edgeMap.get("source");
                String target = (String) edgeMap.get("target");
                edges.add(new Edge(id != null ? id : "edge_" + source + "_" + target, source, target));
            }
        }
    }

    /**
     * 計算新節點位置
     */
    private Position calculatePosition() {
        int x = 100 + (nodeCounter % 3) * 200;
        int y = 100 + (nodeCounter / 3) * 150;
        return new Position(x, y);
    }

    /**
     * 節點記錄
     */
    public record Node(
        String id,
        String type,
        String label,
        Position position,
        Map<String, Object> config
    ) {
        public Node {
            if (config == null) config = new HashMap<>();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("label", label);
            dataMap.put("nodeType", type);
            if (config != null && !config.isEmpty()) {
                dataMap.put("config", config);
            }

            return Map.of(
                "id", id,
                "type", type,
                "data", dataMap,
                "position", Map.of("x", position.x, "y", position.y)
            );
        }
    }

    /**
     * 邊記錄
     */
    public record Edge(String id, String source, String target) {
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "source", source,
                "target", target
            );
        }
    }

    /**
     * 位置記錄
     */
    public record Position(int x, int y) {}
}
