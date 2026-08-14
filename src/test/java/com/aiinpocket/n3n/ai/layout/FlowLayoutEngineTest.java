package com.aiinpocket.n3n.ai.layout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlowLayoutEngineTest {

    private FlowLayoutEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FlowLayoutEngine();
    }

    private static Map<String, Object> node(String id) {
        return Map.of("id", id, "type", "code", "label", id);
    }

    private static Map<String, Object> edge(String source, String target) {
        return Map.of("source", source, "target", target);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> positionOf(List<Map<String, Object>> nodes, String id) {
        return nodes.stream()
            .filter(n -> id.equals(n.get("id")))
            .map(n -> (Map<String, Integer>) n.get("position"))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void layout_diamondDag_parallelNodesShareColumnAndSpreadVertically() {
        // 1 → {2,3,4} → 5：三個並行節點應同一 X、不同 Y
        List<Map<String, Object>> nodes = List.of(node("1"), node("2"), node("3"), node("4"), node("5"));
        List<Map<String, Object>> edges = List.of(
            edge("1", "2"), edge("1", "3"), edge("1", "4"),
            edge("2", "5"), edge("3", "5"), edge("4", "5"));

        List<Map<String, Object>> result = engine.layout(nodes, edges).nodes();

        Map<String, Integer> p2 = positionOf(result, "2");
        Map<String, Integer> p3 = positionOf(result, "3");
        Map<String, Integer> p4 = positionOf(result, "4");

        // 並行節點同欄
        assertThat(p2.get("x")).isEqualTo(p3.get("x")).isEqualTo(p4.get("x"));
        // 垂直展開，Y 各不相同
        List<Integer> ys = List.of(p2.get("y"), p3.get("y"), p4.get("y"));
        assertThat(ys.stream().distinct().count()).isEqualTo(3);

        // 串行節點依層往右：1 < 並行層 < 5
        assertThat(positionOf(result, "1").get("x")).isLessThan(p2.get("x"));
        assertThat(p2.get("x")).isLessThan(positionOf(result, "5").get("x"));
    }

    @Test
    void layout_diamondDag_singleNodesVerticallyCenteredAgainstParallelLayer() {
        List<Map<String, Object>> nodes = List.of(node("1"), node("2"), node("3"), node("4"), node("5"));
        List<Map<String, Object>> edges = List.of(
            edge("1", "2"), edge("1", "3"), edge("1", "4"),
            edge("2", "5"), edge("3", "5"), edge("4", "5"));

        List<Map<String, Object>> result = engine.layout(nodes, edges).nodes();

        List<Integer> parallelYs = List.of(
            positionOf(result, "2").get("y"),
            positionOf(result, "3").get("y"),
            positionOf(result, "4").get("y"));
        int middleY = parallelYs.stream().sorted().collect(Collectors.toList()).get(1);

        // 單節點層（1 與 5）應對齊並行層的中線
        assertThat(positionOf(result, "1").get("y")).isEqualTo(middleY);
        assertThat(positionOf(result, "5").get("y")).isEqualTo(middleY);
    }

    @Test
    void layout_linearChain_staysOnOneRow() {
        List<Map<String, Object>> nodes = List.of(node("1"), node("2"), node("3"));
        List<Map<String, Object>> edges = List.of(edge("1", "2"), edge("2", "3"));

        List<Map<String, Object>> result = engine.layout(nodes, edges).nodes();

        assertThat(positionOf(result, "1").get("y"))
            .isEqualTo(positionOf(result, "2").get("y"))
            .isEqualTo(positionOf(result, "3").get("y"));
        assertThat(positionOf(result, "1").get("x")).isLessThan(positionOf(result, "2").get("x"));
        assertThat(positionOf(result, "2").get("x")).isLessThan(positionOf(result, "3").get("x"));
    }
}
