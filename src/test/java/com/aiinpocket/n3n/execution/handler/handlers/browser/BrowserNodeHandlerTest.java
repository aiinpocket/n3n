package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class BrowserNodeHandlerTest {

    private BrowserNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BrowserNodeHandler(new ObjectMapper());
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsBrowser() {
            assertThat(handler.getType()).isEqualTo("browser");
        }

        @Test
        void getDisplayName_returnsBrowser() {
            assertThat(handler.getDisplayName()).isEqualTo("Browser");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getDescription_mentionsCDP() {
            assertThat(handler.getDescription()).containsIgnoringCase("Chrome DevTools Protocol");
        }

        @Test
        void getCategory_returnsAutomation() {
            assertThat(handler.getCategory()).isEqualTo("Automation");
        }

        @Test
        void getIcon_returnsChrome() {
            assertThat(handler.getIcon()).isEqualTo("chrome");
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
        }
    }

    // ==================== Config Schema ====================

    @Nested
    @DisplayName("Config Schema")
    class ConfigSchema {

        @Test
        void getConfigSchema_containsProperties() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsResourceProperty() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKey("resource");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_resourceHasEnumValues() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            Map<String, Object> resource = (Map<String, Object>) props.get("resource");
            List<String> enumValues = (List<String>) resource.get("enum");
            assertThat(enumValues).contains("session", "page", "element", "cookie", "network");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsOperationProperty() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKey("operation");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsUrlProperty() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKey("url");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsSelectorProperty() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKey("selector");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsCdpHostAndPort() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKeys("cdpHost", "cdpPort");
        }
    }

    // ==================== Interface Definition ====================

    @Nested
    @DisplayName("Interface Definition")
    class InterfaceDef {

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs").containsKey("outputs");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getInterfaceDefinition_hasOutputOfTypeObject() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) iface.get("outputs");
            assertThat(outputs).isNotEmpty();
            assertThat(outputs.get(0).get("type")).isEqualTo("object");
        }
    }

    // ==================== Execution - No CDP Available ====================

    @Nested
    @DisplayName("Execution - No CDP Available")
    class ExecutionNoCdp {

        @Test
        void execute_gotoWithNoCdp_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "page");
            config.put("operation", "goto");
            config.put("url", "https://example.com");
            config.put("cdpHost", "localhost");
            config.put("cdpPort", 19222); // non-existent port

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("browser");
        }

        @Test
        void execute_unknownResource_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "goto");
            config.put("cdpHost", "localhost");
            config.put("cdpPort", 19222);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown resource");
        }

        @Test
        void execute_sessionCreateWithNoCdp_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "session");
            config.put("operation", "create");
            config.put("cdpHost", "localhost");
            config.put("cdpPort", 19222);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_defaultResourceAndOperation() {
            // With no config for resource/operation, should use defaults page/goto
            Map<String, Object> config = new HashMap<>();
            config.put("cdpHost", "localhost");
            config.put("cdpPort", 19222);

            NodeExecutionResult result = handler.execute(buildContext(config));

            // Will fail because no CDP is running, but tests default handling
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("browser-1")
                .nodeType("browser")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
