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
class ErrorTriggerHandlerTest {

    private ErrorTriggerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ErrorTriggerHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsErrorTrigger() {
        assertThat(handler.getType()).isEqualTo("errorTrigger");
    }

    @Test
    void getCategory_returnsTriggers() {
        assertThat(handler.getCategory()).isEqualTo("Triggers");
    }

    @Test
    void getDisplayName_returnsErrorTrigger() {
        assertThat(handler.getDisplayName()).isEqualTo("Error Trigger");
    }

    @Test
    void isTrigger_returnsTrue() {
        assertThat(handler.isTrigger()).isTrue();
    }

    // ========== Test Mode (No Trigger Input) ==========

    @Test
    void execute_noTriggerInput_returnsSampleErrorStructure() {
        NodeExecutionContext context = buildContext(Map.of(), null);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("error");
        assertThat(result.getOutput()).containsKey("source");
        assertThat(result.getOutput()).containsKey("triggeredAt");
        assertThat(result.getOutput()).containsEntry("triggerType", "error");
        assertThat(result.getOutput()).containsEntry("retryEnabled", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.getOutput().get("error");
        assertThat(error).containsEntry("message", "Sample error for testing");
        assertThat(error).containsEntry("type", "runtime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_testMode_sourceFieldsAreEmpty() {
        NodeExecutionContext context = buildContext(Map.of(), null);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> source = (Map<String, Object>) result.getOutput().get("source");
        assertThat(source).containsEntry("flowId", "");
        assertThat(source).containsEntry("flowName", "");
        assertThat(source).containsEntry("nodeId", "");
        assertThat(source).containsEntry("executionId", "");
    }

    // ========== Basic Error Trigger With Trigger Input ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_withTriggerInput_extractsErrorDetails() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Connection refused");
        triggerInput.put("errorStack", "java.net.ConnectException: ...");
        triggerInput.put("sourceNode", "http-1");
        triggerInput.put("sourceFlowName", "Data Sync");
        triggerInput.put("sourceFlowId", "flow-123");
        triggerInput.put("sourceExecutionId", "exec-456");
        triggerInput.put("errorType", "connection");

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();

        Map<String, Object> error = (Map<String, Object>) result.getOutput().get("error");
        assertThat(error).containsEntry("message", "Connection refused");
        assertThat(error).containsEntry("stack", "java.net.ConnectException: ...");
        assertThat(error).containsEntry("type", "connection");

        Map<String, Object> source = (Map<String, Object>) result.getOutput().get("source");
        assertThat(source).containsEntry("flowId", "flow-123");
        assertThat(source).containsEntry("flowName", "Data Sync");
        assertThat(source).containsEntry("nodeId", "http-1");
        assertThat(source).containsEntry("executionId", "exec-456");
    }

    // ========== Source Flow ID Filtering ==========

    @Test
    void execute_sourceFlowIdFilter_matchingFlow_succeeds() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Timeout");
        triggerInput.put("sourceFlowId", "flow-abc");
        triggerInput.put("errorType", "timeout");

        Map<String, Object> config = new HashMap<>();
        config.put("sourceFlowId", "flow-abc");

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_sourceFlowIdFilter_nonMatchingFlow_fails() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Timeout");
        triggerInput.put("sourceFlowId", "flow-xyz");
        triggerInput.put("errorType", "timeout");

        Map<String, Object> config = new HashMap<>();
        config.put("sourceFlowId", "flow-abc");

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("does not match filter");
    }

    // ========== Error Type Filtering ==========

    @Test
    void execute_errorTypeFilter_runtimeType_passes() {
        assertErrorTypeFilterPasses("runtime", List.of("runtime", "timeout"));
    }

    @Test
    void execute_errorTypeFilter_timeoutType_passes() {
        assertErrorTypeFilterPasses("timeout", List.of("timeout"));
    }

    @Test
    void execute_errorTypeFilter_configurationTypeNotInFilter_fails() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Bad config");
        triggerInput.put("errorType", "configuration");

