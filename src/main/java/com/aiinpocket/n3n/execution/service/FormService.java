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
        log.info("Form trigger created/updated: id={}, flowId={}, nodeId={}, token={}",
            trigger.getId(), flowId, nodeId, trigger.getFormToken());

        return trigger;
    }

    /**
     * Get form trigger by token (for public form access).
     */
    public FormTrigger getFormTriggerByToken(String token) {
        return formTriggerRepository.findByFormToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Form not found"));
    }

    /**
     * Get form trigger by ID.
     */
    public FormTrigger getFormTrigger(UUID triggerId) {
        return formTriggerRepository.findById(triggerId)
            .orElseThrow(() -> new ResourceNotFoundException("Form trigger not found: " + triggerId));
    }

    /**
     * Get form trigger by flow and node.
     */
    public Optional<FormTrigger> getFormTriggerByFlowAndNode(UUID flowId, String nodeId) {
        return formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId);
    }

    /**
     * Get all form triggers for a flow.
     */
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
        log.info("Regenerated form token: id={}, newToken={}", triggerId, trigger.getFormToken());
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
    public Optional<FormSubmission> getFormSubmission(UUID executionId, String nodeId) {
        return formSubmissionRepository.findByExecutionIdAndNodeId(executionId, nodeId);
    }

    /**
     * Get all submissions for an execution.
     */
    public List<FormSubmission> getFormSubmissionsForExecution(UUID executionId) {
        return formSubmissionRepository.findByExecutionId(executionId);
    }

    /**
     * Check if form has been submitted.
     */
    public boolean hasFormBeenSubmitted(UUID executionId, String nodeId) {
        return formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId);
    }

    /**
     * Increment submission count for a form trigger.
     */
    @Transactional
    public void incrementSubmissionCount(UUID triggerId) {
        formTriggerRepository.findById(triggerId).ifPresent(trigger -> {
            trigger.incrementSubmissionCount();
            formTriggerRepository.save(trigger);
        });
    }

    /**
     * Expire old form triggers.
     */
    @Transactional
    public int expireOldFormTriggers() {
        List<FormTrigger> expired = formTriggerRepository.findExpiredTriggers(Instant.now());
        for (FormTrigger trigger : expired) {
            trigger.setIsActive(false);
            trigger.setUpdatedAt(Instant.now());
            formTriggerRepository.save(trigger);
            log.info("Expired form trigger: id={}", trigger.getId());
        }
        return expired.size();
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
