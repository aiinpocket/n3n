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
class MergeNodeHandlerTest {

    private MergeNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MergeNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsMerge() {
        assertThat(handler.getType()).isEqualTo("merge");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsMerge() {
        assertThat(handler.getDisplayName()).isEqualTo("Merge");
    }

    // ========== Append Mode (Default) ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_appendMode_combinesValuesIntoList() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append")),
                Map.of("branch1", "hello", "branch2", "world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).containsExactlyInAnyOrder("hello", "world");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_appendMode_flattensNestedLists() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append")),
                Map.of("branch1", List.of("a", "b"), "branch2", List.of("c", "d"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_appendMode_mixesListsAndScalars() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append")),
                Map.of("branch1", List.of(1, 2), "branch2", 3)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_defaultMode_usesAppend() {
        // No mode specified => defaults to "append"
        NodeExecutionContext context = buildContext(
                new HashMap<>(),
                Map.of("branch1", "hello", "branch2", "world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).containsExactlyInAnyOrder("hello", "world");
    }

    // ========== Combine Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_combineMode_mergesMaps() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "combine")),
                Map.of(
                        "branch1", Map.of("name", "Alice", "age", 30),
                        "branch2", Map.of("email", "alice@test.com")
                )
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = (Map<String, Object>) result.getOutput().get("merged");
        assertThat(merged).containsEntry("name", "Alice");
        assertThat(merged).containsEntry("age", 30);
        assertThat(merged).containsEntry("email", "alice@test.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_combineMode_nonMapValuesKeyedByInputKey() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "combine")),
                Map.of("branch1", "scalarValue", "branch2", Map.of("key", "value"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = (Map<String, Object>) result.getOutput().get("merged");
        assertThat(merged).containsEntry("branch1", "scalarValue");
        assertThat(merged).containsEntry("key", "value");
    }

    // ========== Multiplex Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_multiplexMode_returnsAllInputData() {
        Map<String, Object> inputData = Map.of("branch1", "data1", "branch2", "data2");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "multiplex")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = (Map<String, Object>) result.getOutput().get("merged");
        assertThat(merged).containsEntry("branch1", "data1");
        assertThat(merged).containsEntry("branch2", "data2");
    }

    // ========== ChooseBranch Mode ==========

    @Test
    void execute_chooseBranchMode_returnsFirstNonNull() {
        // Use a LinkedHashMap to guarantee insertion order
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("branch1", "firstValue");
        inputData.put("branch2", "secondValue");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "chooseBranch")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Object merged = result.getOutput().get("merged");
        assertThat(merged).isEqualTo("firstValue");
    }

    @Test
    void execute_chooseBranchMode_skipsNullValues() {
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("branch1", null);
        inputData.put("branch2", "secondValue");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "chooseBranch")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Object merged = result.getOutput().get("merged");
        assertThat(merged).isEqualTo("secondValue");
    }

    @Test
    void execute_chooseBranchMode_allNull_returnsNull() {
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("branch1", null);
        inputData.put("branch2", null);

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "chooseBranch")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Object merged = result.getOutput().get("merged");
        assertThat(merged).isNull();
    }

    // ========== Custom OutputKey ==========

    @Test
    void execute_customOutputKey_usesSpecifiedKey() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append", "outputKey", "combined")),
                Map.of("branch1", "hello")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("combined");
        assertThat(result.getOutput()).doesNotContainKey("merged");
    }

    // ========== Null Values Handling ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_appendMode_nullValuesSkipped() {
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("branch1", "hello");
        inputData.put("branch2", null);
        inputData.put("branch3", "world");

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).containsExactlyInAnyOrder("hello", "world");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_combineMode_nullValuesSkipped() {
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("branch1", Map.of("a", 1));
        inputData.put("branch2", null);

        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "combine")),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> merged = (Map<String, Object>) result.getOutput().get("merged");
        assertThat(merged).containsEntry("a", 1);
        assertThat(merged).doesNotContainKey("branch2");
    }

    // ========== Empty Input ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_emptyInput_returnsEmptyList() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("mode", "append")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> merged = (List<Object>) result.getOutput().get("merged");
        assertThat(merged).isEmpty();
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("mode");
        assertThat(properties).containsKey("outputKey");
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
                .nodeId("merge-1")
                .nodeType("merge")
                .nodeConfig(config)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