        Map<String, Object> config = new HashMap<>();
        config.put("errorTypes", List.of("runtime", "timeout"));

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("configuration");
        assertThat(result.getErrorMessage()).contains("not in configured filter");
    }

    @Test
    void execute_errorTypeFilter_connectionType_passes() {
        assertErrorTypeFilterPasses("connection", List.of("connection", "authentication"));
    }

    @Test
    void execute_errorTypeFilter_authenticationType_passes() {
        assertErrorTypeFilterPasses("authentication", List.of("authentication"));
    }

    @Test
    void execute_errorTypeFilter_validationType_passes() {
        assertErrorTypeFilterPasses("validation", List.of("validation", "runtime"));
    }

    @Test
    void execute_emptyErrorTypeList_allowsAllTypes() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Some error");
        triggerInput.put("errorType", "configuration");

        Map<String, Object> config = new HashMap<>();
        config.put("errorTypes", List.of());

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_noErrorTypesConfig_allowsAllTypes() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Some error");
        triggerInput.put("errorType", "validation");

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    // ========== Retry Enabled ==========

    @Test
    void execute_retryEnabled_outputContainsRetryTrue() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Temp failure");

        Map<String, Object> config = new HashMap<>();
        config.put("retryEnabled", true);

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("retryEnabled", true);
    }

    @Test
    void execute_retryDisabled_outputContainsRetryFalse() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Fatal error");

        Map<String, Object> config = new HashMap<>();
        config.put("retryEnabled", false);

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("retryEnabled", false);
    }

    // ========== Error With Stack Trace ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_errorWithStackTrace_stackIncludedInOutput() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "NullPointerException");
        triggerInput.put("errorStack", "at com.example.Service.process(Service.java:42)\nat com.example.Controller.handle(Controller.java:10)");

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> error = (Map<String, Object>) result.getOutput().get("error");
        assertThat(error.get("stack").toString()).contains("Service.java:42");
    }

    // ========== Node Output Passthrough ==========

    @Test
    void execute_triggerInputWithNodeOutput_passesLastNodeOutput() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Processing failed");
        triggerInput.put("nodeOutput", Map.of("processedCount", 42, "failedItem", "item-99"));

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("lastNodeOutput");
        @SuppressWarnings("unchecked")
        Map<String, Object> lastOutput = (Map<String, Object>) result.getOutput().get("lastNodeOutput");
        assertThat(lastOutput).containsEntry("processedCount", 42);
    }

    @Test
    void execute_triggerInputWithoutNodeOutput_noLastNodeOutput() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Simple error");

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).doesNotContainKey("lastNodeOutput");
    }

    // ========== Output Structure ==========

    @Test
    void execute_outputContainsTriggeredAtTimestamp() {
        NodeExecutionContext context = buildContext(Map.of(), null);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("triggeredAt")).isNotNull();
        assertThat(result.getOutput().get("triggeredAt").toString()).isNotEmpty();
    }

    @Test
    void execute_outputContainsTriggerTypeError() {
        NodeExecutionContext context = buildContext(Map.of(), null);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.getOutput()).containsEntry("triggerType", "error");
    }

    // ========== Default Error Type ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_triggerInputMissingErrorType_defaultsToRuntime() {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Something failed");

        NodeExecutionContext context = buildContext(Map.of(), triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> error = (Map<String, Object>) result.getOutput().get("error");
        assertThat(error).containsEntry("type", "runtime");
    }

    // ========== Config Schema and Interface ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("sourceFlowId");
        assertThat(properties).containsKey("errorTypes");
        assertThat(properties).containsKey("retryEnabled");
    }

    @Test
    void getInterfaceDefinition_hasNoInputsAndHasOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
        @SuppressWarnings("unchecked")
        List<Object> inputs = (List<Object>) iface.get("inputs");
        assertThat(inputs).isEmpty();
    }

    // ========== Helpers ==========

    private void assertErrorTypeFilterPasses(String errorType, List<String> filterTypes) {
        Map<String, Object> triggerInput = new HashMap<>();
        triggerInput.put("errorMessage", "Test error");
        triggerInput.put("errorType", errorType);

        Map<String, Object> config = new HashMap<>();
        config.put("errorTypes", filterTypes);

        NodeExecutionContext context = buildContext(config, triggerInput);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> triggerInput) {
        Map<String, Object> globalContext = null;
        if (triggerInput != null) {
            globalContext = new HashMap<>();
            globalContext.put("triggerInput", triggerInput);
        }

        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("error-trigger-1")
                .nodeType("errorTrigger")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .globalContext(globalContext)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
