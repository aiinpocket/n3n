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
class RenameKeysNodeHandlerTest {

    private RenameKeysNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RenameKeysNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRenameKeys() {
        assertThat(handler.getType()).isEqualTo("renameKeys");
    }

    @Test
    void getCategory_returnsDataTransform() {
        assertThat(handler.getCategory()).isEqualTo("Data Transform");
    }

    @Test
    void getDisplayName_returnsRenameKeys() {
        assertThat(handler.getDisplayName()).isEqualTo("Rename Keys");
    }

    // ========== Rename Single Key ==========

    @Test
    void execute_renameSingleKey_renamesCorrectly() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("old_name", "new_name");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);

        Map<String, Object> input = new HashMap<>();
        input.put("old_name", "Alice");
        input.put("age", 30);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("new_name", "Alice");
        assertThat(result.getOutput()).containsEntry("age", 30);
        assertThat(result.getOutput()).doesNotContainKey("old_name");
    }

    // ========== Rename Multiple Keys ==========

    @Test
    void execute_renameMultipleKeys_renamesAll() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("first_name", "firstName");
        mappings.put("last_name", "lastName");
        mappings.put("e_mail", "email");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);

        Map<String, Object> input = new HashMap<>();
        input.put("first_name", "John");
        input.put("last_name", "Doe");
        input.put("e_mail", "john@example.com");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("firstName", "John");
        assertThat(result.getOutput()).containsEntry("lastName", "Doe");
        assertThat(result.getOutput()).containsEntry("email", "john@example.com");
        assertThat(result.getOutput()).doesNotContainKey("first_name");
        assertThat(result.getOutput()).doesNotContainKey("last_name");
        assertThat(result.getOutput()).doesNotContainKey("e_mail");
    }

    // ========== keepUnmapped=false ==========

    @Test
    void execute_keepUnmappedFalse_dropsUnmappedKeys() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("keep_this", "kept");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);
        config.put("keepUnmapped", false);

        Map<String, Object> input = new HashMap<>();
        input.put("keep_this", "value1");
        input.put("drop_this", "value2");
        input.put("also_drop", "value3");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).hasSize(1);
        assertThat(result.getOutput()).containsEntry("kept", "value1");
        assertThat(result.getOutput()).doesNotContainKey("drop_this");
        assertThat(result.getOutput()).doesNotContainKey("also_drop");
    }

    @Test
    void execute_keepUnmappedTrue_preservesAllKeys() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("a", "alpha");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);
        config.put("keepUnmapped", true);

        Map<String, Object> input = new HashMap<>();
        input.put("a", 1);
        input.put("b", 2);
        input.put("c", 3);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("alpha", 1);
        assertThat(result.getOutput()).containsEntry("b", 2);
        assertThat(result.getOutput()).containsEntry("c", 3);
        assertThat(result.getOutput()).doesNotContainKey("a");
    }

    // ========== Mappings as Map Format ==========

    @Test
    void execute_mappingsAsMap_parsesCorrectly() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("src", "source");
        mappings.put("dst", "destination");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);

        Map<String, Object> input = new HashMap<>();
        input.put("src", "/path/a");
        input.put("dst", "/path/b");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("source", "/path/a");
        assertThat(result.getOutput()).containsEntry("destination", "/path/b");
    }

    // ========== Mappings as List Format ==========

    @Test
    void execute_mappingsAsListOfFromTo_parsesCorrectly() {
        List<Map<String, Object>> mappingsList = new ArrayList<>();
        Map<String, Object> mapping1 = new HashMap<>();
        mapping1.put("from", "old_key");
        mapping1.put("to", "newKey");
        mappingsList.add(mapping1);

        Map<String, Object> mapping2 = new HashMap<>();
        mapping2.put("from", "another_old");
        mapping2.put("to", "anotherNew");
        mappingsList.add(mapping2);

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappingsList);

        Map<String, Object> input = new HashMap<>();
        input.put("old_key", "value1");
        input.put("another_old", "value2");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("newKey", "value1");
        assertThat(result.getOutput()).containsEntry("anotherNew", "value2");
    }

    @Test
    void execute_mappingsListWithNullFromOrTo_skipsEntry() {
        List<Map<String, Object>> mappingsList = new ArrayList<>();
        Map<String, Object> mapping1 = new HashMap<>();
        mapping1.put("from", null);
        mapping1.put("to", "something");
        mappingsList.add(mapping1);

        Map<String, Object> mapping2 = new HashMap<>();
        mapping2.put("from", "key");
        mapping2.put("to", null);
        mappingsList.add(mapping2);

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappingsList);

        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Both mappings skipped (null from and null to), key kept because keepUnmapped defaults to true
        assertThat(result.getOutput()).containsEntry("key", "value");
    }

    // ========== Deep Rename in Nested Objects ==========

    @Test
    void execute_deepRenameInNestedObjects_renamesRecursively() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("name", "fullName");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);
        config.put("deep", true);

        Map<String, Object> nested = new HashMap<>();
        nested.put("name", "Inner Name");
        nested.put("extra", "data");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Outer Name");
        input.put("child", nested);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("fullName", "Outer Name");
        assertThat(result.getOutput()).doesNotContainKey("name");

        @SuppressWarnings("unchecked")
        Map<String, Object> childOutput = (Map<String, Object>) result.getOutput().get("child");
        assertThat(childOutput).containsEntry("fullName", "Inner Name");
        assertThat(childOutput).doesNotContainKey("name");
        assertThat(childOutput).containsEntry("extra", "data");
    }

    @Test
    void execute_shallowMode_doesNotRenameNestedKeys() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("name", "fullName");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);
        config.put("deep", false);

        Map<String, Object> nested = new HashMap<>();
        nested.put("name", "Inner Name");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Outer Name");
        input.put("child", nested);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("fullName", "Outer Name");

        @SuppressWarnings("unchecked")
        Map<String, Object> childOutput = (Map<String, Object>) result.getOutput().get("child");
        // shallow mode: nested "name" key should NOT be renamed
        assertThat(childOutput).containsEntry("name", "Inner Name");
        assertThat(childOutput).doesNotContainKey("fullName");
    }

    // ========== Deep Rename in Lists of Objects ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_deepRenameInListOfObjects_renamesInEachListItem() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("title", "heading");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);
        config.put("deep", true);

        List<Object> listItems = new ArrayList<>();
        listItems.add(new HashMap<>(Map.of("title", "First", "body", "content1")));
        listItems.add(new HashMap<>(Map.of("title", "Second", "body", "content2")));

        Map<String, Object> input = new HashMap<>();
        input.put("title", "Main Title");
        input.put("sections", listItems);

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("heading", "Main Title");

        List<Object> sections = (List<Object>) result.getOutput().get("sections");
        assertThat(sections).hasSize(2);

        Map<String, Object> firstSection = (Map<String, Object>) sections.get(0);
        assertThat(firstSection).containsEntry("heading", "First");
        assertThat(firstSection).doesNotContainKey("title");

        Map<String, Object> secondSection = (Map<String, Object>) sections.get(1);
        assertThat(secondSection).containsEntry("heading", "Second");
    }

    // ========== Null Input ==========

    @Test
    void execute_nullInput_returnsEmptyMap() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("a", "b");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);

        NodeExecutionContext context = buildContext(config, null);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEmpty();
    }

    // ========== No Mappings ==========

    @Test
    void execute_noMappings_returnsInputUnchanged() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> input = new HashMap<>();
        input.put("key1", "value1");
        input.put("key2", "value2");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("key1", "value1");
        assertThat(result.getOutput()).containsEntry("key2", "value2");
    }

    // ========== Mapping Key Not Present in Input ==========

    @Test
    void execute_mappingKeyNotInInput_noEffect() {
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("nonexistent", "renamed");

        Map<String, Object> config = new HashMap<>();
        config.put("mappings", mappings);

        Map<String, Object> input = new HashMap<>();
        input.put("actual_key", "value");

        NodeExecutionContext context = buildContext(config, input);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("actual_key", "value");
        assertThat(result.getOutput()).doesNotContainKey("nonexistent");
        assertThat(result.getOutput()).doesNotContainKey("renamed");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("mappings");
        assertThat(properties).containsKey("keepUnmapped");
        assertThat(properties).containsKey("deep");
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
                .nodeId("renameKeys-1")
                .nodeType("renameKeys")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
