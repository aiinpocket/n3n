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
class SwitchNodeHandlerTest {

    private SwitchNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SwitchNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSwitch() {
        assertThat(handler.getType()).isEqualTo("switch");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsSwitch() {
        assertThat(handler.getDisplayName()).isEqualTo("Switch");
    }

    // ========== First Match Mode ==========

    @Test
    void execute_firstMatchMode_stopsAtFirstMatch() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "foo"),
                Map.of("branch", "branchB", "field", "type", "operator", "equals", "value", "foo")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("branchA");
    }

    @Test
    void execute_firstMatchMode_matchesSecondCase() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "bar"),
                Map.of("branch", "branchB", "field", "type", "operator", "equals", "value", "foo")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("branchB");
    }

    // ========== All Match Mode ==========

    @Test
    void execute_allMatchMode_returnsAllMatchingBranches() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "foo"),
                Map.of("branch", "branchB", "field", "type", "operator", "equals", "value", "foo"),
                Map.of("branch", "branchC", "field", "type", "operator", "equals", "value", "bar")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "all", "cases", cases),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("branchA", "branchB");
    }

    @Test
    void execute_allMatchMode_allCasesMatch_returnsAll() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "name", "operator", "contains", "value", "test"),
                Map.of("branch", "branchB", "field", "name", "operator", "startsWith", "value", "test")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "all", "cases", cases),
                Map.of("name", "test-value")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("branchA", "branchB");
    }

    // ========== Fallback / Default ==========

    @Test
    void execute_noCaseMatches_fallbackEnabled_usesDefaultBranch() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "bar")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases, "enableFallback", true),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("default");
    }

    @Test
    void execute_noCaseMatches_customFallbackBranch() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "bar")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases, "enableFallback", true, "fallbackBranch", "other"),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("other");
    }

    @Test
    void execute_noCaseMatches_fallbackDisabled_returnsFailure() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "bar")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases, "enableFallback", false),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("No switch cases matched");
    }

    // ========== Various Operators ==========

    @Test
    void execute_containsOperator_matchesCorrectly() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "matched", "field", "name", "operator", "contains", "value", "world")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("name", "hello world")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("matched");
    }

    @Test
    void execute_greaterThanOperator_numericComparison() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "high", "field", "score", "operator", "greaterThan", "value", "80"),
                Map.of("branch", "low", "field", "score", "operator", "lessOrEqual", "value", "80")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("score", 95)
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("high");
    }

    @Test
    void execute_matchesOperator_regexPattern() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "email", "field", "input", "operator", "matches", "value", ".*@.*\\.com")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("input", "user@example.com")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("email");
    }

    @Test
    void execute_inOperator_commaSeparatedList() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "allowed", "field", "role", "operator", "in", "value", "admin,editor,viewer")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("role", "editor")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("allowed");
    }

    @Test
    void execute_notInOperator_valueNotInList() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "blocked", "field", "role", "operator", "notIn", "value", "admin,editor")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("role", "guest")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("blocked");
    }

    // ========== Null Handling ==========

    @Test
    void execute_isNullOperator_nullFieldValue_matchesTrue() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "nullBranch", "field", "missing", "operator", "isNull")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("nullBranch");
    }

    @Test
    void execute_isNotNullOperator_presentFieldValue_matchesTrue() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "exists", "field", "name", "operator", "isNotNull")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("name", "test")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("exists");
    }

    // ========== Empty Cases ==========

    @Test
    void execute_emptyCasesList_fallbackEnabled_usesDefault() {
        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", List.of(), "enableFallback", true),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBranchesToFollow()).containsExactly("default");
    }

    // ========== Output Contains Switch Info ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_outputContainsSwitchInfoMetadata() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "foo")
        );

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("_switchInfo");
        Map<String, Object> switchInfo = (Map<String, Object>) result.getOutput().get("_switchInfo");
        assertThat(switchInfo).containsEntry("mode", "first");
        assertThat(switchInfo).containsEntry("totalCases", 1);
    }

    // ========== Input Data Passthrough ==========

    @Test
    void execute_outputContainsInputData() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "foo")
        );

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("type", "foo");
        inputData.put("extra", "data");

        NodeExecutionContext context = buildContext(
                Map.of("mode", "first", "cases", cases),
                inputData
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("type", "foo");
        assertThat(result.getOutput()).containsEntry("extra", "data");
    }

    // ========== Default Mode ==========

    @Test
    void execute_noModeSpecified_defaultsToFirst() {
        List<Map<String, Object>> cases = List.of(
                Map.of("branch", "branchA", "field", "type", "operator", "equals", "value", "foo"),
                Map.of("branch", "branchB", "field", "type", "operator", "equals", "value", "foo")
        );

        NodeExecutionContext context = buildContext(
                Map.of("cases", cases),
                Map.of("type", "foo")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Default mode is "first", so only first matching branch
        assertThat(result.getBranchesToFollow()).containsExactly("branchA");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("mode");
        assertThat(properties).containsKey("cases");
        assertThat(properties).containsKey("enableFallback");
        assertThat(properties).containsKey("fallbackBranch");
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
                .nodeId("switch-1")
                .nodeType("switch")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
