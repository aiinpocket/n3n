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
class CompareDatasetNodeHandlerTest {

    private CompareDatasetNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CompareDatasetNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsCompareDatasets() {
        assertThat(handler.getType()).isEqualTo("compareDatasets");
    }

    @Test
    void getCategory_returnsDataTransformation() {
        assertThat(handler.getCategory()).isEqualTo("Data Transformation");
    }

    @Test
    void getDisplayName_returnsCompareDatasets() {
        assertThat(handler.getDisplayName()).isEqualTo("Compare Datasets");
    }

    // ========== Matching Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_identicalDatasets_allMatched() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> matched = (List<Map<String, Object>>) result.getOutput().get("matched");
        assertThat(matched).hasSize(2);

        List<Map<String, Object>> added = (List<Map<String, Object>>) result.getOutput().get("added");
        assertThat(added).isEmpty();

        List<Map<String, Object>> removed = (List<Map<String, Object>>) result.getOutput().get("removed");
        assertThat(removed).isEmpty();

        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).isEmpty();
    }

    // ========== Added Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_newItemInB_detectedAsAdded() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> added = (List<Map<String, Object>>) result.getOutput().get("added");
        assertThat(added).hasSize(1);
        assertThat(added.get(0)).containsEntry("key", "2");
    }

    // ========== Removed Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_itemMissingFromB_detectedAsRemoved() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> removed = (List<Map<String, Object>>) result.getOutput().get("removed");
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0)).containsEntry("key", "2");
    }

    // ========== Changed Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_itemValueChanged_detectedAsChanged() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice", "age", 25))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice", "age", 30))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0)).containsEntry("key", "1");

        List<Map<String, Object>> differences = (List<Map<String, Object>>) changed.get(0).get("differences");
        assertThat(differences).hasSize(1);
        assertThat(differences.get(0)).containsEntry("field", "age");
        assertThat(differences.get(0)).containsEntry("oldValue", 25);
        assertThat(differences.get(0)).containsEntry("newValue", 30);
    }

    // ========== Mode: Added Only ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_addedMode_returnsOnlyAddedItems() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob")),
            new HashMap<>(Map.of("id", "3", "name", "Charlie"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "added");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(2);
        assertThat(result.getOutput()).containsEntry("count", 2);
        // Should not have matched/removed/changed keys
        assertThat(result.getOutput()).doesNotContainKey("matched");
    }

    // ========== Mode: Removed Only ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_removedMode_returnsOnlyRemovedItems() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "removed");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(1);
        assertThat(result.getOutput()).containsEntry("count", 1);
    }

    // ========== Mode: Changed Only ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_changedMode_returnsOnlyChangedItems() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice Updated")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "changed");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getOutput().get("items");
        assertThat(items).hasSize(1);
        assertThat(result.getOutput()).containsEntry("count", 1);
    }

    // ========== Empty Datasets ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_bothDatasetsEmpty_allEmpty() {
        List<Map<String, Object>> datasetA = List.of();
        List<Map<String, Object>> datasetB = List.of();

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        assertThat((List<?>) result.getOutput().get("matched")).isEmpty();
        assertThat((List<?>) result.getOutput().get("added")).isEmpty();
        assertThat((List<?>) result.getOutput().get("removed")).isEmpty();
        assertThat((List<?>) result.getOutput().get("changed")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_datasetAEmpty_allItemsAdded() {
        List<Map<String, Object>> datasetA = List.of();
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        assertThat((List<?>) result.getOutput().get("added")).hasSize(2);
        assertThat((List<?>) result.getOutput().get("removed")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_datasetBEmpty_allItemsRemoved() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice"))
        );
        List<Map<String, Object>> datasetB = List.of();

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        assertThat((List<?>) result.getOutput().get("removed")).hasSize(1);
        assertThat((List<?>) result.getOutput().get("added")).isEmpty();
    }

    // ========== Single Item Datasets ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_singleItemBothSame_matched() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "value", "x"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "value", "x"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        assertThat((List<?>) result.getOutput().get("matched")).hasSize(1);
    }

    // ========== Different Key Fields ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_customCompareKey_usesSpecifiedField() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("email", "alice@test.com", "role", "admin"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("email", "alice@test.com", "role", "user"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "email", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0)).containsEntry("key", "alice@test.com");
    }

    // ========== Null Values in Items ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_nullValueInA_differentFromNonNullInB_detected() {
        Map<String, Object> itemA = new HashMap<>();
        itemA.put("id", "1");
        itemA.put("status", null);

        Map<String, Object> itemB = new HashMap<>();
        itemB.put("id", "1");
        itemB.put("status", "active");

        List<Map<String, Object>> datasetA = List.of(itemA);
        List<Map<String, Object>> datasetB = List.of(itemB);

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);

        List<Map<String, Object>> diffs = (List<Map<String, Object>>) changed.get(0).get("differences");
        assertThat(diffs).anyMatch(d -> "status".equals(d.get("field")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_bothNullValues_consideredMatched() {
        Map<String, Object> itemA = new HashMap<>();
        itemA.put("id", "1");
        itemA.put("notes", null);

        Map<String, Object> itemB = new HashMap<>();
        itemB.put("id", "1");
        itemB.put("notes", null);

        List<Map<String, Object>> datasetA = List.of(itemA);
        List<Map<String, Object>> datasetB = List.of(itemB);

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> matched = (List<Map<String, Object>>) result.getOutput().get("matched");
        assertThat(matched).hasSize(1);
    }

    // ========== Summary Statistics ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_allMode_containsSummaryStatistics() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bob")),
            new HashMap<>(Map.of("id", "3", "name", "Charlie"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            new HashMap<>(Map.of("id", "2", "name", "Bobby")),
            new HashMap<>(Map.of("id", "4", "name", "Dave"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> summary = (Map<String, Object>) result.getOutput().get("summary");
        assertThat(summary).containsEntry("totalA", 3);
        assertThat(summary).containsEntry("totalB", 3);
        assertThat(summary).containsEntry("matchedCount", 1);   // id=1
        assertThat(summary).containsEntry("changedCount", 1);   // id=2
        assertThat(summary).containsEntry("removedCount", 1);   // id=3
        assertThat(summary).containsEntry("addedCount", 1);     // id=4
    }

    // ========== Missing Compare Key ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_itemsMissingCompareKey_skippedInIndex() {
        Map<String, Object> itemWithoutKey = new HashMap<>();
        itemWithoutKey.put("name", "NoId");

        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice")),
            itemWithoutKey
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        // Item without key is skipped, so only id=1 matches
        List<Map<String, Object>> matched = (List<Map<String, Object>>) result.getOutput().get("matched");
        assertThat(matched).hasSize(1);
    }

    // ========== Duplicate Keys ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_duplicateKeysInDataset_lastOneWins() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "First")),
            new HashMap<>(Map.of("id", "1", "name", "Second"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Second"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        // Last entry with id=1 in A is "Second", which matches B
        List<Map<String, Object>> matched = (List<Map<String, Object>>) result.getOutput().get("matched");
        assertThat(matched).hasSize(1);
    }

    // ========== Complex Data ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_nestedObjectsChanged_detectedAsDifference() {
        Map<String, Object> nested1 = new HashMap<>(Map.of("city", "Taipei"));
        Map<String, Object> nested2 = new HashMap<>(Map.of("city", "Tokyo"));

        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "address", nested1))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "address", nested2))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);
    }

    // ========== Missing Dataset ==========

    @Test
    void execute_missingDatasetA_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("compareKey", "id");
        config.put("mode", "all");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("inputB", List.of(Map.of("id", "1")));

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("compare-1")
                .nodeType("compareDatasets")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Dataset A");
    }

    @Test
    void execute_missingDatasetB_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("compareKey", "id");
        config.put("mode", "all");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("inputA", List.of(Map.of("id", "1")));

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("compare-1")
                .nodeType("compareDatasets")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Dataset B");
    }

    // ========== Multiple Field Changes ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_multipleFieldChanges_allDifferencesDetected() {
        List<Map<String, Object>> datasetA = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alice", "age", 25, "city", "Taipei"))
        );
        List<Map<String, Object>> datasetB = List.of(
            new HashMap<>(Map.of("id", "1", "name", "Alicia", "age", 26, "city", "Tokyo"))
        );

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);

        List<Map<String, Object>> diffs = (List<Map<String, Object>>) changed.get(0).get("differences");
        assertThat(diffs).hasSize(3); // name, age, city
    }

    // ========== New Field in B ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_newFieldInB_detectedAsDifference() {
        Map<String, Object> itemA = new HashMap<>();
        itemA.put("id", "1");
        itemA.put("name", "Alice");

        Map<String, Object> itemB = new HashMap<>();
        itemB.put("id", "1");
        itemB.put("name", "Alice");
        itemB.put("email", "alice@example.com");

        List<Map<String, Object>> datasetA = List.of(itemA);
        List<Map<String, Object>> datasetB = List.of(itemB);

        NodeExecutionResult result = executeCompare(datasetA, datasetB, "id", "all");

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> changed = (List<Map<String, Object>>) result.getOutput().get("changed");
        assertThat(changed).hasSize(1);

        List<Map<String, Object>> diffs = (List<Map<String, Object>>) changed.get(0).get("differences");
        assertThat(diffs).anyMatch(d -> "email".equals(d.get("field")) && d.get("oldValue") == null);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("compareKey");
        assertThat(properties).containsKey("mode");
        assertThat(properties).containsKey("inputA");
        assertThat(properties).containsKey("inputB");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helpers ==========

    private NodeExecutionResult executeCompare(
            List<Map<String, Object>> datasetA,
            List<Map<String, Object>> datasetB,
            String compareKey,
            String mode) {

        Map<String, Object> config = new HashMap<>();
        config.put("compareKey", compareKey);
        config.put("mode", mode);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("inputA", new ArrayList<>(datasetA));
        inputData.put("inputB", new ArrayList<>(datasetB));

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("compare-1")
                .nodeType("compareDatasets")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }
}
