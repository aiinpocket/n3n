package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.FormSubmission;
import com.aiinpocket.n3n.execution.entity.FormTrigger;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.execution.service.FormService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormControllerTest {

    @Mock
    private FormService formService;

    @Mock
    private ExecutionService executionService;

    @Mock
    private FlowShareService flowShareService;

    @Mock
    private IpRateLimiter ipRateLimiter;

    @InjectMocks
    private FormController formController;

    // ===== Helpers =====

    private UserDetails testUser(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private FormTrigger activeFormTrigger(UUID flowId) {
        FormTrigger trigger = new FormTrigger();
        trigger.setId(UUID.randomUUID());
        trigger.setFlowId(flowId);
        trigger.setNodeId("form-node-1");
        trigger.setFormToken("test-token-123");
        trigger.setIsActive(true);
        trigger.setSubmissionCount(0);
        trigger.setMaxSubmissions(100);
        trigger.setCreatedBy(UUID.randomUUID());
        trigger.setConfig(Map.of(
                "formTitle", "Contact Form",
                "formDescription", "Please fill in",
                "fields", List.of(Map.of("name", "email", "type", "text")),
                "submitButtonText", "Send",
                "successMessage", "Thank you!"
        ));
        return trigger;
    }

    private HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }

    // ===== GET /{token} =====

    @Test
    void getFormByToken_shouldReturnFormDefinition() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);

        var response = formController.getFormByToken("test-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getFormByToken_shouldReturnGoneWhenInactive() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        trigger.setIsActive(false);
        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);

        var response = formController.getFormByToken("test-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void getFormByToken_shouldReturnGoneWhenExpired() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        trigger.setExpiresAt(Instant.now().minusSeconds(3600)); // expired
        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);

        var response = formController.getFormByToken("test-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void getFormByToken_shouldReturnGoneWhenAtSubmissionLimit() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        trigger.setSubmissionCount(100);
        trigger.setMaxSubmissions(100);
        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);

        var response = formController.getFormByToken("test-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void getFormByToken_shouldReturn404WhenNotFound() {
        when(formService.getFormTriggerByToken("invalid-token")).thenThrow(new RuntimeException("Not found"));

        var response = formController.getFormByToken("invalid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== POST /{token}/submit =====

    @Test
    void submitForm_shouldTriggerExecution() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        UUID executionId = UUID.randomUUID();
        HttpServletRequest httpRequest = mockRequest();

        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);
        when(formService.tryIncrementSubmissionCount(trigger.getId())).thenReturn(true);
        when(executionService.startExecution(eq(trigger.getFlowId()), eq(trigger.getCreatedBy()), anyMap()))
                .thenReturn(ExecutionResponse.builder().id(executionId).build());

        var response = formController.submitForm("test-token", Map.of("email", "test@example.com"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(formService).tryIncrementSubmissionCount(trigger.getId());
        verify(ipRateLimiter).checkAllowed(eq("form-submit"), anyString(), eq(10), eq(60));
    }

    @Test
    void submitForm_shouldReturnGoneWhenFormNotAccepting() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        HttpServletRequest httpRequest = mockRequest();

        when(formService.getFormTriggerByToken("test-token")).thenReturn(trigger);
        // Atomic check-and-increment returns false when form is inactive/expired/at limit
        when(formService.tryIncrementSubmissionCount(trigger.getId())).thenReturn(false);

        var response = formController.submitForm("test-token", Map.of("email", "test@example.com"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        verify(executionService, never()).startExecution(any(), any(), any());
    }

    @Test
    void submitForm_shouldRejectOversizedPayload() {
        HttpServletRequest httpRequest = mockRequest();

        // Create a map with >500 fields
        Map<String, Object> largeData = new HashMap<>();
        for (int i = 0; i < 501; i++) {
            largeData.put("field" + i, "value" + i);
        }

        var response = formController.submitForm("test-token", largeData, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitForm_shouldReturnBadRequestOnException() {
        HttpServletRequest httpRequest = mockRequest();
        when(formService.getFormTriggerByToken("test-token")).thenThrow(new RuntimeException("Error"));

        var response = formController.submitForm("test-token", Map.of("key", "value"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== POST /execution/{executionId}/submit =====

    @Test
    void submitExecutionForm_shouldResumeExecution() {
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        HttpServletRequest httpRequest = mockRequest();

        FormSubmission submission = new FormSubmission();
        submission.setId(submissionId);

        when(formService.createFormSubmission(eq(executionId), eq("node-1"), anyMap(), eq(userId), anyString()))
                .thenReturn(submission);
        when(executionService.resumeExecution(eq(executionId), anyMap(), eq(userId)))
                .thenReturn(ExecutionResponse.builder().id(executionId).status("running").build());

        var request = new FormController.FormSubmissionRequest("node-1", Map.of("field1", "value1"));
        var response = formController.submitExecutionForm(executionId, request, testUser(userId), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(executionService).resumeExecution(eq(executionId), anyMap(), eq(userId));
    }

    @Test
    void submitExecutionForm_shouldReturnBadRequestOnError() {
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        HttpServletRequest httpRequest = mockRequest();

        when(formService.createFormSubmission(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Error"));

        var request = new FormController.FormSubmissionRequest("node-1", Map.of("field1", "value1"));
        var response = formController.submitExecutionForm(executionId, request, testUser(userId), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ===== GET /flow/{flowId}/url =====

    @Test
    void getFormUrl_shouldReturnUrlWhenUserHasAccess() {
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FormTrigger trigger = activeFormTrigger(flowId);

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(formService.getFormTriggerByFlowAndNode(flowId, "form-node-1")).thenReturn(Optional.of(trigger));

        var response = formController.getFormUrl(flowId, "form-node-1", testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getFormUrl_shouldHandleNullExpiresAt() {
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        FormTrigger trigger = activeFormTrigger(flowId);
        trigger.setExpiresAt(null); // expiresAt is null

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(formService.getFormTriggerByFlowAndNode(flowId, "form-node-1")).thenReturn(Optional.of(trigger));

        var response = formController.getFormUrl(flowId, "form-node-1", testUser(userId));

        // Should NOT throw NPE — bug was: Map.of() doesn't allow null values
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getFormUrl_shouldReturn403WhenNoAccess() {
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        var response = formController.getFormUrl(flowId, "form-node-1", testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getFormUrl_shouldReturn404WhenTriggerNotFound() {
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(formService.getFormTriggerByFlowAndNode(flowId, "missing-node")).thenReturn(Optional.empty());

        var response = formController.getFormUrl(flowId, "missing-node", testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== FormDefinitionResponse DTO =====

    @Test
    void formDefinitionResponse_shouldMapFromTrigger() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());

        var response = FormController.FormDefinitionResponse.from(trigger);

        assertThat(response.token()).isEqualTo("test-token-123");
        assertThat(response.title()).isEqualTo("Contact Form");
        assertThat(response.description()).isEqualTo("Please fill in");
        assertThat(response.submitButtonText()).isEqualTo("Send");
        assertThat(response.successMessage()).isEqualTo("Thank you!");
    }

    @Test
    void formDefinitionResponse_shouldHandleNullConfig() {
        FormTrigger trigger = activeFormTrigger(UUID.randomUUID());
        trigger.setConfig(null);

        var response = FormController.FormDefinitionResponse.from(trigger);

        assertThat(response.title()).isEqualTo("Submit Form");
        assertThat(response.submitButtonText()).isEqualTo("Submit");
    }
}
