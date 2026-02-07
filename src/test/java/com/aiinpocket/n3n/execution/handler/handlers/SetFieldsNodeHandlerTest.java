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
class SetFieldsNodeHandlerTest {

    private SetFieldsNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SetFieldsNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSetFields() {
        assertThat(handler.getType()).isEqualTo("setFields");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsEditFieldsSet() {
        assertThat(handler.getDisplayName()).isEqualTo("Edit Fields (Set)");
    }

    // ========== Default Mode (add) ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_defaultMode_addsNewFieldsOnly() {
        // Default mode is "add" - should only add fields not already present
        Map<String, Object> fields = new HashMap<>();
        fields.put("existing", "new-value");
        fields.put("added", "brand-new");

        Map<String, Object> config = new HashMap<>();
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "original-value");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // "add" mode: existing key should NOT be overwritten
        assertThat(result.getOutput()).containsEntry("existing", "original-value");
        // new key should be added
        assertThat(result.getOutput()).containsEntry("added", "brand-new");
    }

    // ========== Update Mode ==========

    @Test
    void execute_updateMode_overwritesExistingFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "Updated Name");
        fields.put("newField", "New Value");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Original Name");
        input.put("untouched", "stays");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("name", "Updated Name");
        assertThat(result.getOutput()).containsEntry("newField", "New Value");
        assertThat(result.getOutput()).containsEntry("untouched", "stays");
    }

    @Test
    void execute_updateMode_addsNewFieldsAndOverwritesExisting() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("color", "blue");
        fields.put("size", "large");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("color", "red");
        input.put("shape", "circle");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("color", "blue");
        assertThat(result.getOutput()).containsEntry("size", "large");
        assertThat(result.getOutput()).containsEntry("shape", "circle");
    }

    // ========== Add Mode ==========

    @Test
    void execute_addMode_onlyAddsFieldsNotAlreadyInInput() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("existing", "should-not-overwrite");
        fields.put("newKey", "should-be-added");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "add");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "original");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("existing", "original");
        assertThat(result.getOutput()).containsEntry("newKey", "should-be-added");
    }

    @Test
    void execute_addMode_allNewFields_addsAll() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("a", 1);
        fields.put("b", 2);

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "add");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("c", 3);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("a", 1);
        assertThat(result.getOutput()).containsEntry("b", 2);
        assertThat(result.getOutput()).containsEntry("c", 3);
    }

    // ========== Replace Mode ==========

    @Test
    void execute_replaceMode_onlyIncludesSpecifiedFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("kept", "yes");
        fields.put("also_kept", "yes");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "replace");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("kept", "original");
        input.put("dropped", "should-not-appear");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("kept", "yes");
        assertThat(result.getOutput()).containsEntry("also_kept", "yes");
        assertThat(result.getOutput()).doesNotContainKey("dropped");
    }

    @Test
    void execute_replaceMode_ignoresOriginalInput() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("only", "this");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "replace");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("original", "data");
        input.put("more", "stuff");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).hasSize(1);
        assertThat(result.getOutput()).containsEntry("only", "this");
    }

    // ========== keepOriginal ==========

    @Test
    void execute_keepOriginalFalse_startsFromEmptyMap() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("newField", "value");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("keepOriginal", false);
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("existingField", "should-be-gone");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("newField", "value");
        assertThat(result.getOutput()).doesNotContainKey("existingField");
    }

    @Test
    void execute_keepOriginalTrue_preservesOriginalData() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("newField", "value");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("keepOriginal", true);
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("existingField", "preserved");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("newField", "value");
        assertThat(result.getOutput()).containsEntry("existingField", "preserved");
    }

    // ========== Fields as Map Format ==========

    @Test
    void execute_fieldsAsMap_parsesCorrectly() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("x", 10);
        fields.put("y", 20);

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        NodeExecutionContext context = buildContext(config, new HashMap<>());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("x", 10);
        assertThat(result.getOutput()).containsEntry("y", 20);
    }

    // ========== Fields as List Format ==========

    @Test
    void execute_fieldsAsListOfNameValue_parsesCorrectly() {
        List<Map<String, Object>> fieldsList = new ArrayList<>();
        Map<String, Object> field1 = new HashMap<>();
        field1.put("name", "greeting");
        field1.put("value", "hello");
        fieldsList.add(field1);

        Map<String, Object> field2 = new HashMap<>();
        field2.put("name", "count");
        field2.put("value", 42);
        fieldsList.add(field2);

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fieldsList);

        NodeExecutionContext context = buildContext(config, new HashMap<>());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("greeting", "hello");
        assertThat(result.getOutput()).containsEntry("count", 42);
    }

    @Test
    void execute_fieldsListWithNullName_skipsEntry() {
        List<Map<String, Object>> fieldsList = new ArrayList<>();
        Map<String, Object> field1 = new HashMap<>();
        field1.put("name", null);
        field1.put("value", "ignored");
        fieldsList.add(field1);

        Map<String, Object> field2 = new HashMap<>();
        field2.put("name", "valid");
        field2.put("value", "kept");
        fieldsList.add(field2);

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fieldsList);

        NodeExecutionContext context = buildContext(config, new HashMap<>());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).hasSize(1);
        assertThat(result.getOutput()).containsEntry("valid", "kept");
    }

    // ========== Expression Syntax ==========

    @Test
    void execute_expressionWithDollarInput_resolvesFromInputData() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("resolvedName", "{{$input.name}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Alice");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("resolvedName", "Alice");
    }

    @Test
    void execute_expressionWithInputPrefix_resolvesFromInputData() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("resolvedAge", "{{input.age}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("age", 30);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("resolvedAge", 30);
    }

    @Test
    void execute_nestedExpression_resolvesDeepPath() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("city", "{{$input.address.city}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> address = new HashMap<>();
        address.put("city", "Tokyo");
        address.put("zip", "100-0001");

        Map<String, Object> input = new HashMap<>();
        input.put("address", address);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("city", "Tokyo");
    }

    @Test
    void execute_directFieldExpression_resolvesWithoutPrefix() {
        // Expression without $input. or input. prefix does direct field reference
        Map<String, Object> fields = new HashMap<>();
        fields.put("copied", "{{status}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("status", "active");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("copied", "active");
    }

    @Test
    void execute_expressionWithMissingPath_returnsNull() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("missing", "{{$input.nonexistent}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        Map<String, Object> input = new HashMap<>();
        input.put("other", "value");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("missing")).isNull();
    }

    // ========== Null / Empty Handling ==========

    @Test
    void execute_nullInputData_expressionReturnsNull() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("resolved", "{{$input.something}}");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("keepOriginal", false);
        config.put("fields", fields);

        NodeExecutionContext context = buildContext(config, null);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("resolved")).isNull();
    }

    @Test
    void execute_emptyFieldsConfig_returnsInputAsIs() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");

        Map<String, Object> input = new HashMap<>();
        input.put("original", "data");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("original", "data");
    }

    @Test
    void execute_nonExpressionStringValue_passedThrough() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("literal", "just a string");

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        NodeExecutionContext context = buildContext(config, new HashMap<>());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("literal", "just a string");
    }

    @Test
    void execute_nonStringValue_passedThrough() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("number", 42);
        fields.put("bool", true);

        Map<String, Object> config = new HashMap<>();
        config.put("mode", "update");
        config.put("fields", fields);

        NodeExecutionContext context = buildContext(config, new HashMap<>());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("number", 42);
        assertThat(result.getOutput()).containsEntry("bool", true);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("mode");
        assertThat(properties).containsKey("keepOriginal");
        assertThat(properties).containsKey("fields");
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
                .nodeId("setFields-1")
                .nodeType("setFields")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
