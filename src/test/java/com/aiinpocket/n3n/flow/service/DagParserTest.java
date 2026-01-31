package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.base.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DagParserTest {

    private DagParser dagParser;

    @BeforeEach
    void setUp() {
        dagParser = new DagParser();
    }

    // ========== Valid DAG Tests ==========

    @Test
    void parse_simpleLinearFlow_isValid() {
        // Given
        Map<String, Object> definition = TestDataFactory.createSimpleFlowDefinition();

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getEntryPoints()).containsExactly("node1");
        assertThat(result.getExitPoints()).containsExactly("node2");
        assertThat(result.getExecutionOrder()).containsExactly("node1", "node2");
    }

    @Test
    void parse_complexDiamondFlow_isValid() {
        // Given
        Map<String, Object> definition = TestDataFactory.createComplexFlowDefinition();

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getEntryPoints()).containsExactly("trigger");
        assertThat(result.getExitPoints()).containsExactly("output");
        assertThat(result.getExecutionOrder()).hasSize(5);
        // Trigger should come first
        assertThat(result.getExecutionOrder().get(0)).isEqualTo("trigger");
        // Output should come last
        assertThat(result.getExecutionOrder().get(4)).isEqualTo("output");
    }

    @Test
    void parse_parallelBranches_isValid() {
        // Given - Flow with parallel branches that merge
        Map<String, Object> definition = createParallelBranchFlow();

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getEntryPoints()).containsExactly("start");
        assertThat(result.getExitPoints()).containsExactly("end");
    }

    // ========== Invalid DAG Tests ==========

    @Test
    void parse_nullDefinition_returnsError() {
        // When
        DagParser.ParseResult result = dagParser.parse(null);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("Flow definition is null");
    }

    @Test
    void parse_emptyNodes_returnsError() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of());
        definition.put("edges", List.of());

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("Flow has no nodes");
    }

    @Test
    void parse_flowWithCycle_returnsError() {
        // Given
        Map<String, Object> definition = TestDataFactory.createFlowDefinitionWithCycle();

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Cycle detected"));
    }

    @Test
    void parse_selfLoop_returnsError() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("node1", "trigger")
        ));
        definition.put("edges", List.of(
                createEdge("e1", "node1", "node1")  // Self-loop
        ));

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Self-loop"));
    }

    @Test
    void parse_edgeToNonExistentNode_returnsError() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("node1", "trigger")
        ));
        definition.put("edges", List.of(
                createEdge("e1", "node1", "node2")  // node2 doesn't exist
        ));

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("non-existent target node"));
    }

    @Test
    void parse_edgeFromNonExistentNode_returnsError() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("node1", "trigger")
        ));
        definition.put("edges", List.of(
                createEdge("e1", "node2", "node1")  // node2 doesn't exist
        ));

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("non-existent source node"));
    }

    // ========== Warning Tests ==========

    @Test
    void parse_unknownNodeType_addsWarning() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("node1", "unknown_type")
        ));
        definition.put("edges", List.of());

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("unknown type"));
    }

    @Test
    void parse_nodeWithoutType_addsWarning() {
        // Given
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node1");
        node.put("position", Map.of("x", 0, "y", 0));
        // No type specified

        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(node));
        definition.put("edges", List.of());

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("no type specified"));
    }

    // ========== Dependencies Tests ==========

    @Test
    void parse_buildsDependencyMap() {
        // Given
        Map<String, Object> definition = TestDataFactory.createSimpleFlowDefinition();

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.getDependencies()).containsKey("node1");
        assertThat(result.getDependencies()).containsKey("node2");
        assertThat(result.getDependencies().get("node1")).isEmpty();
        assertThat(result.getDependencies().get("node2")).containsExactly("node1");
    }

    @Test
    void parse_fanInDependencies() {
        // Given - node3 depends on both node1 and node2
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("node1", "trigger"),
                createNode("node2", "trigger"),
                createNode("node3", "action")
        ));
        definition.put("edges", List.of(
                createEdge("e1", "node1", "node3"),
                createEdge("e2", "node2", "node3")
        ));

        // When
        DagParser.ParseResult result = dagParser.parse(definition);

        // Then
        assertThat(result.getDependencies().get("node3"))
                .containsExactlyInAnyOrder("node1", "node2");
    }

    // ========== Helper Methods ==========

    @Test
    void isValidDag_validFlow_returnsTrue() {
        // Given
        Map<String, Object> definition = TestDataFactory.createSimpleFlowDefinition();

        // When
        boolean valid = dagParser.isValidDag(definition);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidDag_cycleFlow_returnsFalse() {
        // Given
        Map<String, Object> definition = TestDataFactory.createFlowDefinitionWithCycle();

        // When
        boolean valid = dagParser.isValidDag(definition);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void getExecutionOrder_validFlow_returnsOrder() {
        // Given
        Map<String, Object> definition = TestDataFactory.createSimpleFlowDefinition();

        // When
        List<String> order = dagParser.getExecutionOrder(definition);

        // Then
        assertThat(order).containsExactly("node1", "node2");
    }

    @Test
    void getExecutionOrder_invalidFlow_returnsEmpty() {
        // Given
        Map<String, Object> definition = TestDataFactory.createFlowDefinitionWithCycle();

        // When
        List<String> order = dagParser.getExecutionOrder(definition);

        // Then
        assertThat(order).isEmpty();
    }

    // ========== Test Data Helpers ==========

    private Map<String, Object> createNode(String id, String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("position", Map.of("x", 0, "y", 0));
        node.put("data", Map.of("label", id));
        return node;
    }

    private Map<String, Object> createEdge(String id, String source, String target) {
        Map<String, Object> edge = new HashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        return edge;
    }

    private Map<String, Object> createParallelBranchFlow() {
        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", List.of(
                createNode("start", "trigger"),
                createNode("branch1", "action"),
                createNode("branch2", "action"),
                createNode("end", "output")
        ));
        definition.put("edges", List.of(
                createEdge("e1", "start", "branch1"),
                createEdge("e2", "start", "branch2"),
                createEdge("e3", "branch1", "end"),
                createEdge("e4", "branch2", "end")
        ));
        return definition;
    }
}
