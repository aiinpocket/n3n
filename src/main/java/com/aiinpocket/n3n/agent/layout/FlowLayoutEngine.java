package com.aiinpocket.n3n.agent.layout;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 流程自動排版引擎
 *
 * 將 AI 生成的流程節點自動排列成易讀的布局
 * 使用簡化的分層算法（類似 Sugiyama）
 */
@Component
public class FlowLayoutEngine {

    // 排版參數
    private static final int NODE_WIDTH = 200;
    private static final int NODE_HEIGHT = 80;
    private static final int HORIZONTAL_GAP = 100;
    private static final int VERTICAL_GAP = 80;
    private static final int INITIAL_X = 100;
    private static final int INITIAL_Y = 100;

    /**
     * 對節點進行自動排版
     *
     * @param nodes 節點列表
     * @param edges 邊列表
     * @return 排版後的結果
     */
    public LayoutResult layout(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return new LayoutResult(List.of(), edges != null ? edges : List.of());
        }

        // 建立圖結構
        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Set<String>> incoming = new HashMap<>();
        Set<String> allNodeIds = new HashSet<>();

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            allNodeIds.add(id);
            outgoing.put(id, new HashSet<>());
            incoming.put(id, new HashSet<>());
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                if (source != null && target != null &&
                    allNodeIds.contains(source) && allNodeIds.contains(target)) {
                    outgoing.get(source).add(target);
                    incoming.get(target).add(source);
                }
            }
        }

        // 分層（拓撲排序）
        List<List<String>> layers = assignLayers(allNodeIds, outgoing, incoming);

        // 計算每層的節點位置
        List<Map<String, Object>> layoutedNodes = new ArrayList<>();
        Map<String, int[]> positions = new HashMap<>();

        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);

            // 計算該層的起始 Y 位置（垂直置中）
            int layerHeight = layer.size() * (NODE_HEIGHT + VERTICAL_GAP) - VERTICAL_GAP;
            int startY = INITIAL_Y;

            for (int nodeIndex = 0; nodeIndex < layer.size(); nodeIndex++) {
                String nodeId = layer.get(nodeIndex);
                int x = INITIAL_X + layerIndex * (NODE_WIDTH + HORIZONTAL_GAP);
                int y = startY + nodeIndex * (NODE_HEIGHT + VERTICAL_GAP);

                positions.put(nodeId, new int[]{x, y});
            }
        }

        // 將位置應用到節點
        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            int[] pos = positions.getOrDefault(id, new int[]{INITIAL_X, INITIAL_Y});

            Map<String, Object> layoutedNode = new LinkedHashMap<>(node);
            layoutedNode.put("position", Map.of("x", pos[0], "y", pos[1]));
            layoutedNodes.add(layoutedNode);
        }

        return new LayoutResult(layoutedNodes, edges != null ? edges : List.of());
    }

    /**
     * 使用修改的 Kahn 算法進行分層
     */
    private List<List<String>> assignLayers(
            Set<String> nodeIds,
            Map<String, Set<String>> outgoing,
            Map<String, Set<String>> incoming) {

        List<List<String>> layers = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 計算入度
        for (String id : nodeIds) {
            inDegree.put(id, incoming.get(id).size());
        }

        // 分層處理
        while (assigned.size() < nodeIds.size()) {
            List<String> currentLayer = new ArrayList<>();

            // 找出當前入度為 0 的節點（或尚未處理的節點）
            for (String id : nodeIds) {
                if (!assigned.contains(id)) {
                    int currentInDegree = 0;
                    for (String pred : incoming.get(id)) {
                        if (!assigned.contains(pred)) {
                            currentInDegree++;
                        }
                    }
                    if (currentInDegree == 0) {
                        currentLayer.add(id);
                    }
                }
            }

            // 如果沒有入度為 0 的節點（可能有環），取任意未處理的節點
            if (currentLayer.isEmpty()) {
                for (String id : nodeIds) {
                    if (!assigned.contains(id)) {
                        currentLayer.add(id);
                        break;
                    }
                }
            }

            // 按原始順序排序（保持穩定性）
            currentLayer.sort(Comparator.naturalOrder());

            layers.add(currentLayer);
            assigned.addAll(currentLayer);
        }

        return layers;
    }

    /**
     * 優化分支布局（處理有多個分支的情況）
     *
     * @param nodes 節點列表
     * @param edges 邊列表
     * @return 優化後的結果
     */
    public LayoutResult layoutWithBranches(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        // 基本排版
        LayoutResult basic = layout(nodes, edges);

        if (edges == null || edges.size() <= nodes.size()) {
            return basic;
        }

        // 檢測分支點並調整布局
        Map<String, Integer> outDegree = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            outDegree.merge(source, 1, Integer::sum);
        }

        // 找出分支點（出度 > 1）
        Set<String> branchPoints = new HashSet<>();
        for (Map.Entry<String, Integer> entry : outDegree.entrySet()) {
            if (entry.getValue() > 1) {
                branchPoints.add(entry.getKey());
            }
        }

        if (branchPoints.isEmpty()) {
            return basic;
        }

        // 對分支進行垂直展開
        List<Map<String, Object>> adjustedNodes = new ArrayList<>();
        Map<String, Integer> nodeYOffsets = new HashMap<>();

        for (String branchPoint : branchPoints) {
            List<String> targets = new ArrayList<>();
            for (Map<String, Object> edge : edges) {
                if (branchPoint.equals(edge.get("source"))) {
                    targets.add((String) edge.get("target"));
                }
            }

            // 為目標節點分配 Y 偏移
            int offset = -(targets.size() - 1) * (NODE_HEIGHT + VERTICAL_GAP) / 2;
            for (String target : targets) {
                nodeYOffsets.put(target, offset);
                offset += NODE_HEIGHT + VERTICAL_GAP;
            }
        }

        // 應用偏移
        for (Map<String, Object> node : basic.nodes()) {
            Map<String, Object> adjustedNode = new LinkedHashMap<>(node);
            String id = (String) node.get("id");

            if (nodeYOffsets.containsKey(id)) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> pos = (Map<String, Integer>) node.get("position");
                int newY = pos.get("y") + nodeYOffsets.get(id);
                adjustedNode.put("position", Map.of("x", pos.get("x"), "y", newY));
            }

            adjustedNodes.add(adjustedNode);
        }

        return new LayoutResult(adjustedNodes, edges);
    }

    /**
     * 計算流程圖的邊界
     *
     * @param nodes 節點列表
     * @return 邊界矩形
     */
    public Bounds calculateBounds(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new Bounds(0, 0, 0, 0);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Map<String, Object> node : nodes) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> pos = (Map<String, Integer>) node.get("position");
            if (pos != null) {
                int x = pos.get("x");
                int y = pos.get("y");
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + NODE_WIDTH);
                maxY = Math.max(maxY, y + NODE_HEIGHT);
            }
        }

        return new Bounds(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * 排版結果
     */
    public record LayoutResult(
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges
    ) {}

    /**
     * 邊界矩形
     */
    public record Bounds(int x, int y, int width, int height) {
        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }
    }
}
