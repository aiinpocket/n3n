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
class ItemListsNodeHandlerTest {

    private ItemListsNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ItemListsNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsItemLists() {
        assertThat(handler.getType()).isEqualTo("itemLists");
    }

    @Test
    void getCategory_returnsDataTransformation() {
        assertThat(handler.getCategory()).isEqualTo("Data Transformation");
    }

    @Test
    void getDisplayName_returnsItemLists() {
        assertThat(handler.getDisplayName()).isEqualTo("Item Lists");
    }

    // ========== Concatenate Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_concatenate_mergesLists() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "concatenate"),
            Map.of(
                "inputA", List.of("a", "b"),
                "inputB", List.of("c", "d")
            )
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly("a", "b", "c", "d");
    }

    // ========== Limit Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_limit_restrictsCount() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "limit", "limit", 2),
            Map.of("items", List.of(1, 2, 3, 4, 5))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
    }

    // ========== Offset Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_offset_skipsItems() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "offset", "offset", 3),
            Map.of("items", List.of(1, 2, 3, 4, 5))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly(4, 5);
    }

    // ========== Slice Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_slice_extractsRange() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "slice", "start", 1, "end", 3),
            Map.of("items", List.of("a", "b", "c", "d", "e"))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly("b", "c");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_slice_negativeStart_fromEnd() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "slice", "start", -2, "end", -1),
            Map.of("items", List.of("a", "b", "c", "d", "e"))
        );
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
    }

    // ========== Shuffle Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_shuffle_maintainsSize() {
        List<Object> original = List.of(1, 2, 3, 4, 5);
        NodeExecutionContext context = buildContext(
            Map.of("operation", "shuffle"),
            Map.of("items", original)
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).hasSize(5);
        assertThat(items).containsExactlyInAnyOrderElementsOf(original);
    }

    // ========== Unique Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_unique_removeDuplicates() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "unique"),
            Map.of("items", List.of(1, 2, 2, 3, 3, 3))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly(1, 2, 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_uniqueByField_removeDuplicates() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "unique", "field", "name"),
            Map.of("items", List.of(
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 30),
                Map.of("name", "Alice", "age", 26)
            ))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
    }

    // ========== Flatten Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_flatten_flattensNestedLists() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "flatten", "depth", 1),
            Map.of("items", List.of(List.of(1, 2), List.of(3, 4), 5))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly(1, 2, 3, 4, 5);
    }

    // ========== Chunk Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_chunk_splitsIntoChunks() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "chunk", "chunkSize", 3),
            Map.of("items", List.of(1, 2, 3, 4, 5, 6, 7))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<List<Object>> chunks = (List<List<Object>>) result.getOutput().get("chunks");
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).containsExactly(1, 2, 3);
        assertThat(chunks.get(1)).containsExactly(4, 5, 6);
        assertThat(chunks.get(2)).containsExactly(7);
    }

    // ========== Reverse Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_reverse_reversesOrder() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "reverse"),
            Map.of("items", List.of(1, 2, 3))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly(3, 2, 1);
    }

    // ========== First/Last Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_first_getsFirstN() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "first", "count", 2),
            Map.of("items", List.of("a", "b", "c", "d"))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly("a", "b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_last_getsLastN() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "last", "count", 2),
            Map.of("items", List.of("a", "b", "c", "d"))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> items = (List<Object>) result.getOutput().get("items");
        assertThat(items).containsExactly("c", "d");
    }

    // ========== Zip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_zip_pairsItems() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "zip"),
            Map.of(
                "inputA", List.of("a", "b", "c"),
                "inputB", List.of(1, 2, 3)
            )
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0)).containsEntry("a", "a").containsEntry("b", 1);
    }

    // ========== GroupBy Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_groupBy_groupsByField() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "groupBy", "field", "category"),
            Map.of("items", List.of(
                Map.of("name", "Apple", "category", "fruit"),
                Map.of("name", "Carrot", "category", "vegetable"),
                Map.of("name", "Banana", "category", "fruit")
            ))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, List<Object>> groups = (Map<String, List<Object>>) result.getOutput().get("groups");
        assertThat(groups).containsKey("fruit");
        assertThat(groups.get("fruit")).hasSize(2);
        assertThat(groups.get("vegetable")).hasSize(1);
    }

    // ========== Count Tests ==========

    @Test
    void execute_count_returnsSize() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "count"),
            Map.of("items", List.of(1, 2, 3))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("count")).isEqualTo(3);
    }

    // ========== IsEmpty Tests ==========

    @Test
    void execute_isEmpty_returnsTrueForEmptyList() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "isEmpty"),
            Map.of("items", List.of())
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("isEmpty")).isEqualTo(true);
    }

    @Test
    void execute_isEmpty_returnsFalseForNonEmptyList() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "isEmpty"),
            Map.of("items", List.of(1))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("isEmpty")).isEqualTo(false);
    }

    // ========== Contains Tests ==========

    @Test
    void execute_contains_findItem() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "contains", "value", "2"),
            Map.of("items", List.of("1", "2", "3"))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("contains")).isEqualTo(true);
    }

    @Test
    void execute_contains_notFound() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "contains", "value", "x"),
            Map.of("items", List.of("a", "b", "c"))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("contains")).isEqualTo(false);
    }

    // ========== Pluck Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_pluck_extractsField() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "pluck", "field", "name"),
            Map.of("items", List.of(
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 30)
            ))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> values = (List<Object>) result.getOutput().get("values");
        assertThat(values).containsExactly("Alice", "Bob");
    }

    // ========== Summarize Tests ==========

    @Test
    void execute_summarize_calculatesStats() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "summarize", "field", "value"),
            Map.of("items", List.of(
                Map.of("value", 10),
                Map.of("value", 20),
                Map.of("value", 30)
            ))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutput().get("sum")).doubleValue()).isEqualTo(60.0);
        assertThat(((Number) result.getOutput().get("average")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) result.getOutput().get("min")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) result.getOutput().get("max")).doubleValue()).isEqualTo(30.0);
    }

    // ========== Edge Cases ==========

    @Test
    void execute_emptyList_handlesGracefully() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "count"),
            Map.of()
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("count")).isEqualTo(0);
    }

    @Test
    void execute_unknownOperation_fails() {
        NodeExecutionContext context = buildContext(
            Map.of("operation", "invalid"),
            Map.of("items", List.of(1))
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void getConfigSchema_hasOperationProperty() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("itemLists")
            .nodeConfig(new HashMap<>(config))
            .inputData(new HashMap<>(inputData))
            .build();
    }
}
