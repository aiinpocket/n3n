package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for email trigger nodes.
 * Triggers workflow when new emails are received via IMAP.
 */
@Component
@Slf4j
public class EmailTriggerHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "emailTrigger";
    }

    @Override
    public String getDisplayName() {
        return "Email Trigger (IMAP)";
    }

    @Override
    public String getDescription() {
        return "Triggers workflow when new emails are received via IMAP.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "mail";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Email trigger is handled by a background polling service
        // When triggered, the email data comes from global context
        Map<String, Object> emailData = context.getGlobal("triggerInput", null);

        if (emailData == null) {
            // Return sample structure for testing
            emailData = new HashMap<>();
            emailData.put("from", "sender@example.com");
            emailData.put("to", "recipient@example.com");
            emailData.put("subject", "Test Email");
            emailData.put("body", "This is a test email body");
            emailData.put("date", java.time.Instant.now().toString());
            emailData.put("attachments", List.of());
        }

        log.info("Email trigger activated with subject: {}", emailData.get("subject"));

        return NodeExecutionResult.builder()
            .success(true)
            .output(emailData)
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("credentialId"),
            "properties", Map.of(
                "credentialId", Map.of(
                    "type", "string",
                    "title", "IMAP Credential",
                    "description", "ID of the IMAP credential to use"
                ),
                "folder", Map.of(
                    "type", "string",
                    "title", "Folder",
                    "description", "Email folder to monitor",
                    "default", "INBOX"
                ),
                "pollInterval", Map.of(
                    "type", "integer",
                    "title", "Poll Interval (seconds)",
                    "description", "How often to check for new emails",
                    "minimum", 30,
                    "default", 60
                ),
                "markAsRead", Map.of(
                    "type", "boolean",
                    "title", "Mark as Read",
                    "description", "Mark processed emails as read",
                    "default", true
                ),
                "filterSubject", Map.of(
                    "type", "string",
                    "title", "Subject Filter",
                    "description", "Only trigger for emails matching this subject pattern"
                ),
                "filterFrom", Map.of(
                    "type", "string",
                    "title", "From Filter",
                    "description", "Only trigger for emails from this sender"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),
            "outputs", List.of(
                Map.of("name", "email", "type", "object",
                    "description", "Email data including from, to, subject, body, date, attachments")
            )
        );
    }
}
