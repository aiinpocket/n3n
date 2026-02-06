package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.JavaScriptEngine;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.ScriptExecutionException;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.ScriptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeNodeHandlerTest {

    @Mock
    private JavaScriptEngine javaScriptEngine;

    private CodeNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CodeNodeHandler(javaScriptEngine);
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsCode() {
        assertThat(handler.getType()).isEqualTo("code");
    }

    @Test
    void getCategory_returnsTransform() {
        assertThat(handler.getCategory()).isEqualTo("Transform");
    }

    @Test
    void getDisplayName_returnsCode() {
        assertThat(handler.getDisplayName()).isEqualTo("Code");
    }

    @Test
    void supportsAsync_returnsTrue() {
        assertThat(handler.supportsAsync()).isTrue();
    }

    // ========== Empty Code ==========

    @Test
    void execute_emptyCode_returnsFailure() {
        // validateConfig catches empty code before doExecute
        NodeExecutionContext context = buildContext(Map.of("code", ""));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Code is required");
    }

    @Test
    void execute_noCodeConfig_returnsFailure() {
        // validateConfig catches missing code before doExecute
        NodeExecutionContext context = buildContext(Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Code is required");
    }

    // ========== Unsupported Language ==========

    @Test
    void execute_unsupportedLanguage_returnsFailure() {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        // isAvailable() is not called because the language check happens first

        NodeExecutionContext context = buildContext(
                Map.of("code", "print('hello')", "language", "python")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unsupported language");
        assertThat(result.getErrorMessage()).contains("python");
    }

    // ========== JavaScript Engine Not Available ==========

    @Test
    void execute_engineNotAvailable_returnsFailure() {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(false);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return 1+1;", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not available");
    }

    // ========== Successful Execution (Map Result) ==========

    @Test
    void execute_validCodeReturningMap_returnsSuccess() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        Map<String, Object> resultData = Map.of("greeting", "hello");
        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .data(resultData)
                .logs(List.of())
                .executionTimeMs(50)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return {greeting: 'hello'};", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("greeting", "hello");
    }

    // ========== Successful Execution (Primitive Result) ==========

    @Test
    void execute_validCodeReturningPrimitive_wrapsInOutput() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .output(42)
                .logs(List.of())
                .executionTimeMs(10)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return 42;", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", 42);
    }

    // ========== Script Execution Failure ==========

    @Test
    void execute_scriptFails_returnsFailure() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(false)
                .errorType("RUNTIME_ERROR")
                .errorMessage("ReferenceError: x is not defined")
                .logs(List.of("some log"))
                .executionTimeMs(5)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return x;", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("ReferenceError");
    }

    @Test
    void execute_scriptFailure_includesErrorTypeInMetadata() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(false)
                .errorType("SYNTAX_ERROR")
                .errorMessage("Unexpected token")
                .logs(List.of())
                .executionTimeMs(2)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "{{invalid", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMetadata()).containsEntry("errorType", "SYNTAX_ERROR");
    }

    // ========== Script Execution Exception ==========

    @Test
    void execute_engineThrowsException_returnsFailure() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong()))
                .thenThrow(new ScriptExecutionException("EXECUTION_ERROR", "Engine crashed"));

        NodeExecutionContext context = buildContext(
                Map.of("code", "while(true){}", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Engine crashed");
    }

    // ========== Input Data Passed to Script ==========

    @Test
    void execute_inputDataPassedToScript_containsExecutionMetadata() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .data(Map.of("result", "ok"))
                .executionTimeMs(10)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        Map<String, Object> inputData = Map.of("key", "value");
        NodeExecutionContext context = buildContextWithInput(
                Map.of("code", "return {result: 'ok'};", "language", "javascript"),
                inputData
        );

        handler.execute(context);

        // Verify that the script input contains the original input data plus execution metadata
        verify(javaScriptEngine).execute(anyString(), argThat(scriptInput -> {
            return scriptInput.containsKey("key") &&
                    scriptInput.containsKey("$executionId") &&
                    scriptInput.containsKey("$nodeId");
        }), anyLong());
    }

    // ========== Logs in Metadata ==========

    @Test
    void execute_scriptWithLogs_includesLogsInMetadata() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .data(Map.of("x", 1))
                .logs(List.of("log line 1", "log line 2"))
                .executionTimeMs(15)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "console.log('test'); return {x:1};", "language", "javascript")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata()).containsKey("logs");
        assertThat(result.getMetadata()).containsKey("executionTimeMs");
    }

    // ========== Timeout Config ==========

    @Test
    void execute_customTimeout_passedToEngine() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .output("done")
                .executionTimeMs(100)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), eq(60000L))).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return 'done';", "language", "javascript", "timeout", 60000)
        );

        handler.execute(context);

        verify(javaScriptEngine).execute(anyString(), anyMap(), eq(60000L));
    }

    // ========== JS Language Alias ==========

    @Test
    void execute_jsLanguageAlias_acceptedAsJavaScript() throws Exception {
        when(javaScriptEngine.validateSyntax(anyString())).thenReturn(true);
        when(javaScriptEngine.isAvailable()).thenReturn(true);

        ScriptResult scriptResult = ScriptResult.builder()
                .success(true)
                .output("ok")
                .executionTimeMs(5)
                .build();

        when(javaScriptEngine.execute(anyString(), anyMap(), anyLong())).thenReturn(scriptResult);

        NodeExecutionContext context = buildContext(
                Map.of("code", "return 'ok';", "language", "js")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    // ========== Validation ==========

    @Test
    void validateConfig_emptyCode_returnsInvalid() {
        // Empty code is caught before validateSyntax is called
        var result = handler.validateConfig(Map.of("code", ""));

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_nullCode_returnsInvalid() {
        Map<String, Object> config = new HashMap<>();
        config.put("code", null);

        var result = handler.validateConfig(config);

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_invalidSyntax_returnsInvalid() {
        when(javaScriptEngine.validateSyntax("{{invalid}}")).thenReturn(false);

        var result = handler.validateConfig(Map.of("code", "{{invalid}}"));

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_validCode_returnsValid() {
        when(javaScriptEngine.validateSyntax("return 1;")).thenReturn(true);

        var result = handler.validateConfig(Map.of("code", "return 1;"));

        assertThat(result.isValid()).isTrue();
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

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("code-1")
                .nodeType("code")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }

    private NodeExecutionContext buildContextWithInput(Map<String, Object> config, Map<String, Object> inputData) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("code-1")
                .nodeType("code")
                .nodeConfig(nodeConfig)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
