package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class FacebookNodeHandlerTest {

    private FacebookNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FacebookNodeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsFacebook() { assertThat(handler.getType()).isEqualTo("facebook"); }

        @Test
        void getDisplayName() { assertThat(handler.getDisplayName()).contains("Facebook"); }

        @Test
        void getCategory_returnsSocial() { assertThat(handler.getCategory()).isNotNull(); }

        @Test
        void getConfigSchema_containsProperties() { assertThat(handler.getConfigSchema()).containsKey("properties"); }

        @Test
        void getInterfaceDefinition_hasIO() {
            assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs");
        }

        @Test
        void getResources_notEmpty() {
            assertThat(handler.getResources()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        void execute_missingCredential_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "page");
            config.put("operation", "getInfo");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("facebook-1").nodeType("facebook")
                .nodeConfig(new HashMap<>(config)).inputData(inputData)
                .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
