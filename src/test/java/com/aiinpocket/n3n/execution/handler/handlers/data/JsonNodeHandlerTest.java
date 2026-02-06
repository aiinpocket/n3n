package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JsonNodeHandlerTest {

    private JsonNodeHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new JsonNodeHandler(objectMapper);
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsJson() {
        assertThat(handler.getType()).isEqualTo("json");
    }

    @Test
    void getCategory_returnsData() {
        assertThat(handler.getCategory()).isEqualTo("Data");
    }

    @Test
    void getDisplayName_returnsJson() {
        assertThat(handler.getDisplayName()).isEqualTo("JSON");
    }

    @Test
    void getCredentialType_returnsNull() {
        assertThat(handler.getCredentialType()).isNull();
    }

    // ========== Resource/Operation Validation ==========

    @Test
    void execute_missingResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("operation", "parse"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Resource not selected");
    }

    @Test
    void execute_missingOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("resource", "transform"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Operation not selected");
    }

    @Test
    void execute_unknownResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "unknown",
                "operation", "parse"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown resource");
    }

    @Test
    void execute_unknownOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "unknownOp"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown operation");
    }

    // ========== Parse Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_parseValidJsonObject_returnsObjectData() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "parse",
                "jsonString", "{\"name\":\"Alice\",\"age\":30}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("type", "object");
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("name", "Alice");
        assertThat(data).containsEntry("age", 30);
    }

    @Test
    void execute_parseValidJsonArray_returnsArrayType() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "parse",
                "jsonString", "[1,2,3]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("type", "array");
        assertThat(result.getOutput().get("data")).isInstanceOf(List.class);
    }

    @Test
    void execute_parseInvalidJson_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "parse",
                "jsonString", "{invalid json"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("JSON error");
    }

    @Test
    void execute_parseMissingJsonString_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "parse"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
    }

    // ========== Stringify Operation ==========

    @Test
    void execute_stringifyObject_returnsJsonString() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "stringify",
                "object", "{\"name\":\"Alice\"}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("json");
        String json = (String) result.getOutput().get("json");
        assertThat(json).contains("name");
        assertThat(json).contains("Alice");
    }

    @Test
    void execute_stringifyWithPrettyPrint_returnsFormattedJson() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "stringify",
                "object", "{\"name\":\"Alice\"}",
                "pretty", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String json = (String) result.getOutput().get("json");
        // Pretty printed JSON should contain newlines
        assertThat(json).contains("\n");
    }

    @Test
    void execute_stringifyWithoutPrettyPrint_returnsCompactJson() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "stringify",
                "object", "{\"name\":\"Alice\"}",
                "pretty", false
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String json = (String) result.getOutput().get("json");
        assertThat(json).doesNotContain("\n");
    }

    // ========== Merge Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_mergeTwoObjects_returnsMergedData() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "merge",
                "object1", "{\"a\":1}",
                "object2", "{\"b\":2}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("a", 1);
        assertThat(data).containsEntry("b", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_mergeThreeObjects_returnsMergedData() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "merge",
                "object1", "{\"a\":1}",
                "object2", "{\"b\":2}",
                "object3", "{\"c\":3}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("a", 1);
        assertThat(data).containsEntry("b", 2);
        assertThat(data).containsEntry("c", 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_mergeOverlappingKeys_secondOverridesFirst() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "merge",
                "object1", "{\"a\":1,\"b\":2}",
                "object2", "{\"b\":3,\"c\":4}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("a", 1);
        assertThat(data).containsEntry("b", 3); // overridden by object2
        assertThat(data).containsEntry("c", 4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_mergeDeepNested_mergesRecursively() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "merge",
                "object1", "{\"user\":{\"name\":\"Alice\",\"age\":25}}",
                "object2", "{\"user\":{\"email\":\"alice@test.com\"}}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat(user).containsEntry("name", "Alice");
        assertThat(user).containsEntry("email", "alice@test.com");
    }

    // ========== SetValue Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_setValueAtSimplePath_updatesValue() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "setValue",
                "object", "{\"name\":\"Alice\"}",
                "path", "email",
                "value", "\"alice@test.com\""
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        assertThat(data).containsEntry("name", "Alice");
        assertThat(data).containsEntry("email", "alice@test.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_setValueAtNestedPath_createsIntermediateNodes() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "transform",
                "operation", "setValue",
                "object", "{\"name\":\"Alice\"}",
                "path", "address.city",
                "value", "\"Taipei\""
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
        Map<String, Object> address = (Map<String, Object>) data.get("address");
        assertThat(address).containsEntry("city", "Taipei");
    }

    // ========== GetValue Operation (JSONPath) ==========

    @Test
    void execute_getValueExistingPath_returnsValue() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "getValue",
                "object", "{\"users\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]}",
                "path", "$.users[0].name"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("value", "Alice");
        assertThat(result.getOutput()).containsEntry("found", true);
    }

    @Test
    void execute_getValueNonExistingPath_returnsDefault() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "getValue",
                "object", "{\"name\":\"Alice\"}",
                "path", "$.missing.field",
                "defaultValue", "\"N/A\""
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("value", "N/A");
        assertThat(result.getOutput()).containsEntry("found", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_getValueArrayPath_returnsArray() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "getValue",
                "object", "{\"users\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]}",
                "path", "$.users[*].name"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("found", true);
        List<String> values = (List<String>) result.getOutput().get("value");
        assertThat(values).containsExactly("Alice", "Bob");
    }

    // ========== Filter Operation (JSONPath) ==========

    @Test
    void execute_filterArray_returnsMatchingItems() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "filter",
                "array", "[{\"active\":true,\"name\":\"A\"},{\"active\":false,\"name\":\"B\"},{\"active\":true,\"name\":\"C\"}]",
                "filterPath", "$[?(@.active==true)]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("count", 2);
    }

    @Test
    void execute_filterArray_noMatches_returnsEmptyList() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "filter",
                "array", "[{\"score\":10},{\"score\":20}]",
                "filterPath", "$[?(@.score>100)]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("count", 0);
    }

    // ========== Keys Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_getKeys_returnsAllKeys() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "keys",
                "object", "{\"name\":\"Alice\",\"age\":30,\"email\":\"a@b.c\"}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<String> keys = (List<String>) result.getOutput().get("keys");
        assertThat(keys).containsExactlyInAnyOrder("name", "age", "email");
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    void execute_getKeys_nonObjectInput_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "keys",
                "object", "[1,2,3]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("must be an object");
    }

    // ========== Values Operation ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_getValues_returnsAllValues() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "values",
                "object", "{\"a\":1,\"b\":2,\"c\":3}"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> values = (List<Object>) result.getOutput().get("values");
        assertThat(values).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(result.getOutput()).containsEntry("count", 3);
    }

    @Test
    void execute_getValues_nonObjectInput_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "query",
                "operation", "values",
                "object", "[1,2,3]"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("must be an object");
    }

    // ========== Resources & Operations Metadata ==========

    @Test
    void getResources_containsTransformAndQuery() {
        var resources = handler.getResources();
        assertThat(resources).containsKey("transform");
        assertThat(resources).containsKey("query");
    }

    @Test
    void getOperations_transformHasExpectedOps() {
        var operations = handler.getOperations();
        var transformOps = operations.get("transform");
        assertThat(transformOps).isNotNull();

        var opNames = transformOps.stream().map(op -> op.getName()).toList();
        assertThat(opNames).containsExactly("parse", "stringify", "merge", "setValue");
    }

    @Test
    void getOperations_queryHasExpectedOps() {
        var operations = handler.getOperations();
        var queryOps = operations.get("query");
        assertThat(queryOps).isNotNull();

        var opNames = queryOps.stream().map(op -> op.getName()).toList();
        assertThat(opNames).containsExactly("getValue", "filter", "keys", "values");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsMultiOperationMarker() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsEntry("x-multi-operation", true);
        assertThat(schema).containsKey("properties");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("json-1")
                .nodeType("json")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
