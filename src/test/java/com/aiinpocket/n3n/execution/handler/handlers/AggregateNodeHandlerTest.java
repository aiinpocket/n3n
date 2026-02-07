package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AggregateNodeHandlerTest {

    private AggregateNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AggregateNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsAggregate() {
        assertThat(handler.getType()).isEqualTo("aggregate");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsAggregate() {
        assertThat(handler.getDisplayName()).isEqualTo("Aggregate");
    }

    // ========== Collect Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_collect_returnsItemsAsIs() {
        List<Object> items = List.of("a", "b", "c");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "collect", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> collected = (List<Object>) result.getOutput().get("result");
        assertThat(collected).containsExactly("a", "b", "c");
        assertThat(result.getOutput()).containsEntry("inputCount", 3);
        assertThat(result.getOutput()).containsEntry("operation", "collect");
    }

    // ========== Count Operation ==========

    @Test
    void execute_count_returnsCorrectCount() {
        List<Object> items = List.of("a", "b", "c", "d");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "count", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", 4);
        assertThat(result.getOutput()).containsEntry("inputCount", 4);
    }

    // ========== Sum Operation ==========

    @Test
    void execute_sum_sumsNumericField() {
        List<Object> items = List.of(
                Map.of("value", 10),
                Map.of("value", 20),
                Map.of("value", 30)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "sum", "field", "value", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(60.0);
    }

    // ========== Average Operation ==========

    @Test
    void execute_average_calculatesAverage() {
        List<Object> items = List.of(
                Map.of("score", 10),
                Map.of("score", 20),
                Map.of("score", 30)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "average", "field", "score", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(20.0);
    }

    @Test
    void execute_avg_calculatesAverageAlias() {
        List<Object> items = List.of(
                Map.of("score", 100),
                Map.of("score", 200)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "avg", "field", "score", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(150.0);
    }

    // ========== Min / Max Operations ==========

    @Test
    void execute_min_findsMinValue() {
        List<Object> items = List.of(
                Map.of("price", 50),
                Map.of("price", 10),
                Map.of("price", 30)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "min", "field", "price", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(10.0);
    }

    @Test
    void execute_max_findsMaxValue() {
        List<Object> items = List.of(
                Map.of("price", 50),
                Map.of("price", 10),
                Map.of("price", 30)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "max", "field", "price", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(50.0);
    }

    // ========== First / Last Operations ==========

    @Test
    void execute_first_returnsFirstItem() {
        List<Object> items = List.of("alpha", "beta", "gamma");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "first", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("alpha");
    }

    @Test
    void execute_last_returnsLastItem() {
        List<Object> items = List.of("alpha", "beta", "gamma");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "last", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("gamma");
    }

    @Test
    void execute_first_emptyList_returnsNull() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "first", "inputKey", "items")),
                Map.of("items", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isNull();
    }

    // ========== Merge Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_merge_mergesMaps() {
        List<Object> items = List.of(
                Map.of("name", "Alice"),
                Map.of("age", 30),
                Map.of("email", "alice@test.com")
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "merge", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = (Map<String, Object>) result.getOutput().get("result");
        assertThat(merged).containsEntry("name", "Alice");
        assertThat(merged).containsEntry("age", 30);
        assertThat(merged).containsEntry("email", "alice@test.com");
    }

    // ========== Concat Operation ==========

    @Test
    void execute_concat_concatenatesFieldValues() {
        List<Object> items = List.of(
                Map.of("text", "Hello"),
                Map.of("text", " "),
                Map.of("text", "World")
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "concat", "field", "text", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("Hello World");
    }

    @Test
    void execute_concat_noField_concatenatesDirectValues() {
        List<Object> items = List.of("Hello", " ", "World");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "concat", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("result")).isEqualTo("Hello World");
    }

    // ========== Unique Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_unique_returnsDistinctValues() {
        List<Object> items = List.of(
                Map.of("color", "red"),
                Map.of("color", "blue"),
                Map.of("color", "red"),
                Map.of("color", "green"),
                Map.of("color", "blue")
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "unique", "field", "color", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("result");
        assertThat(unique).containsExactly("red", "blue", "green");
    }

    // ========== GroupBy ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_groupBy_groupsItemsByField() {
        List<Object> items = List.of(
                Map.of("dept", "engineering", "name", "Alice"),
                Map.of("dept", "sales", "name", "Bob"),
                Map.of("dept", "engineering", "name", "Charlie"),
                Map.of("dept", "sales", "name", "Diana")
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "count", "groupBy", "dept", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> groups = (Map<String, Object>) result.getOutput().get("groups");
        assertThat(groups).containsEntry("engineering", 2);
        assertThat(groups).containsEntry("sales", 2);
        assertThat(result.getOutput()).containsEntry("groupCount", 2);
        assertThat(result.getOutput()).containsEntry("inputCount", 4);
    }

    // ========== Empty Items ==========

    @Test
    void execute_emptyItems_countReturnsZero() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "count", "inputKey", "items")),
                Map.of("items", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", 0);
        assertThat(result.getOutput()).containsEntry("inputCount", 0);
    }

    @Test
    void execute_emptyItems_sumReturnsZero() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "sum", "field", "value", "inputKey", "items")),
                Map.of("items", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(0.0);
    }

    // ========== Auto-Detect Input List ==========

    @Test
    void execute_noInputKeyMatch_autoDetectsFirstList() {
        List<Object> items = List.of(
                Map.of("value", 5),
                Map.of("value", 15)
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "count", "inputKey", "nonExistentKey")),
                Map.of("myList", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Should auto-detect the list in "myList"
        assertThat(result.getOutput()).containsEntry("result", 2);
    }

    // ========== Sum with String Numeric Values ==========

    @Test
    void execute_sum_parsesStringNumbers() {
        List<Object> items = List.of(
                Map.of("amount", "10.5"),
                Map.of("amount", "20.5")
        );

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "sum", "field", "amount", "inputKey", "items")),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat((Double) result.getOutput().get("result")).isEqualTo(31.0);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("inputKey");
        assertThat(properties).containsKey("operation");
        assertThat(properties).containsKey("field");
        assertThat(properties).containsKey("groupBy");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("aggregate-1")
                .nodeType("aggregate")
                .nodeConfig(config)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
