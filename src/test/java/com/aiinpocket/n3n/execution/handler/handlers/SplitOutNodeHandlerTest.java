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
class SplitOutNodeHandlerTest {

    private SplitOutNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SplitOutNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSplitOut() {
        assertThat(handler.getType()).isEqualTo("splitOut");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsSplitOut() {
        assertThat(handler.getDisplayName()).isEqualTo("Split Out");
    }

    // ========== Split List into Individual Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitList_createsIndividualItems() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data")),
                Map.of("data", List.of("a", "b", "c"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("item")).isEqualTo("a");
        assertThat(items.get(1).get("item")).isEqualTo("b");
        assertThat(items.get(2).get("item")).isEqualTo("c");
        assertThat(result.getOutput()).containsEntry("totalCount", 3);
        assertThat(result.getOutput()).containsEntry("outputCount", 3);
    }

    // ========== Include Index ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_includeIndex_addsIndexMetadata() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data", "includeIndex", true)),
                Map.of("data", List.of("x", "y", "z"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(3);

        // First item
        assertThat(items.get(0)).containsEntry("index", 0);
        assertThat(items.get(0)).containsEntry("total", 3);
        assertThat(items.get(0)).containsEntry("isFirst", true);
        assertThat(items.get(0)).containsEntry("isLast", false);

        // Middle item
        assertThat(items.get(1)).containsEntry("index", 1);
        assertThat(items.get(1)).containsEntry("isFirst", false);
        assertThat(items.get(1)).containsEntry("isLast", false);

        // Last item
        assertThat(items.get(2)).containsEntry("index", 2);
        assertThat(items.get(2)).containsEntry("isFirst", false);
        assertThat(items.get(2)).containsEntry("isLast", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_includeIndexFalse_omitsIndexInfo() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data", "includeIndex", false)),
                Map.of("data", List.of("x", "y"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).doesNotContainKey("index");
        assertThat(items.get(0)).doesNotContainKey("total");
        assertThat(items.get(0)).doesNotContainKey("isFirst");
        assertThat(items.get(0)).doesNotContainKey("isLast");
        assertThat(items.get(0)).containsEntry("item", "x");
    }

    // ========== Batch Size ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_batchSize_groupsItems() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data", "batchSize", 2)),
                Map.of("data", List.of("a", "b", "c", "d", "e"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        // 5 items in batches of 2 = 3 batches (2, 2, 1)
        assertThat(items).hasSize(3);
        assertThat((List<Object>) items.get(0).get("items")).containsExactly("a", "b");
        assertThat((List<Object>) items.get(1).get("items")).containsExactly("c", "d");
        assertThat((List<Object>) items.get(2).get("items")).containsExactly("e");
        assertThat(items.get(0)).containsEntry("batchIndex", 0);
        assertThat(items.get(1)).containsEntry("batchIndex", 1);
        assertThat(items.get(2)).containsEntry("batchIndex", 2);
        assertThat(result.getOutput()).containsEntry("totalCount", 5);
        assertThat(result.getOutput()).containsEntry("outputCount", 3);
    }

    // ========== Split String by Delimiter ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitString_byDelimiter() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "text", "delimiter", ",")),
                Map.of("text", "apple,banana,cherry")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("item")).isEqualTo("apple");
        assertThat(items.get(1).get("item")).isEqualTo("banana");
        assertThat(items.get(2).get("item")).isEqualTo("cherry");
    }

    // ========== Split Map into Key/Value Entries ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitMap_intoKeyValueEntries() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        mapData.put("name", "Alice");
        mapData.put("age", 30);

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data")),
                Map.of("data", mapData)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);

        // Verify items contain key/value pairs
        List<Object> keys = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> entry = (Map<String, Object>) item.get("item");
            keys.add(entry.get("key"));
        }
        assertThat(keys).containsExactlyInAnyOrder("name", "age");
    }

    // ========== Nested Field Path ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_fieldPath_nestedArray() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "response.items")),
                Map.of("response", Map.of("items", List.of(1, 2, 3)))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("item")).isEqualTo(1);
        assertThat(items.get(1).get("item")).isEqualTo(2);
        assertThat(items.get(2).get("item")).isEqualTo(3);
    }

    // ========== Empty Array ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_emptyArray_returnsEmptyOutput() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data")),
                Map.of("data", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).isEmpty();
        assertThat(result.getOutput()).containsEntry("totalCount", 0);
        assertThat(result.getOutput()).containsEntry("outputCount", 0);
    }

    // ========== Auto-Detect First List ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_emptyFieldPath_autoDetectsFirstList() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(),  // No fieldPath => auto-detect
                Map.of("someKey", "notAList", "myArray", List.of("a", "b"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("item")).isEqualTo("a");
        assertThat(items.get(1).get("item")).isEqualTo("b");
    }

    // ========== Single Non-List Non-Map Non-String Item ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_singleScalarValue_wrapsInList() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "data")),
                Map.of("data", 42)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("item")).isEqualTo(42);
        assertThat(result.getOutput()).containsEntry("totalCount", 1);
    }

    // ========== String Split Trims and Filters Empty ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_splitString_trimsAndFiltersEmpty() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("fieldPath", "text", "delimiter", ",")),
                Map.of("text", " apple , , banana ")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("item")).isEqualTo("apple");
        assertThat(items.get(1).get("item")).isEqualTo("banana");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("fieldPath");
        assertThat(properties).containsKey("includeIndex");
        assertThat(properties).containsKey("batchSize");
        assertThat(properties).containsKey("delimiter");
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
                .nodeId("splitOut-1")
                .nodeType("splitOut")
                .nodeConfig(config)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
