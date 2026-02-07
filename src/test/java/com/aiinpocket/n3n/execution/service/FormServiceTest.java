package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.entity.FormSubmission;
import com.aiinpocket.n3n.execution.entity.FormTrigger;
import com.aiinpocket.n3n.execution.repository.FormSubmissionRepository;
import com.aiinpocket.n3n.execution.repository.FormTriggerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormServiceTest extends BaseServiceTest {

    @Mock
    private FormTriggerRepository formTriggerRepository;

    @Mock
    private FormSubmissionRepository formSubmissionRepository;

    @InjectMocks
    private FormService formService;

    // ========== createOrUpdateFormTrigger Tests ==========

    @Test
    void createOrUpdateFormTrigger_newTrigger_createsWithToken() {
        // Given
        UUID flowId = UUID.randomUUID();
        String nodeId = "form-node-1";
        Map<String, Object> config = Map.of("title", "Feedback Form", "fields", List.of("name", "email"));
        UUID createdBy = UUID.randomUUID();

        when(formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId))
                .thenReturn(Optional.empty());
        when(formTriggerRepository.save(any(FormTrigger.class)))
                .thenAnswer(inv -> {
                    FormTrigger t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(UUID.randomUUID());
                    return t;
                });

        // When
        FormTrigger result = formService.createOrUpdateFormTrigger(flowId, nodeId, config, null, null, createdBy);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFlowId()).isEqualTo(flowId);
        assertThat(result.getNodeId()).isEqualTo(nodeId);
        assertThat(result.getFormToken()).isNotNull().hasSize(32);
        assertThat(result.getConfig()).containsEntry("title", "Feedback Form");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getSubmissionCount()).isZero();
        assertThat(result.getMaxSubmissions()).isZero();
        assertThat(result.getExpiresAt()).isNull();
        assertThat(result.getCreatedBy()).isEqualTo(createdBy);

        verify(formTriggerRepository).save(any(FormTrigger.class));
    }

    @Test
    void createOrUpdateFormTrigger_newWithExpiration_setsExpiresAt() {
        // Given
        UUID flowId = UUID.randomUUID();
        String nodeId = "form-node-2";
        Map<String, Object> config = Map.of("title", "Survey");
        UUID createdBy = UUID.randomUUID();

        when(formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId))
                .thenReturn(Optional.empty());
        when(formTriggerRepository.save(any(FormTrigger.class)))
                .thenAnswer(inv -> {
                    FormTrigger t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(UUID.randomUUID());
                    return t;
                });

        // When
        FormTrigger result = formService.createOrUpdateFormTrigger(flowId, nodeId, config, 7, 100, createdBy);

        // Then
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(result.getMaxSubmissions()).isEqualTo(100);
    }

    @Test
    void createOrUpdateFormTrigger_existingTrigger_updatesConfig() {
        // Given
        UUID flowId = UUID.randomUUID();
        String nodeId = "form-node-1";
        UUID triggerId = UUID.randomUUID();

        FormTrigger existing = FormTrigger.builder()
                .id(triggerId)
                .flowId(flowId)
                .nodeId(nodeId)
                .formToken("existing-token-1234567890123456")
                .config(Map.of("title", "Old Title"))
                .isActive(true)
                .submissionCount(5)
                .maxSubmissions(0)
                .createdBy(UUID.randomUUID())
                .build();

        Map<String, Object> newConfig = Map.of("title", "Updated Title", "newField", true);

        when(formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId))
                .thenReturn(Optional.of(existing));
        when(formTriggerRepository.save(any(FormTrigger.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FormTrigger result = formService.createOrUpdateFormTrigger(flowId, nodeId, newConfig, 30, 200, UUID.randomUUID());

        // Then
        assertThat(result.getId()).isEqualTo(triggerId);
        assertThat(result.getFormToken()).isEqualTo("existing-token-1234567890123456"); // token unchanged
        assertThat(result.getConfig()).containsEntry("title", "Updated Title");
        assertThat(result.getConfig()).containsEntry("newField", true);
        assertThat(result.getMaxSubmissions()).isEqualTo(200);
        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getSubmissionCount()).isEqualTo(5); // unchanged
    }

    // ========== getFormTriggerByToken Tests ==========

    @Test
    void getFormTriggerByToken_existing_returnsTrigger() {
        // Given
        String token = "valid-token-12345678901234567890";
        FormTrigger trigger = FormTrigger.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .nodeId("node1")
                .formToken(token)
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .build();

        when(formTriggerRepository.findByFormToken(token)).thenReturn(Optional.of(trigger));

        // When
        FormTrigger result = formService.getFormTriggerByToken(token);

        // Then
        assertThat(result).isSameAs(trigger);
        assertThat(result.getFormToken()).isEqualTo(token);
    }

    @Test
    void getFormTriggerByToken_notFound_throwsResourceNotFoundException() {
        // Given
        String token = "invalid-token";
        when(formTriggerRepository.findByFormToken(token)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> formService.getFormTriggerByToken(token))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Form not found");
    }

    // ========== getFormTrigger Tests ==========

    @Test
    void getFormTrigger_existing_returnsTrigger() {
        // Given
        UUID triggerId = UUID.randomUUID();
        FormTrigger trigger = FormTrigger.builder()
                .id(triggerId)
                .flowId(UUID.randomUUID())
                .nodeId("node1")
                .formToken("some-token")
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .build();

        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.of(trigger));

        // When
        FormTrigger result = formService.getFormTrigger(triggerId);

        // Then
        assertThat(result).isSameAs(trigger);
        assertThat(result.getId()).isEqualTo(triggerId);
    }

    @Test
    void getFormTrigger_notFound_throwsResourceNotFoundException() {
        // Given
        UUID triggerId = UUID.randomUUID();
        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> formService.getFormTrigger(triggerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(triggerId.toString());
    }

    // ========== getFormTriggerByFlowAndNode Tests ==========

    @Test
    void getFormTriggerByFlowAndNode_existing_returnsOptional() {
        // Given
        UUID flowId = UUID.randomUUID();
        String nodeId = "node1";
        FormTrigger trigger = FormTrigger.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .nodeId(nodeId)
                .formToken("token")
                .config(Map.of())
                .isActive(true)
                .build();

        when(formTriggerRepository.findByFlowIdAndNodeId(flowId, nodeId))
                .thenReturn(Optional.of(trigger));

        // When
        Optional<FormTrigger> result = formService.getFormTriggerByFlowAndNode(flowId, nodeId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getFlowId()).isEqualTo(flowId);
    }

    @Test
    void getFormTriggerByFlowAndNode_notFound_returnsEmpty() {
        // Given
        UUID flowId = UUID.randomUUID();
        when(formTriggerRepository.findByFlowIdAndNodeId(flowId, "missing"))
                .thenReturn(Optional.empty());

        // When
        Optional<FormTrigger> result = formService.getFormTriggerByFlowAndNode(flowId, "missing");

        // Then
        assertThat(result).isEmpty();
    }

    // ========== getFormTriggersForFlow Tests ==========

    @Test
    void getFormTriggersForFlow_hasTriggers_returnsList() {
        // Given
        UUID flowId = UUID.randomUUID();
        List<FormTrigger> triggers = List.of(
                FormTrigger.builder().id(UUID.randomUUID()).flowId(flowId).nodeId("n1").formToken("t1").config(Map.of()).build(),
                FormTrigger.builder().id(UUID.randomUUID()).flowId(flowId).nodeId("n2").formToken("t2").config(Map.of()).build()
        );

        when(formTriggerRepository.findByFlowId(flowId)).thenReturn(triggers);

        // When
        List<FormTrigger> result = formService.getFormTriggersForFlow(flowId);

        // Then
        assertThat(result).hasSize(2);
    }

    // ========== deactivateFormTrigger Tests ==========

    @Test
    void deactivateFormTrigger_existingTrigger_setsInactive() {
        // Given
        UUID triggerId = UUID.randomUUID();
        FormTrigger trigger = FormTrigger.builder()
                .id(triggerId)
                .flowId(UUID.randomUUID())
                .nodeId("node1")
                .formToken("token")
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .build();

        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.of(trigger));
        when(formTriggerRepository.save(any(FormTrigger.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        formService.deactivateFormTrigger(triggerId);

        // Then
        assertThat(trigger.getIsActive()).isFalse();
        assertThat(trigger.getUpdatedAt()).isNotNull();
        verify(formTriggerRepository).save(trigger);
    }

    @Test
    void deactivateFormTrigger_notFound_doesNothing() {
        // Given
        UUID triggerId = UUID.randomUUID();
        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.empty());

        // When
        formService.deactivateFormTrigger(triggerId);

        // Then
        verify(formTriggerRepository, never()).save(any());
    }

    // ========== regenerateFormToken Tests ==========

    @Test
    void regenerateFormToken_existingTrigger_generatesNewToken() {
        // Given
        UUID triggerId = UUID.randomUUID();
        String oldToken = "old-token-1234567890123456789012";
        FormTrigger trigger = FormTrigger.builder()
                .id(triggerId)
                .flowId(UUID.randomUUID())
                .nodeId("node1")
                .formToken(oldToken)
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .build();

        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.of(trigger));
        when(formTriggerRepository.save(any(FormTrigger.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FormTrigger result = formService.regenerateFormToken(triggerId);

        // Then
        assertThat(result.getFormToken()).isNotEqualTo(oldToken);
        assertThat(result.getFormToken()).hasSize(32);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(formTriggerRepository).save(any(FormTrigger.class));
    }

    @Test
    void regenerateFormToken_notFound_throwsResourceNotFoundException() {
        // Given
        UUID triggerId = UUID.randomUUID();
        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> formService.regenerateFormToken(triggerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(triggerId.toString());
    }

    // ========== createFormSubmission Tests ==========

    @Test
    void createFormSubmission_newSubmission_createsSuccessfully() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "form-node-1";
        Map<String, Object> data = Map.of("name", "John", "email", "john@example.com");
        UUID submittedBy = UUID.randomUUID();
        String ip = "192.168.1.100";

        when(formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId)).thenReturn(false);
        when(formSubmissionRepository.save(any(FormSubmission.class)))
                .thenAnswer(inv -> {
                    FormSubmission s = inv.getArgument(0);
                    if (s.getId() == null) s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        FormSubmission result = formService.createFormSubmission(executionId, nodeId, data, submittedBy, ip);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(executionId);
        assertThat(result.getNodeId()).isEqualTo(nodeId);
        assertThat(result.getData()).containsEntry("name", "John");
        assertThat(result.getData()).containsEntry("email", "john@example.com");
        assertThat(result.getSubmittedBy()).isEqualTo(submittedBy);
        assertThat(result.getSubmittedIp()).isEqualTo(ip);

        ArgumentCaptor<FormSubmission> captor = ArgumentCaptor.forClass(FormSubmission.class);
        verify(formSubmissionRepository).save(captor.capture());
        assertThat(captor.getValue().getExecutionId()).isEqualTo(executionId);
    }

    @Test
    void createFormSubmission_alreadySubmitted_throwsIllegalStateException() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "form-node-1";

        when(formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> formService.createFormSubmission(
                executionId, nodeId, Map.of("key", "value"), UUID.randomUUID(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been submitted");

        verify(formSubmissionRepository, never()).save(any());
    }

    // ========== getFormSubmission Tests ==========

    @Test
    void getFormSubmission_existing_returnsOptional() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "form-node";
        FormSubmission submission = FormSubmission.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .nodeId(nodeId)
                .data(Map.of("answer", "yes"))
                .build();

        when(formSubmissionRepository.findByExecutionIdAndNodeId(executionId, nodeId))
                .thenReturn(Optional.of(submission));

        // When
        Optional<FormSubmission> result = formService.getFormSubmission(executionId, nodeId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getData()).containsEntry("answer", "yes");
    }

    @Test
    void getFormSubmission_notFound_returnsEmpty() {
        // Given
        UUID executionId = UUID.randomUUID();
        when(formSubmissionRepository.findByExecutionIdAndNodeId(executionId, "missing"))
                .thenReturn(Optional.empty());

        // When
        Optional<FormSubmission> result = formService.getFormSubmission(executionId, "missing");

        // Then
        assertThat(result).isEmpty();
    }

    // ========== hasFormBeenSubmitted Tests ==========

    @Test
    void hasFormBeenSubmitted_submitted_returnsTrue() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "form-node";
        when(formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId)).thenReturn(true);

        // When
        boolean result = formService.hasFormBeenSubmitted(executionId, nodeId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasFormBeenSubmitted_notSubmitted_returnsFalse() {
        // Given
        UUID executionId = UUID.randomUUID();
        String nodeId = "form-node";
        when(formSubmissionRepository.existsByExecutionIdAndNodeId(executionId, nodeId)).thenReturn(false);

        // When
        boolean result = formService.hasFormBeenSubmitted(executionId, nodeId);

        // Then
        assertThat(result).isFalse();
    }

    // ========== incrementSubmissionCount Tests ==========

    @Test
    void incrementSubmissionCount_existingTrigger_incrementsCount() {
        // Given
        UUID triggerId = UUID.randomUUID();
        FormTrigger trigger = FormTrigger.builder()
                .id(triggerId)
                .flowId(UUID.randomUUID())
                .nodeId("node1")
                .formToken("token")
                .config(Map.of())
                .isActive(true)
                .submissionCount(3)
                .build();

        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.of(trigger));
        when(formTriggerRepository.save(any(FormTrigger.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        formService.incrementSubmissionCount(triggerId);

        // Then
        assertThat(trigger.getSubmissionCount()).isEqualTo(4);
        verify(formTriggerRepository).save(trigger);
    }

    @Test
    void incrementSubmissionCount_notFound_doesNothing() {
        // Given
        UUID triggerId = UUID.randomUUID();
        when(formTriggerRepository.findById(triggerId)).thenReturn(Optional.empty());

        // When
        formService.incrementSubmissionCount(triggerId);

        // Then
        verify(formTriggerRepository, never()).save(any());
    }

    // ========== expireOldFormTriggers Tests ==========

    @Test
    void expireOldFormTriggers_hasExpired_deactivatesAndReturnsCount() {
        // Given
        FormTrigger expired1 = FormTrigger.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .nodeId("n1")
                .formToken("t1")
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        FormTrigger expired2 = FormTrigger.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .nodeId("n2")
                .formToken("t2")
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .expiresAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .build();
        FormTrigger expired3 = FormTrigger.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .nodeId("n3")
                .formToken("t3")
                .config(Map.of())
                .isActive(true)
                .submissionCount(0)
                .expiresAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();

        when(formTriggerRepository.findExpiredTriggers(any(Instant.class)))
                .thenReturn(List.of(expired1, expired2, expired3));
        when(formTriggerRepository.save(any(FormTrigger.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = formService.expireOldFormTriggers();

        // Then
        assertThat(count).isEqualTo(3);
        assertThat(expired1.getIsActive()).isFalse();
        assertThat(expired2.getIsActive()).isFalse();
        assertThat(expired3.getIsActive()).isFalse();
        assertThat(expired1.getUpdatedAt()).isNotNull();
        assertThat(expired2.getUpdatedAt()).isNotNull();
        assertThat(expired3.getUpdatedAt()).isNotNull();

        verify(formTriggerRepository, times(3)).save(any(FormTrigger.class));
    }

    @Test
    void expireOldFormTriggers_noExpired_returnsZero() {
        // Given
        when(formTriggerRepository.findExpiredTriggers(any(Instant.class)))
                .thenReturn(List.of());

        // When
        int count = formService.expireOldFormTriggers();

        // Then
        assertThat(count).isZero();
        verify(formTriggerRepository, never()).save(any());
    }

    // ========== getFormSubmissionsForExecution Tests ==========

    @Test
    void getFormSubmissionsForExecution_hasSubmissions_returnsList() {
        // Given
        UUID executionId = UUID.randomUUID();
        List<FormSubmission> submissions = List.of(
                FormSubmission.builder().id(UUID.randomUUID()).executionId(executionId).nodeId("n1").data(Map.of()).build(),
                FormSubmission.builder().id(UUID.randomUUID()).executionId(executionId).nodeId("n2").data(Map.of()).build()
        );

        when(formSubmissionRepository.findByExecutionId(executionId)).thenReturn(submissions);

        // When
        List<FormSubmission> result = formService.getFormSubmissionsForExecution(executionId);

        // Then
        assertThat(result).hasSize(2);
        verify(formSubmissionRepository).findByExecutionId(executionId);
    }
}
