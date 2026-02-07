package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNodeHandlerTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EmailNodeHandler(mailSender);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsEmail() {
            assertThat(handler.getType()).isEqualTo("email");
        }

        @Test
        void getDisplayName_returnsSendEmail() {
            assertThat(handler.getDisplayName()).isEqualTo("Send Email");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getCategory_returnsCommunication() {
            assertThat(handler.getCategory()).isEqualTo("Communication");
        }

        @Test
        void getIcon_returnsMail() {
            assertThat(handler.getIcon()).isEqualTo("mail");
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
        void getConfigSchema_containsRequiredFields() {
            Map<String, Object> schema = handler.getConfigSchema();
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertThat(required).contains("to", "subject");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getConfigSchema_containsAllFieldProperties() {
            Map<String, Object> schema = handler.getConfigSchema();
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKeys("to", "from", "cc", "bcc", "subject", "body", "html");
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

    // ==================== Validation ====================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void validateConfig_missingTo_returnsInvalid() {
            Map<String, Object> config = Map.of("subject", "Test");
            ValidationResult result = handler.validateConfig(config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_emptyTo_returnsInvalid() {
            Map<String, Object> config = Map.of("to", "", "subject", "Test");
            ValidationResult result = handler.validateConfig(config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_missingSubject_returnsInvalid() {
            Map<String, Object> config = Map.of("to", "test@example.com");
            ValidationResult result = handler.validateConfig(config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_emptySubject_returnsInvalid() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "test@example.com");
            config.put("subject", "  ");
            ValidationResult result = handler.validateConfig(config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void validateConfig_validConfig_returnsValid() {
            Map<String, Object> config = Map.of("to", "test@example.com", "subject", "Hello");
            ValidationResult result = handler.validateConfig(config);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ==================== Execution ====================

    @Nested
    @DisplayName("Execution")
    class Execution {

        @Test
        void execute_missingTo_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("subject", "Test Subject");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_missingSubject_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "test@example.com");
            config.put("body", "Test Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_simpleEmail_sendsAndReturnsSuccess() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "recipient@example.com");
            config.put("subject", "Test Subject");
            config.put("body", "Hello World");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("success", true);
            assertThat(result.getOutput()).containsEntry("to", "recipient@example.com");
            assertThat(result.getOutput()).containsEntry("subject", "Test Subject");
            assertThat(result.getOutput()).containsKey("sentAt");
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        void execute_simpleEmailWithFrom_setsFromAddress() {
            Map<String, Object> config = new HashMap<>();
            config.put("to", "recipient@example.com");
            config.put("from", "sender@example.com");
            config.put("subject", "Test");
            config.put("body", "Body");

            handler.execute(buildContext(config));

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getFrom()).isEqualTo("sender@example.com");
        }

        @Test
        void execute_htmlEmail_sendsMimeMessage() {
            MimeMessage mockMime = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mockMime);

            Map<String, Object> config = new HashMap<>();
            config.put("to", "recipient@example.com");
            config.put("subject", "HTML Email");
            config.put("body", "<h1>Hello</h1>");
            config.put("html", true);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isTrue();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void execute_withCc_sendsMimeMessage() {
            MimeMessage mockMime = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mockMime);

            Map<String, Object> config = new HashMap<>();
            config.put("to", "recipient@example.com");
            config.put("cc", "cc@example.com");
            config.put("subject", "CC Email");
            config.put("body", "Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isTrue();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void execute_mailSenderThrows_returnsFailure() {
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            Map<String, Object> config = new HashMap<>();
            config.put("to", "recipient@example.com");
            config.put("subject", "Test");
            config.put("body", "Body");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Failed to send email");
        }

        @Test
        void execute_emptyConfig_returnsFailure() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("email-1")
                .nodeType("email")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
