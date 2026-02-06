package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendEmailNodeHandlerTest {

    @Mock
    private JavaMailSender mailSender;

    private SendEmailNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SendEmailNodeHandler(mailSender);
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsSendEmail() {
        assertThat(handler.getType()).isEqualTo("sendEmail");
    }

    @Test
    void getCategory_returnsCommunication() {
        assertThat(handler.getCategory()).isEqualTo("Communication");
    }

    @Test
    void getDisplayName_returnsSendEmail() {
        assertThat(handler.getDisplayName()).isEqualTo("Send Email");
    }

    // ========== Validation ==========

    @Test
    void execute_missingTo_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "subject", "Test Subject"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Recipient (to) is required");
    }

    @Test
    void execute_emptyTo_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "to", "",
                "subject", "Test Subject"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Recipient (to) is required");
    }

    @Test
    void execute_missingSubject_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "to", "test@example.com"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Subject is required");
    }

    // ========== Simple Email ==========

    @Test
    void execute_simpleTextEmail_sendsSuccessfully() {
        NodeExecutionContext context = buildContext(Map.of(
                "to", "recipient@example.com",
                "subject", "Test Subject",
                "body", "Hello World"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", "sent");
        assertThat(result.getOutput()).containsEntry("to", "recipient@example.com");
        assertThat(result.getOutput()).containsEntry("subject", "Test Subject");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("recipient@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("Test Subject");
        assertThat(sentMessage.getText()).isEqualTo("Hello World");
    }

    @Test
    void execute_multipleRecipients_splitsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "to", "a@example.com,b@example.com",
                "subject", "Multi"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("a@example.com", "b@example.com");
    }

    // ========== MIME Email (HTML / CC / BCC) ==========

    @Test
    void execute_htmlEmail_sendsMimeMessage() {
        MimeMessage mockMime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMime);

        NodeExecutionContext context = buildContext(Map.of(
                "to", "recipient@example.com",
                "subject", "HTML Email",
                "body", "<h1>Hello</h1>",
                "contentType", "text/html"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("contentType", "text/html");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void execute_withCc_sendsMimeMessage() {
        MimeMessage mockMime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMime);

        NodeExecutionContext context = buildContext(Map.of(
                "to", "to@example.com",
                "cc", "cc@example.com",
                "subject", "CC Email",
                "body", "Body"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("cc", "cc@example.com");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void execute_withBcc_sendsMimeMessage() {
        MimeMessage mockMime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMime);

        NodeExecutionContext context = buildContext(Map.of(
                "to", "to@example.com",
                "bcc", "bcc@example.com",
                "subject", "BCC Email",
                "body", "Body"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("bcc", "bcc@example.com");
    }

    // ========== Error Handling ==========

    @Test
    void execute_sendFails_returnsFailure() {
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        NodeExecutionContext context = buildContext(Map.of(
                "to", "recipient@example.com",
                "subject", "Test",
                "body", "Body"
        ));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("SMTP connection refused");
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
                .nodeId("send-email-1")
                .nodeType("sendEmail")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
