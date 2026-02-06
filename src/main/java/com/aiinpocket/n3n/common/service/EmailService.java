package com.aiinpocket.n3n.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendFlowInvitation(String toEmail, String flowName, String sharedByName) {
        if (!isMailConfigured()) {
            log.info("Mail not configured. Flow invitation for {} would be sent to {}", flowName, toEmail);
            return;
        }
        send(toEmail, "Flow Invitation: " + flowName,
                "You have been invited to collaborate on flow '" + flowName + "' by " + sharedByName + ".\n\n" +
                        "Visit " + baseUrl + " to access it.");
    }

    public void sendPasswordReset(String toEmail, String tempPassword) {
        if (!isMailConfigured()) {
            log.info("Mail not configured. Password reset would be sent to {}", toEmail);
            return;
        }
        send(toEmail, "Password Reset",
                "Your password has been reset. Your temporary password is: " + tempPassword + "\n\n" +
                        "Please change it after logging in at " + baseUrl);
    }

    public void sendUserInvitation(String toEmail, String name, String tempPassword) {
        if (!isMailConfigured()) {
            log.info("Mail not configured. User invitation would be sent to {}", toEmail);
            return;
        }
        send(toEmail, "Welcome to N3N",
                "Hello " + name + ",\n\nYou have been invited to N3N Flow Platform.\n\n" +
                        "Your temporary password is: " + tempPassword + "\n\n" +
                        "Login at " + baseUrl);
    }

    private boolean isMailConfigured() {
        return fromAddress != null && !fromAddress.isBlank();
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
