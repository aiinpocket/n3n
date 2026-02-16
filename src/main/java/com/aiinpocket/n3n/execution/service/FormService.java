package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.entity.FormSubmission;
import com.aiinpocket.n3n.execution.entity.FormTrigger;
import com.aiinpocket.n3n.execution.repository.FormSubmissionRepository;
import com.aiinpocket.n3n.execution.repository.FormTriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormService {

    private final FormTriggerRepository formTriggerRepository;
    private final FormSubmissionRepository formSubmissionRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Create or update a form trigger for a flow node.
     */
    @Transactional
    public FormTrigger createOrUpdateFormTrigger(UUID flowId, String nodeId, Map<String, Object> config,
                                                  Integer expiresInDays, Integer maxSubmissions, UUID createdBy) {
        // Validate redirect URL if present in config
        if (config != null && config.get("redirectUrl") != null) {
            validateRedirectUrl(config.get("redirectUrl").toString());
        }

        Optional<FormTrigger> existing = formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId);

        FormTrigger trigger;
        if (existing.isPresent()) {
            trigger = existing.get();
            trigger.setConfig(config);
            trigger.setExpiresAt(expiresInDays != null && expiresInDays > 0
                ? Instant.now().plus(expiresInDays, ChronoUnit.DAYS)
                : null);
            trigger.setMaxSubmissions(maxSubmissions != null ? maxSubmissions : 0);
            trigger.setUpdatedAt(Instant.now());
        } else {
            trigger = FormTrigger.builder()
                .flowId(flowId)
                .nodeId(nodeId)
                .formToken(generateSecureToken())
                .config(config)
                .isActive(true)
                .expiresAt(expiresInDays != null && expiresInDays > 0
                    ? Instant.now().plus(expiresInDays, ChronoUnit.DAYS)
                    : null)
                .maxSubmissions(maxSubmissions != null ? maxSubmissions : 0)
                .submissionCount(0)
                .createdBy(createdBy)
                .build();
        }

        trigger = formTriggerRepository.save(trigger);
        log.info("Form trigger created/updated: id={}, flowId={}, nodeId={}",
            trigger.getId(), flowId, nodeId);

        return trigger;
    }

    /**
     * Get form trigger by token (for public form access).
     */
    @Transactional(readOnly = true)
    public FormTrigger getFormTriggerByToken(String token) {
        return formTriggerRepository.findByFormToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Form not found"));
    }

    /**
     * Get form trigger by ID.
     */
    @Transactional(readOnly = true)
    public FormTrigger getFormTrigger(UUID triggerId) {
        return formTriggerRepository.findById(triggerId)
            .orElseThrow(() -> new ResourceNotFoundException("Form trigger not found: " + triggerId));
    }

    /**
     * Get form trigger by flow and node.
     */
    @Transactional(readOnly = true)
    public Optional<FormTrigger> getFormTriggerByFlowAndNode(UUID flowId, String nodeId) {
        return formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId);
    }

    /**
     * Get all form triggers for a flow.
     */
    @Transactional(readOnly = true)
    public List<FormTrigger> getFormTriggersForFlow(UUID flowId) {
        return formTriggerRepository.findByFlowId(flowId);
    }

    /**
     * Deactivate a form trigger.
     */
    @Transactional
    public void deactivateFormTrigger(UUID triggerId) {
        formTriggerRepository.findById(triggerId).ifPresent(trigger -> {
            trigger.setIsActive(false);
            trigger.setUpdatedAt(Instant.now());
            formTriggerRepository.save(trigger);
            log.info("Deactivated form trigger: id={}", triggerId);
        });
    }

    /**
     * Regenerate form token.
     */
    @Transactional
    public FormTrigger regenerateFormToken(UUID triggerId) {
        FormTrigger trigger = getFormTrigger(triggerId);
        trigger.setFormToken(generateSecureToken());
        trigger.setUpdatedAt(Instant.now());
        trigger = formTriggerRepository.save(trigger);
        log.info("Regenerated form token: id={}", triggerId);
        return trigger;
    }

    // ===== Form Submission Methods =====

    /**
     * Create a form submission for a running execution.
     */
    @Transactional
    public FormSubmission createFormSubmission(UUID executionId, String nodeId, Map<String, Object> data,
                                                UUID submittedBy, String submittedIp) {
        // Check if submission already exists
        if (formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId)) {
            throw new IllegalStateException("Form has already been submitted for this execution");
        }

        FormSubmission submission = FormSubmission.builder()
            .executionId(executionId)
            .nodeId(nodeId)
            .data(data)
            .submittedBy(submittedBy)
            .submittedIp(submittedIp)
            .build();

        submission = formSubmissionRepository.save(submission);
        log.info("Form submission created: id={}, executionId={}, nodeId={}",
            submission.getId(), executionId, nodeId);

        return submission;
    }

    /**
     * Get form submission for execution and node.
     */
    @Transactional(readOnly = true)
    public Optional<FormSubmission> getFormSubmission(UUID executionId, String nodeId) {
        return formSubmissionRepository.findByExecutionIdAndNodeId(executionId, nodeId);
    }

    /**
     * Get all submissions for an execution.
     */
    @Transactional(readOnly = true)
    public List<FormSubmission> getFormSubmissionsForExecution(UUID executionId) {
        return formSubmissionRepository.findByExecutionId(executionId);
    }

    /**
     * Check if form has been submitted.
     */
    @Transactional(readOnly = true)
    public boolean hasFormBeenSubmitted(UUID executionId, String nodeId) {
        return formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId);
    }

    /**
     * Increment submission count for a form trigger.
     */
    @Transactional
    public void incrementSubmissionCount(UUID triggerId) {
        formTriggerRepository.incrementSubmissionCountById(triggerId);
    }

    /**
     * Atomically check-and-increment submission count.
     * Returns true if the submission was accepted (form active, not expired, under limit).
     * Returns false if the form is no longer accepting submissions.
     * This prevents TOCTOU race conditions between canAcceptSubmission() and incrementSubmissionCount().
     */
    @Transactional
    public boolean tryIncrementSubmissionCount(UUID triggerId) {
        return formTriggerRepository.incrementSubmissionCountIfAllowed(triggerId) > 0;
    }

    /**
     * Expire old form triggers.
     */
    @Transactional
    public int expireOldFormTriggers() {
        List<FormTrigger> expired = formTriggerRepository.findExpiredTriggers(Instant.now());
        if (expired.isEmpty()) return 0;
        Instant now = Instant.now();
        for (FormTrigger trigger : expired) {
            trigger.setIsActive(false);
            trigger.setUpdatedAt(now);
        }
        formTriggerRepository.saveAll(expired);
        expired.forEach(t -> log.info("Expired form trigger: id={}", t.getId()));
        return expired.size();
    }

    /**
     * Validate redirect URL to prevent open redirect attacks.
     * Only allows http/https URLs with valid format. Rejects internal network addresses.
     */
    private void validateRedirectUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new IllegalArgumentException("Redirect URL must use http or https protocol");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Redirect URL must have a valid host");
            }
            String lower = host.toLowerCase();
            if (lower.equals("localhost") || lower.equals("127.0.0.1") ||
                lower.equals("0.0.0.0") || lower.equals("::1") ||
                lower.equals("169.254.169.254") ||
                lower.startsWith("10.") || lower.startsWith("192.168.") ||
                lower.matches("172\\.(1[6-9]|2\\d|3[01])\\..*") ||
                lower.endsWith(".internal") || lower.endsWith(".local")) {
                throw new IllegalArgumentException("Redirect URL cannot point to internal network addresses");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid redirect URL format: " + url);
        }
    }

    /**
     * Generate a secure random token for form URLs.
     */
    private String generateSecureToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
