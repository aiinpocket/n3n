package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ActionNodeHandlerTest {

    private ActionNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ActionNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsAction() {
            assertThat(handler.getType()).isEqualTo("action");
        }

        @Test
        void getDisplayName_returnsAction() {
            assertThat(handler.getDisplayName()).isEqualTo("Action");
        }

        @Test
        void getCategory_returnsActions() {
            assertThat(handler.getCategory()).isEqualTo("Actions");
        }

        @Test
        void getConfigSchema_containsProperties() {
            var schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasInputsAndOutputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Passthrough Action")
    class PassthroughAction {
        @Test
        void execute_passthrough_returnsInputAsIs() {
            Map<String, Object> input = new HashMap<>(Map.of("key1", "value1", "key2", 42));
            Map<String, Object> config = Map.of("actionType", "passthrough");
            NodeExecutionContext context = buildContext(config, input);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("key1", "value1");
            assertThat(result.getOutput()).containsEntry("key2", 42);
        }

        @Test
        void execute_defaultAction_isPassthrough() {
            Map<String, Object> input = new HashMap<>(Map.of("data", "test"));
            Map<String, Object> config = new HashMap<>();
            NodeExecutionContext context = buildContext(config, input);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("data", "test");
        }

        @Test
        void execute_passthrough_nullInput_returnsEmptyMap() {
            Map<String, Object> config = Map.of("actionType", "passthrough");
            NodeExecutionContext context = buildContext(config, null);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Transform Action")
    class TransformAction {
        @Test
        void execute_transform_mapsSourcesToTargets() {
            Map<String, Object> input = new HashMap<>(Map.of("firstName", "Alice", "lastName", "Smith"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "transform");
            config.put("mappings", Map.of("name", "firstName", "surname", "lastName"));

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("name", "Alice");
            assertThat(result.getOutput()).containsEntry("surname", "Smith");
        }

        @Test
        void execute_transform_missingSourceKey_skipsThatMapping() {
            Map<String, Object> input = new HashMap<>(Map.of("firstName", "Alice"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "transform");
            config.put("mappings", Map.of("name", "firstName", "age", "nonExistent"));

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("name", "Alice");
            assertThat(result.getOutput()).doesNotContainKey("age");
        }

        @Test
        void execute_transform_nullMappings_returnsEmpty() {
            Map<String, Object> input = new HashMap<>(Map.of("key", "value"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "transform");
            // No mappings

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Set Action")
    class SetAction {
        @Test
        void execute_set_addsValuesToInput() {
            Map<String, Object> input = new HashMap<>(Map.of("existing", "data"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "set");
            config.put("values", Map.of("newKey", "newValue", "count", 5));

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("existing", "data");
            assertThat(result.getOutput()).containsEntry("newKey", "newValue");
            assertThat(result.getOutput()).containsEntry("count", 5);
        }

        @Test
        void execute_set_overwritesExistingKeys() {
            Map<String, Object> input = new HashMap<>(Map.of("key", "old"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "set");
            config.put("values", Map.of("key", "new"));

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("key", "new");
        }
    }

    @Nested
    @DisplayName("Merge Action")
    class MergeAction {
        @Test
        void execute_merge_combinesInputWithAdditionalData() {
            Map<String, Object> input = new HashMap<>(Map.of("a", 1));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "merge");
            config.put("additionalData", Map.of("b", 2, "c", 3));

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("a", 1);
            assertThat(result.getOutput()).containsEntry("b", 2);
            assertThat(result.getOutput()).containsEntry("c", 3);
        }

        @Test
        void execute_merge_nullAdditionalData_returnsInput() {
            Map<String, Object> input = new HashMap<>(Map.of("x", "y"));
            Map<String, Object> config = new HashMap<>();
            config.put("actionType", "merge");

            NodeExecutionContext context = buildContext(config, input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("x", "y");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("action-1")
                .nodeType("action")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
