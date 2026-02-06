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
class LoopNodeHandlerTest {

    private LoopNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoopNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsLoop() {
        assertThat(handler.getType()).isEqualTo("loop");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsLoop() {
        assertThat(handler.getDisplayName()).isEqualTo("Loop");
    }

    // ========== Array Input ==========

    @Test
    void execute_listInput_processesAllItems() {
        List<String> items = List.of("a", "b", "c");
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 3);
        assertThat(result.getOutput()).containsEntry("batchSize", 1);
        assertThat(result.getOutput()).containsEntry("totalBatches", 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_listInput_batchSizeOne_createsOneBatchPerItem() {
        List<Integer> items = List.of(1, 2, 3);
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items", "batchSize", 1),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> batches = (List<Map<String, Object>>) result.getOutput().get("batches");
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsEntry("batchIndex", 0);
        assertThat(batches.get(0)).containsEntry("itemsInBatch", 1);
        assertThat(batches.get(0)).containsEntry("totalItems", 3);
    }

    // ========== Batch Size ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_batchSizeTwo_groupsItemsCorrectly() {
        List<String> items = List.of("a", "b", "c", "d", "e");
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items", "batchSize", 2),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalBatches", 3);
        List<Map<String, Object>> batches = (List<Map<String, Object>>) result.getOutput().get("batches");
        assertThat(batches).hasSize(3);
        // First batch has 2 items
        assertThat(batches.get(0)).containsEntry("itemsInBatch", 2);
        // Last batch has 1 item (remainder)
        assertThat(batches.get(2)).containsEntry("itemsInBatch", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_batchSizeLargerThanArray_createsSingleBatch() {
        List<String> items = List.of("a", "b");
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items", "batchSize", 10),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalBatches", 1);
        List<Map<String, Object>> batches = (List<Map<String, Object>>) result.getOutput().get("batches");
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).containsEntry("itemsInBatch", 2);
    }

    // ========== Empty Array ==========

    @Test
    void execute_emptyArray_returnsEmptyResult() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items"),
                Map.of("items", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 0);
        assertThat(result.getOutput()).containsEntry("totalBatches", 0);
    }

    // ========== Null / Missing Array Field ==========

    @Test
    void execute_nullArrayField_returnsSuccessWithEmptyData() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "missing"),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // When array field is missing, returns output with empty data list
        assertThat(result.getOutput()).containsKey("data");
    }

    // ========== Non-Array Input (Wrapping) ==========

    @Test
    void execute_nonArrayInput_wrapsAsSingleItem() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "item"),
                Map.of("item", "single-value")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 1);
        assertThat(result.getOutput()).containsEntry("totalBatches", 1);
    }

    @Test
    void execute_mapInput_wrapsAsSingleItem() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "data"),
                Map.of("data", Map.of("key", "value"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 1);
    }

    // ========== Nested Field Path ==========

    @Test
    void execute_nestedArrayField_resolvesCorrectly() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "data.results"),
                Map.of("data", Map.of("results", List.of("x", "y", "z")))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 3);
    }

    // ========== Default Array Field ==========

    @Test
    void execute_noArrayFieldSpecified_defaultsToItems() {
        NodeExecutionContext context = buildContext(
                Map.of(),
                Map.of("items", List.of("a", "b"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("totalItems", 2);
    }

    // ========== StopOnError Config ==========

    @Test
    void execute_stopOnErrorTrue_includedInOutput() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items", "stopOnError", true),
                Map.of("items", List.of("a"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("stopOnError", true);
    }

    @Test
    void execute_stopOnErrorFalse_includedInOutput() {
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items"),
                Map.of("items", List.of("a"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("stopOnError", false);
    }

    // ========== Batch Metadata ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_batchOutput_containsCorrectMetadata() {
        List<String> items = List.of("a", "b", "c");
        NodeExecutionContext context = buildContext(
                Map.of("arrayField", "items", "batchSize", 2),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Map<String, Object>> batches = (List<Map<String, Object>>) result.getOutput().get("batches");

        // Check first batch metadata
        Map<String, Object> firstBatch = batches.get(0);
        assertThat(firstBatch).containsEntry("batchIndex", 0);
        assertThat(firstBatch).containsEntry("totalBatches", 2);
        assertThat(firstBatch).containsEntry("totalItems", 3);

        // Check second batch
        Map<String, Object> secondBatch = batches.get(1);
        assertThat(secondBatch).containsEntry("batchIndex", 1);
        assertThat(secondBatch).containsEntry("itemsInBatch", 1);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("arrayField");
        assertThat(properties).containsKey("batchSize");
        assertThat(properties).containsKey("stopOnError");
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
                .nodeId("loop-1")
                .nodeType("loop")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
