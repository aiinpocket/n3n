package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SendEmailNodeHandlerTest {

    @Mock
    private JavaMailSender mailSender;

    private SendEmailNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SendEmailNodeHandler(mailSender);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsSendEmail() {
            assertThat(handler.getType()).isEqualTo("sendEmail");
        }

        @Test
        void getDisplayName_containsSendEmail() {
            assertThat(handler.getDisplayName()).contains("Send Email");
        }

        @Test
        void getCategory_returnsCommunication() {
            assertThat(handler.getCategory()).isEqualTo("Communication");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsMail() {
            assertThat(handler.getIcon()).isEqualTo("mail");
        }

        @Test
        void supportsAsync_returnsFalse() {
            assertThat(handler.supportsAsync()).isFalse();
        }

        @Test
        void getConfigSchema_containsProperties() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }
    }

    // ==================== Validation ====================

    @Nested
    @DisplayName("Validation - Missing Fields")
    class ValidationMissingFields {

        @Test
        void execute_missingTo_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("subject", "Test Subject");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("recipient");
        }

        @Test
        void execute_emptyTo_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "");
            config.put("subject", "Test Subject");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("recipient");
        }

        @Test
        void execute_missingSubject_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "test@example.com");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("subject");
        }

        @Test
        void execute_emptySubject_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "test@example.com");
            config.put("subject", "");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("subject");
        }

        @Test
        void execute_emptyConfig_failsWithRecipientError() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("recipient");
        }
    }

    // ==================== Validation - Combined ====================

    @Nested
    @DisplayName("Validation - Combined")
    class ValidationCombined {

        @Test
        void execute_missingToAndSubject_failsWithRecipientError() {
            Map<String, Object> config = new HashMap<>();
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("recipient");
        }

        @Test
        void execute_hasToMissingSubject_failsWithSubjectError() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "test@example.com");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("subject");
        }

        @Test
        void execute_nullConfigValues_failsGracefully() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", null);
            config.put("subject", null);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("email-1")
                .nodeType("sendEmail")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
