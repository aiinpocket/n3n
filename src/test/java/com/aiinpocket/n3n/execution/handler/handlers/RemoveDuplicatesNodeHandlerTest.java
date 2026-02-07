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
class RemoveDuplicatesNodeHandlerTest {

    private RemoveDuplicatesNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoveDuplicatesNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRemoveDuplicates() {
        assertThat(handler.getType()).isEqualTo("removeDuplicates");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsRemoveDuplicates() {
        assertThat(handler.getDisplayName()).isEqualTo("Remove Duplicates");
    }

    // ========== Remove Duplicate Strings ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_duplicateStrings_removesExactDuplicates() {
        List<Object> items = new ArrayList<>(List.of("apple", "banana", "apple", "cherry", "banana"));

        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(3);
        assertThat(unique).containsExactly("apple", "banana", "cherry");
    }

    // ========== Remove Duplicate Maps (entire object comparison) ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_duplicateMaps_removesIdenticalObjects() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("name", "Alice", "age", 30)));
        items.add(new HashMap<>(Map.of("name", "Bob", "age", 25)));
        items.add(new HashMap<>(Map.of("name", "Alice", "age", 30)));

        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(2);
        assertThat(result.getOutput()).containsEntry("removedCount", 1);
    }

    // ========== Compare by Specific Field ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_compareByField_deduplicatesByFieldValue() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("id", "1", "name", "Alice")));
        items.add(new HashMap<>(Map.of("id", "2", "name", "Bob")));
        items.add(new HashMap<>(Map.of("id", "1", "name", "Alice Updated")));

        Map<String, Object> config = new HashMap<>();
        config.put("compareField", "id");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(2);
        // Default keepMode is "first", so "Alice" should be kept, not "Alice Updated"
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) unique.get(0);
        assertThat(first.get("name")).isEqualTo("Alice");
    }

    // ========== keepMode=last ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_keepModeLast_keepsLastOccurrence() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("id", "1", "version", "v1")));
        items.add(new HashMap<>(Map.of("id", "2", "version", "v1")));
        items.add(new HashMap<>(Map.of("id", "1", "version", "v2")));

        Map<String, Object> config = new HashMap<>();
        config.put("compareField", "id");
        config.put("keepMode", "last");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(2);
        // "last" mode: the second occurrence of id=1 (version=v2) should be kept
        boolean foundV2 = unique.stream()
                .filter(o -> o instanceof Map)
                .map(o -> (Map<String, Object>) o)
                .anyMatch(m -> "1".equals(m.get("id")) && "v2".equals(m.get("version")));
        assertThat(foundV2).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_keepModeLast_firstOccurrenceGoesToDuplicates() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("id", "1", "version", "old")));
        items.add(new HashMap<>(Map.of("id", "1", "version", "new")));

        Map<String, Object> config = new HashMap<>();
        config.put("compareField", "id");
        config.put("keepMode", "last");
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> duplicates = (List<Object>) result.getOutput().get("duplicates");
        assertThat(duplicates).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> dup = (Map<String, Object>) duplicates.get(0);
        assertThat(dup.get("version")).isEqualTo("old");
    }

    // ========== Case Insensitive ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_caseInsensitive_treatsAsSameCaseStrings() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("tag", "Java")));
        items.add(new HashMap<>(Map.of("tag", "java")));
        items.add(new HashMap<>(Map.of("tag", "JAVA")));
        items.add(new HashMap<>(Map.of("tag", "Python")));

        Map<String, Object> config = new HashMap<>();
        config.put("compareField", "tag");
        config.put("caseSensitive", false);
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(2);
        assertThat(result.getOutput()).containsEntry("removedCount", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_caseSensitiveDefault_treatsDifferentCase() {
        List<Object> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("tag", "Java")));
        items.add(new HashMap<>(Map.of("tag", "java")));

        Map<String, Object> config = new HashMap<>();
        config.put("compareField", "tag");
        config.put("inputKey", "items");
        // caseSensitive defaults to true

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(2); // "Java" != "java" when case sensitive
    }

    // ========== No Duplicates ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_noDuplicates_returnsAllItems() {
        List<Object> items = new ArrayList<>(List.of("a", "b", "c"));

        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).hasSize(3);
        assertThat(result.getOutput()).containsEntry("uniqueCount", 3);
        assertThat(result.getOutput()).containsEntry("duplicateCount", 0);
        assertThat(result.getOutput()).containsEntry("removedCount", 0);
    }

    // ========== Non-List Input ==========

    @Test
    void execute_nonListInput_returnsInputAsIs() {
        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", "not a list");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("items", "not a list");
    }

    // ========== Output Counts ==========

    @Test
    void execute_outputContainsCorrectCounts() {
        List<Object> items = new ArrayList<>(List.of("x", "y", "x", "z", "y", "x"));

        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", items);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("uniqueCount", 3);
        assertThat(result.getOutput()).containsEntry("duplicateCount", 3);
        assertThat(result.getOutput()).containsEntry("removedCount", 3);
    }

    // ========== Empty List ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_emptyList_returnsEmptyResults() {
        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "items");

        Map<String, Object> input = new HashMap<>();
        input.put("items", new ArrayList<>());

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> unique = (List<Object>) result.getOutput().get("items");
        assertThat(unique).isEmpty();
        assertThat(result.getOutput()).containsEntry("uniqueCount", 0);
        assertThat(result.getOutput()).containsEntry("removedCount", 0);
    }

    // ========== Fallback to First List ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_inputKeyNotFound_fallsBackToFirstListValue() {
        List<Object> data = new ArrayList<>(List.of("a", "a", "b"));

        Map<String, Object> config = new HashMap<>();
        config.put("inputKey", "nonexistent");

        Map<String, Object> input = new HashMap<>();
        input.put("myData", data);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Falls back to first List found, output stored under original inputKey
        assertThat(result.getOutput()).containsEntry("uniqueCount", 2);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("inputKey");
        assertThat(properties).containsKey("compareField");
        assertThat(properties).containsKey("keepMode");
        assertThat(properties).containsKey("caseSensitive");
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
                .nodeId("removeDuplicates-1")
                .nodeType("removeDuplicates")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
