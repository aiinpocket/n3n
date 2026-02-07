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
class SortNodeHandlerTest {

    private SortNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SortNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSort() {
        assertThat(handler.getType()).isEqualTo("sort");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsSort() {
        assertThat(handler.getDisplayName()).isEqualTo("Sort");
    }

    // ========== Ascending Sort by String Field ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_ascendingByStringField_sortsAlphabetically() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("name", "Charlie")));
        items.add(new HashMap<>(Map.of("name", "Alice")));
        items.add(new HashMap<>(Map.of("name", "Bob")));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "name");
        config.put("order", "ascending");
        config.put("sortType", "string");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).get("name")).isEqualTo("Alice");
        assertThat(sorted.get(1).get("name")).isEqualTo("Bob");
        assertThat(sorted.get(2).get("name")).isEqualTo("Charlie");
    }

    // ========== Descending Sort by String Field ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_descendingByStringField_sortsReverseAlphabetically() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("name", "Alice")));
        items.add(new HashMap<>(Map.of("name", "Charlie")));
        items.add(new HashMap<>(Map.of("name", "Bob")));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "name");
        config.put("order", "descending");
        config.put("sortType", "string");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted.get(0).get("name")).isEqualTo("Charlie");
        assertThat(sorted.get(1).get("name")).isEqualTo("Bob");
        assertThat(sorted.get(2).get("name")).isEqualTo("Alice");
    }

    // ========== Sort by Number Field (auto detect) ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_autoDetectNumberSort_sortsNumerically() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("score", 30)));
        items.add(new HashMap<>(Map.of("score", 10)));
        items.add(new HashMap<>(Map.of("score", 20)));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "score");
        config.put("order", "ascending");
        config.put("sortType", "auto");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted.get(0).get("score")).isEqualTo(10);
        assertThat(sorted.get(1).get("score")).isEqualTo(20);
        assertThat(sorted.get(2).get("score")).isEqualTo(30);
    }

    // ========== Explicit Number Sort Type ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_explicitNumberSort_sortsNumerically() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("price", "100.5")));
        items.add(new HashMap<>(Map.of("price", "9.99")));
        items.add(new HashMap<>(Map.of("price", "50.0")));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "price");
        config.put("order", "ascending");
        config.put("sortType", "number");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted.get(0).get("price")).isEqualTo("9.99");
        assertThat(sorted.get(1).get("price")).isEqualTo("50.0");
        assertThat(sorted.get(2).get("price")).isEqualTo("100.5");
    }

    // ========== Date Sort ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_dateSort_sortsByIsoDate() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("created", "2024-03-15T10:00:00Z")));
        items.add(new HashMap<>(Map.of("created", "2024-01-01T00:00:00Z")));
        items.add(new HashMap<>(Map.of("created", "2024-06-20T15:30:00Z")));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "created");
        config.put("order", "ascending");
        config.put("sortType", "date");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted.get(0).get("created")).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(sorted.get(1).get("created")).isEqualTo("2024-03-15T10:00:00Z");
        assertThat(sorted.get(2).get("created")).isEqualTo("2024-06-20T15:30:00Z");
    }

    // ========== Nested Field Sort ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_nestedFieldSort_sortsByDotNotationPath() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("user", new HashMap<>(Map.of("age", 30)))));
        items.add(new HashMap<>(Map.of("user", new HashMap<>(Map.of("age", 20)))));
        items.add(new HashMap<>(Map.of("user", new HashMap<>(Map.of("age", 25)))));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "user.age");
        config.put("order", "ascending");
        config.put("sortType", "auto");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstUser = (Map<String, Object>) sorted.get(0).get("user");
        assertThat(firstUser.get("age")).isEqualTo(20);
        @SuppressWarnings("unchecked")
        Map<String, Object> lastUser = (Map<String, Object>) sorted.get(2).get("user");
        assertThat(lastUser.get("age")).isEqualTo(30);
    }

    // ========== Empty Array ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_emptyArray_returnsEmpty() {
        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "name");
        config.put("order", "ascending");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", new ArrayList<>());

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> sorted = (List<Object>) result.getOutput().get("items");
        assertThat(sorted).isEmpty();
    }

    // ========== Non-List Input ==========

    @Test
    void execute_nonListInput_returnsInputAsIs() {
        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "name");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", "not a list");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // When input isn't a list, returns inputData as-is
        assertThat(result.getOutput()).containsEntry("items", "not a list");
    }

    // ========== Null Values in Sort ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_nullValuesInSort_handledCorrectly() {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Bob");
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", null);
        items.add(item2);

        Map<String, Object> item3 = new HashMap<>();
        item3.put("name", "Alice");
        items.add(item3);

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "name");
        config.put("order", "ascending");
        config.put("sortType", "string");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted).hasSize(3);
        // null values sorted to beginning in ascending
        assertThat(sorted.get(0).get("name")).isNull();
    }

    // ========== Custom InputKey ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_customInputKey_usesSpecifiedKey() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(new HashMap<>(Map.of("val", 3)));
        records.add(new HashMap<>(Map.of("val", 1)));
        records.add(new HashMap<>(Map.of("val", 2)));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "val");
        config.put("order", "ascending");
        config.put("inputKey", "records");

        Map<String, Object> input = new HashMap<>();
        input.put("records", records);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("records");
        assertThat(sorted.get(0).get("val")).isEqualTo(1);
        assertThat(sorted.get(2).get("val")).isEqualTo(3);
    }

    // ========== Fallback to First List Value ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_inputKeyNotFound_fallsBackToFirstListValue() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(new HashMap<>(Map.of("n", 2)));
        data.add(new HashMap<>(Map.of("n", 1)));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "n");
        config.put("order", "ascending");
        config.put("inputKey", "nonexistent");

        Map<String, Object> input = new HashMap<>();
        input.put("someList", data);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Falls back to first List found in values
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("nonexistent");
        assertThat(sorted).isNotNull();
        assertThat(sorted.get(0).get("n")).isEqualTo(1);
    }

    // ========== Output Metadata ==========

    @Test
    void execute_outputContainsSortMetadata() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("x", 1)));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "x");
        config.put("order", "ascending");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("_sorted", true);
        assertThat(result.getOutput()).containsEntry("_sortedBy", "x");
        assertThat(result.getOutput()).containsEntry("_sortOrder", "ascending");
    }

    // ========== Descending Number Sort ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_descendingNumberSort_highestFirst() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("score", 10)));
        items.add(new HashMap<>(Map.of("score", 50)));
        items.add(new HashMap<>(Map.of("score", 30)));

        Map<String, Object> config = new HashMap<>();
        config.put("sortField", "score");
        config.put("order", "descending");
        config.put("sortType", "number");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(sorted.get(0).get("score")).isEqualTo(50);
        assertThat(sorted.get(1).get("score")).isEqualTo(30);
        assertThat(sorted.get(2).get("score")).isEqualTo(10);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("inputKey");
        assertThat(properties).containsKey("sortField");
        assertThat(properties).containsKey("order");
        assertThat(properties).containsKey("sortType");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("sort-1")
                .nodeType("sort")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
