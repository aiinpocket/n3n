package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ClaudeNodeHandlerTest {

    private ClaudeNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClaudeNodeHandler(new ObjectMapper());
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsClaude() {
            assertThat(handler.getType()).isEqualTo("claude");
        }

        @Test
        void getDisplayName_containsClaude() {
            assertThat(handler.getDisplayName()).contains("Claude");
        }

        @Test
        void getCategory_returnsAI() {
            assertThat(handler.getCategory()).isEqualTo("AI");
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsClaude() {
            assertThat(handler.getIcon()).isEqualTo("claude");
        }

        @Test
        void getCredentialType_returnsAnthropic() {
            assertThat(handler.getCredentialType()).isEqualTo("anthropic");
        }
    }

    // ==================== Config Schema ====================

    @Nested
    @DisplayName("Config Schema")
    class ConfigSchemaTests {

        @Test
        void getConfigSchema_containsProperties() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getConfigSchema_containsResourceProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("resource");
        }

        @Test
        void getConfigSchema_containsOperationProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("operation");
        }

        @Test
        void getConfigSchema_containsCredentialIdProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("credentialId");
        }

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }
    }

    // ==================== Resources ====================

    @Nested
    @DisplayName("Resources")
    class ResourceTests {

        @Test
        void getResources_isNotEmpty() {
            assertThat(handler.getResources()).isNotEmpty();
        }

        @Test
        void getResources_containsMessagesResource() {
            Map<String, ResourceDef> resources = handler.getResources();
            assertThat(resources).containsKey("messages");
        }

        @Test
        void getOperations_isNotEmpty() {
            assertThat(handler.getOperations()).isNotEmpty();
        }

        @Test
        void getOperations_hasOperationsForEachResource() {
            for (String resourceKey : handler.getResources().keySet()) {
                assertThat(handler.getOperations()).containsKey(resourceKey);
                assertThat(handler.getOperations().get(resourceKey)).isNotEmpty();
            }
        }
    }

    // ==================== Validation ====================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void execute_missingResource_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "createMessage");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("resource");
        }

        @Test
        void execute_missingOperation_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "messages");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("operation");
        }

        @Test
        void execute_unknownResource_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "createMessage");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("unknown resource");
        }

        @Test
        void execute_unknownOperation_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "messages");
            config.put("operation", "nonexistentOp");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("unknown operation");
        }

        @Test
        void execute_validResourceAndOperation_noCredential_fails() {
            String resource = handler.getResources().keySet().iterator().next();
            String operation = handler.getOperations().get(resource).get(0).getName();

            Map<String, Object> config = new HashMap<>();
            config.put("resource", resource);
            config.put("operation", operation);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_emptyConfig_fails() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("claude-1")
                .nodeType("claude")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
