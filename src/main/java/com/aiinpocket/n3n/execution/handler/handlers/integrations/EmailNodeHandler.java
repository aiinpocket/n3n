package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for sending emails via SMTP.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmailNodeHandler extends AbstractNodeHandler {

    private final JavaMailSender mailSender;

    @Override
    public String getType() {
        return "email";
    }

    @Override
    public String getDisplayName() {
        return "Send Email";
    }

    @Override
    public String getDescription() {
        return "Send emails via SMTP server.";
    }

    @Override
    public String getCategory() {
        return "Communication";
    }

    @Override
    public String getIcon() {
        return "mail";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String to = getStringConfig(context, "to", "");
        String subject = getStringConfig(context, "subject", "");
        String body = getStringConfig(context, "body", "");
        String from = getStringConfig(context, "from", "");
        String cc = getStringConfig(context, "cc", "");
        String bcc = getStringConfig(context, "bcc", "");
        boolean isHtml = getBooleanConfig(context, "html", false);

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("Recipient email (to) is required");
        }

        if (subject.isEmpty()) {
            return NodeExecutionResult.failure("Subject is required");
        }

        try {
            if (isHtml || !cc.isEmpty() || !bcc.isEmpty()) {
                sendHtmlEmail(from, to, cc, bcc, subject, body, isHtml);
            } else {
                sendSimpleEmail(from, to, subject, body);
            }

            log.info("Email sent successfully to: {}", to);

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("to", to);
            output.put("subject", subject);
            output.put("sentAt", System.currentTimeMillis());

            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            return NodeExecutionResult.failure("Failed to send email: " + e.getMessage());
        }
    }

    private void sendSimpleEmail(String from, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) {
            message.setFrom(from);
        }
        message.setTo(to.split(","));
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void sendHtmlEmail(String from, String to, String cc, String bcc,
                                String subject, String body, boolean isHtml) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        if (!from.isEmpty()) {
            helper.setFrom(from);
        }
        helper.setTo(to.split(","));
        if (!cc.isEmpty()) {
            helper.setCc(cc.split(","));
        }
        if (!bcc.isEmpty()) {
            helper.setBcc(bcc.split(","));
        }
        helper.setSubject(subject);
        helper.setText(body, isHtml);

        mailSender.send(message);
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object to = config.get("to");
        if (to == null || to.toString().trim().isEmpty()) {
            return ValidationResult.invalid("to", "Recipient email is required");
        }

        Object subject = config.get("subject");
        if (subject == null || subject.toString().trim().isEmpty()) {
            return ValidationResult.invalid("subject", "Subject is required");
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("to", "subject"),
            "properties", Map.of(
                "to", Map.of(
                    "type", "string",
                    "title", "To",
                    "description", "Recipient email address(es), comma-separated",
                    "format", "email"
                ),
                "from", Map.of(
                    "type", "string",
                    "title", "From",
                    "description", "Sender email address (optional, uses default)",
                    "format", "email"
                ),
                "cc", Map.of(
                    "type", "string",
                    "title", "CC",
                    "description", "CC recipients, comma-separated"
                ),
                "bcc", Map.of(
                    "type", "string",
                    "title", "BCC",
                    "description", "BCC recipients, comma-separated"
                ),
                "subject", Map.of(
                    "type", "string",
                    "title", "Subject",
                    "description", "Email subject line"
                ),
                "body", Map.of(
                    "type", "string",
                    "title", "Body",
                    "description", "Email body content",
                    "format", "textarea"
                ),
                "html", Map.of(
                    "type", "boolean",
                    "title", "HTML Format",
                    "default", false,
                    "description", "Send as HTML email"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
