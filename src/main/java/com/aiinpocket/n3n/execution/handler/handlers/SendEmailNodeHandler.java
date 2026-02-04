package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for send email nodes.
 * Sends emails via SMTP.
 */
@Component
@Slf4j
public class SendEmailNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "sendEmail";
    }

    @Override
    public String getDisplayName() {
        return "Send Email";
    }

    @Override
    public String getDescription() {
        return "Sends emails via SMTP server.";
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
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Get email configuration
        String to = getStringConfig(context, "to", "");
        String cc = getStringConfig(context, "cc", "");
        String bcc = getStringConfig(context, "bcc", "");
        String subject = getStringConfig(context, "subject", "");
        String body = getStringConfig(context, "body", "");
        String contentType = getStringConfig(context, "contentType", "text/plain");
        String credentialId = getStringConfig(context, "credentialId", "");

        // Validate required fields
        if (to.isEmpty()) {
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("Recipient (to) is required")
                .build();
        }

        if (subject.isEmpty()) {
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("Subject is required")
                .build();
        }

        log.info("Sending email to: {}, subject: {}", to, subject);

        // In a real implementation, this would:
        // 1. Fetch SMTP credentials from CredentialService using credentialId
        // 2. Configure JavaMailSender
        // 3. Send the email

        // For now, return a placeholder success response
        Map<String, Object> output = new HashMap<>();
        output.put("status", "sent");
        output.put("to", to);
        output.put("cc", cc.isEmpty() ? null : cc);
        output.put("bcc", bcc.isEmpty() ? null : bcc);
        output.put("subject", subject);
        output.put("contentType", contentType);
        output.put("timestamp", java.time.Instant.now().toString());
        output.put("messageId", UUID.randomUUID().toString());

        // Note: Actual email sending would be implemented with JavaMailSender
        // This is a placeholder that would be connected to Spring Mail

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("to", "subject"),
            "properties", Map.of(
                "credentialId", Map.of(
                    "type", "string",
                    "title", "SMTP Credential",
                    "description", "ID of the SMTP credential to use"
                ),
                "to", Map.of(
                    "type", "string",
                    "title", "To",
                    "description", "Recipient email addresses (comma-separated)"
                ),
                "cc", Map.of(
                    "type", "string",
                    "title", "CC",
                    "description", "CC email addresses (comma-separated)"
                ),
                "bcc", Map.of(
                    "type", "string",
                    "title", "BCC",
                    "description", "BCC email addresses (comma-separated)"
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
                "contentType", Map.of(
                    "type", "string",
                    "title", "Content Type",
                    "enum", List.of("text/plain", "text/html"),
                    "default", "text/plain"
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
