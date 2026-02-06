package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConditionNodeHandlerTest {

    private ConditionNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConditionNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsCondition() {
        assertThat(handler.getType()).isEqualTo("condition");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsCondition() {
        assertThat(handler.getDisplayName()).isEqualTo("Condition");
    }

    // ========== Equals Operator ==========

    @Test
    void execute_equalsOperator_matchingValues_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "equals", "value", "active"),
                Map.of("status", "active")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_equalsOperator_nonMatchingValues_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "equals", "value", "active"),
                Map.of("status", "inactive")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    @Test
    void execute_equalsOperator_bothNull_returnsTrueBranch() {
        Map<String, Object> config = new HashMap<>();
        config.put("field", "missing");
        config.put("operator", "equals");
        config.put("value", null);

        NodeExecutionContext context = buildContext(config, Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_equalsOperator_typeCoercion_numbersAsStrings() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "equals", "value", "42"),
                Map.of("count", 42)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== NotEquals Operator ==========

    @Test
    void execute_notEqualsOperator_differentValues_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "notEquals", "value", "active"),
                Map.of("status", "inactive")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_notEqualsOperator_sameValues_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "notEquals", "value", "active"),
                Map.of("status", "active")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    @Test
    void execute_notEqualsOperator_nullFieldValue_returnsTrueBranch() {
        Map<String, Object> config = new HashMap<>();
        config.put("field", "missing");
        config.put("operator", "notEquals");
        config.put("value", "something");

        NodeExecutionContext context = buildContext(config, Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== Contains Operator ==========

    @Test
    void execute_containsOperator_substringPresent_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "message", "operator", "contains", "value", "world"),
                Map.of("message", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_containsOperator_substringAbsent_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "message", "operator", "contains", "value", "xyz"),
                Map.of("message", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== NotContains Operator ==========

    @Test
    void execute_notContainsOperator_substringAbsent_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "message", "operator", "notContains", "value", "xyz"),
                Map.of("message", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_notContainsOperator_substringPresent_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "message", "operator", "notContains", "value", "hello"),
                Map.of("message", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== StartsWith Operator ==========

    @Test
    void execute_startsWithOperator_matchingPrefix_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "startsWith", "value", "hello"),
                Map.of("name", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_startsWithOperator_nonMatchingPrefix_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "startsWith", "value", "world"),
                Map.of("name", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== EndsWith Operator ==========

    @Test
    void execute_endsWithOperator_matchingSuffix_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "endsWith", "value", "world"),
                Map.of("name", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_endsWithOperator_nonMatchingSuffix_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "endsWith", "value", "hello"),
                Map.of("name", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== GreaterThan Operator ==========

    @Test
    void execute_greaterThanOperator_greaterValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "greaterThan", "value", "5"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_greaterThanOperator_lesserValue_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "greaterThan", "value", "10"),
                Map.of("count", 5)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    @Test
    void execute_greaterThanOperator_equalValues_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "greaterThan", "value", "10"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== LessThan Operator ==========

    @Test
    void execute_lessThanOperator_lesserValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "lessThan", "value", "10"),
                Map.of("count", 5)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_lessThanOperator_greaterValue_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "lessThan", "value", "5"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== GreaterOrEqual Operator ==========

    @Test
    void execute_greaterOrEqualOperator_equalValues_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "greaterOrEqual", "value", "10"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_greaterOrEqualOperator_greaterValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "greaterOrEqual", "value", "5"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== LessOrEqual Operator ==========

    @Test
    void execute_lessOrEqualOperator_equalValues_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "lessOrEqual", "value", "10"),
                Map.of("count", 10)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_lessOrEqualOperator_lesserValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "count", "operator", "lessOrEqual", "value", "10"),
                Map.of("count", 5)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== IsEmpty Operator ==========

    @Test
    void execute_isEmptyOperator_emptyString_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isEmpty"),
                Map.of("name", "")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_isEmptyOperator_nonEmptyString_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isEmpty"),
                Map.of("name", "hello")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    @Test
    void execute_isEmptyOperator_nullField_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "missing", "operator", "isEmpty"),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== IsNotEmpty Operator ==========

    @Test
    void execute_isNotEmptyOperator_nonEmptyString_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isNotEmpty"),
                Map.of("name", "hello")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_isNotEmptyOperator_emptyString_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "isNotEmpty"),
                Map.of("name", "")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== IsTrue / IsFalse Operators ==========

    @Test
    void execute_isTrueOperator_trueValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "active", "operator", "isTrue"),
                Map.of("active", true)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_isTrueOperator_falseValue_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "active", "operator", "isTrue"),
                Map.of("active", false)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    @Test
    void execute_isFalseOperator_falseValue_returnsTrueBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "active", "operator", "isFalse"),
                Map.of("active", false)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== Nested Field Paths ==========

    @Test
    void execute_nestedFieldPath_resolvesCorrectly() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "user.address.city", "operator", "equals", "value", "Taipei"),
                Map.of("user", Map.of("address", Map.of("city", "Taipei")))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    @Test
    void execute_nestedFieldPath_missingIntermediateKey_returnsFalse() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "user.address.city", "operator", "equals", "value", "Taipei"),
                Map.of("user", Map.of("name", "John"))
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== Unknown Operator ==========

    @Test
    void execute_unknownOperator_returnsFalseBranch() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "unknownOp", "value", "active"),
                Map.of("status", "active")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("false");
    }

    // ========== Default Operator ==========

    @Test
    void execute_noOperatorSpecified_defaultsToEquals() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "value", "active"),
                Map.of("status", "active")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== Output Passthrough ==========

    @Test
    void execute_outputContainsInputData() {
        Map<String, Object> inputData = Map.of("status", "active", "name", "test");
        NodeExecutionContext context = buildContext(
                Map.of("field", "status", "operator", "equals", "value", "active"),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsAllEntriesOf(inputData);
    }

    // ========== Number String Fallback ==========

    @Test
    void execute_greaterThanOperator_nonNumericStrings_fallsBackToStringComparison() {
        NodeExecutionContext context = buildContext(
                Map.of("field", "name", "operator", "greaterThan", "value", "apple"),
                Map.of("name", "banana")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // "banana" > "apple" in string comparison
        assertThat(result.getBranchesToFollow()).containsExactly("true");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_hasRequiredFields() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("required");
        assertThat(schema).containsKey("properties");
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
                .nodeId("condition-1")
                .nodeType("condition")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
