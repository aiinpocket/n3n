package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Handler for send email nodes.
 * Sends emails via SMTP using Spring JavaMailSender.
 * Supports credential-based dynamic SMTP configuration.
 */
@Component
@Slf4j
public class SendEmailNodeHandler extends AbstractNodeHandler {

    private final JavaMailSender defaultMailSender;

    public SendEmailNodeHandler(JavaMailSender defaultMailSender) {
        this.defaultMailSender = defaultMailSender;
    }

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
        String to = getStringConfig(context, "to", "");
        String cc = getStringConfig(context, "cc", "");
        String bcc = getStringConfig(context, "bcc", "");
        String subject = getStringConfig(context, "subject", "");
        String body = getStringConfig(context, "body", "");
        String contentType = getStringConfig(context, "contentType", "text/plain");
        String credentialId = getStringConfig(context, "credentialId", "");

        if (to.isEmpty()) {
            return NodeExecutionResult.failure("Recipient (to) is required");
        }
        if (subject.isEmpty()) {
            return NodeExecutionResult.failure("Subject is required");
        }

        try {
            JavaMailSender mailSender;
            String fromAddress = "";

            // 如果提供了 credentialId，從 CredentialService 取得 SMTP 設定
            if (!credentialId.isEmpty()) {
                Map<String, Object> smtpConfig = context.resolveCredential(UUID.fromString(credentialId));
                mailSender = createMailSender(smtpConfig);
                fromAddress = (String) smtpConfig.getOrDefault("username", "");
            } else {
                mailSender = defaultMailSender;
            }

            boolean isHtml = "text/html".equals(contentType);
            boolean needsMime = isHtml || !cc.isEmpty() || !bcc.isEmpty();

            log.info("Sending email to: {}, subject: {}, html: {}", to, subject, isHtml);

            if (needsMime) {
                sendMimeEmail(mailSender, fromAddress, to, cc, bcc, subject, body, isHtml);
            } else {
                sendSimpleEmail(mailSender, fromAddress, to, subject, body);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("status", "sent");
            output.put("to", to);
            if (!cc.isEmpty()) output.put("cc", cc);
            if (!bcc.isEmpty()) output.put("bcc", bcc);
            output.put("subject", subject);
            output.put("contentType", contentType);
            output.put("timestamp", Instant.now().toString());
            output.put("messageId", UUID.randomUUID().toString());

            return NodeExecutionResult.success(output);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            return NodeExecutionResult.failure("Failed to send email: " + e.getMessage());
        }
    }

    private JavaMailSender createMailSender(Map<String, Object> smtpConfig) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost((String) smtpConfig.getOrDefault("host", "smtp.gmail.com"));
        sender.setPort(((Number) smtpConfig.getOrDefault("port", 587)).intValue());
        sender.setUsername((String) smtpConfig.getOrDefault("username", ""));
        sender.setPassword((String) smtpConfig.getOrDefault("password", ""));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable",
                smtpConfig.getOrDefault("starttls", true).toString());
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    private void sendSimpleEmail(JavaMailSender sender, String from, String to,
                                  String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) message.setFrom(from);
        message.setTo(to.split(","));
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("Simple email sent to: {}", to);
    }

    private void sendMimeEmail(JavaMailSender sender, String from, String to,
                                String cc, String bcc, String subject,
                                String body, boolean isHtml) throws MessagingException {
        MimeMessage mimeMessage = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        if (!from.isEmpty()) helper.setFrom(from);
        helper.setTo(to.split(","));
        if (!cc.isEmpty()) helper.setCc(cc.split(","));
        if (!bcc.isEmpty()) helper.setBcc(bcc.split(","));
        helper.setSubject(subject);
        helper.setText(body, isHtml);

        sender.send(mimeMessage);
        log.info("MIME email sent to: {} (html: {})", to, isHtml);
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
