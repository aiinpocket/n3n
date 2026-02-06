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
class FilterNodeHandlerTest {

    private FilterNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FilterNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsFilter() {
        assertThat(handler.getType()).isEqualTo("filter");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsFilter() {
        assertThat(handler.getDisplayName()).isEqualTo("Filter");
    }

    // ========== Exists Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_existsOperator_filtersItemsWithField() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice", "email", "alice@test.com"),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "email", "operator", "exists", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
        assertThat(result.getOutput()).containsEntry("count", 1);
        assertThat(result.getOutput()).containsEntry("rejectedCount", 1);
    }

    // ========== NotExists Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_notExistsOperator_filtersItemsWithoutField() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice", "email", "alice@test.com"),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "email", "operator", "notExists", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
        assertThat(result.getOutput()).containsEntry("count", 1);
    }

    // ========== Equals Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_equalsOperator_filtersMatchingItems() {
        List<Map<String, Object>> items = List.of(
                Map.of("status", "active"),
                Map.of("status", "inactive"),
                Map.of("status", "active")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "equals", "value", "active", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
        assertThat(result.getOutput()).containsEntry("count", 2);
    }

    // ========== NotEquals Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_notEqualsOperator_excludesMatchingItems() {
        List<Map<String, Object>> items = List.of(
                Map.of("status", "active"),
                Map.of("status", "inactive"),
                Map.of("status", "active")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "notEquals", "value", "active", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
    }

    // ========== Contains Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_containsOperator_filtersItemsContainingSubstring() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "hello world"),
                Map.of("name", "goodbye"),
                Map.of("name", "hello there")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "contains", "value", "hello", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== NotContains Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_notContainsOperator_excludesItemsContainingSubstring() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "hello world"),
                Map.of("name", "goodbye"),
                Map.of("name", "hello there")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "notContains", "value", "hello", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
    }

    // ========== GreaterThan Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_greaterThanOperator_filtersNumerically() {
        List<Map<String, Object>> items = List.of(
                Map.of("score", 90),
                Map.of("score", 50),
                Map.of("score", 75)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "score", "operator", "greaterThan", "value", "70", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== LessThan Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_lessThanOperator_filtersNumerically() {
        List<Map<String, Object>> items = List.of(
                Map.of("score", 90),
                Map.of("score", 50),
                Map.of("score", 75)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "score", "operator", "lessThan", "value", "70", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
    }

    // ========== IsEmpty Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_isEmptyOperator_filtersEmptyValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice"),
                Map.of("name", ""),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isEmpty", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
    }

    // ========== IsNotEmpty Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_isNotEmptyOperator_filtersNonEmptyValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice"),
                Map.of("name", ""),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isNotEmpty", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== IsTrue / IsFalse Operators ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_isTrueOperator_filtersTrueValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("active", true),
                Map.of("active", false),
                Map.of("active", true)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "active", "operator", "isTrue", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_isFalseOperator_filtersFalseValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("active", true),
                Map.of("active", false),
                Map.of("active", true)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "active", "operator", "isFalse", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(1);
    }

    // ========== Regex Operator ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_regexOperator_matchesPattern() {
        List<Map<String, Object>> items = List.of(
                Map.of("email", "user@example.com"),
                Map.of("email", "invalid"),
                Map.of("email", "admin@test.org")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "email", "operator", "regex", "value", ".*@.*\\..+", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== Empty Input ==========

    @Test
    void execute_emptyList_returnsEmptyResults() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "exists", "inputKey", "items"),
                Map.of("items", List.of())
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("count", 0);
        assertThat(result.getOutput()).containsEntry("rejectedCount", 0);
    }

    // ========== Rejected Output ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_rejectedOutput_containsNonMatchingItems() {
        List<Map<String, Object>> items = List.of(
                Map.of("status", "active"),
                Map.of("status", "inactive")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "equals", "value", "active", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> rejected = (List<Object>) result.getOutput().get("rejected");
        assertThat(rejected).hasSize(1);
        assertThat(result.getOutput()).containsEntry("rejectedCount", 1);
    }

    // ========== Nested Field ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_nestedField_resolvesCorrectly() {
        List<Map<String, Object>> items = List.of(
                Map.of("user", Map.of("role", "admin")),
                Map.of("user", Map.of("role", "guest")),
                Map.of("user", Map.of("role", "admin"))
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "user.role", "operator", "equals", "value", "admin", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== Default Operator ==========

    @Test
    void execute_noOperatorSpecified_defaultsToExists() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("count", 2);
    }

    // ========== GreaterOrEqual / LessOrEqual ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_greaterOrEqualOperator_includesEqualValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("score", 70),
                Map.of("score", 50),
                Map.of("score", 80)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "score", "operator", "greaterOrEqual", "value", "70", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_lessOrEqualOperator_includesEqualValues() {
        List<Map<String, Object>> items = List.of(
                Map.of("score", 70),
                Map.of("score", 50),
                Map.of("score", 80)
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "score", "operator", "lessOrEqual", "value", "70", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        List<Object> filtered = (List<Object>) result.getOutput().get("filtered");
        assertThat(filtered).hasSize(2);
    }

    // ========== Unknown Operator ==========

    @Test
    void execute_unknownOperator_passesAllItems() {
        List<Map<String, Object>> items = List.of(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );

        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "unknownOp", "inputKey", "items"),
                Map.of("items", items)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Unknown operator defaults to true, so all items pass
        assertThat(result.getOutput()).containsEntry("count", 2);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("inputKey");
        assertThat(properties).containsKey("field");
        assertThat(properties).containsKey("operator");
        assertThat(properties).containsKey("value");
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
                .nodeId("filter-1")
                .nodeType("filter")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
