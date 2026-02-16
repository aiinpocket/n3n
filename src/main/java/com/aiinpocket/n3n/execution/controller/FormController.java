package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.FormSubmission;
import com.aiinpocket.n3n.execution.entity.FormTrigger;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.execution.service.FormService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for public form access.
 * Forms can be accessed without authentication using a secure token.
 */
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Forms", description = "Public form access and submission")
public class FormController {

    private final FormService formService;
    private final ExecutionService executionService;
    private final FlowShareService flowShareService;
    private final com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    /**
     * Get form definition by token (public endpoint).
     * This returns the form schema for rendering.
     */
    @GetMapping("/{token}")
    public ResponseEntity<?> getFormByToken(
            @PathVariable @Size(max = 255) String token,
            HttpServletRequest request) {
        // Rate limit: 30 form lookups per minute per IP (token enumeration protection)
        ipRateLimiter.checkAllowed("form-lookup", getClientIp(request), 30, 60);
        try {
            FormTrigger trigger = formService.getFormTriggerByToken(token);

            if (!trigger.canAcceptSubmission()) {
                String reason;
                if (!trigger.getIsActive()) {
                    reason = "This form is no longer active";
                } else if (trigger.isExpired()) {
                    reason = "This form has expired";
                } else if (trigger.isAtSubmissionLimit()) {
                    reason = "This form has reached its submission limit";
                } else {
                    reason = "This form is not accepting submissions";
                }
                return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", reason));
            }

            // Return form config for rendering
            return ResponseEntity.ok(FormDefinitionResponse.from(trigger));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching form: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An error occurred while loading the form"));
        }
    }

    /**
     * Submit form data (public endpoint).
     * This triggers the associated flow with the form data.
     */
    @PostMapping("/{token}/submit")
    public ResponseEntity<?> submitForm(
            @PathVariable @Size(max = 255) String token,
            @RequestBody Map<String, Object> formData,
            HttpServletRequest request) {

        // Rate limit: 10 form submissions per minute per IP
        String clientIp = getClientIp(request);
        ipRateLimiter.checkAllowed("form-submit", clientIp, 10, 60);

        // Validate form data size to prevent oversized payloads
        if (formData != null && formData.size() > 500) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Form data exceeds maximum field limit"));
        }

        try {
            FormTrigger trigger = formService.getFormTriggerByToken(token);

            // Atomically check-and-increment to prevent TOCTOU race condition.
            // This ensures two concurrent submissions can't both pass the limit check.
            if (!formService.tryIncrementSubmissionCount(trigger.getId())) {
                return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "This form is not accepting submissions"));
            }

            // Trigger the flow with form data
            log.info("Form submitted: token={}, flowId={}, nodeId={}",
                token, trigger.getFlowId(), trigger.getNodeId());

            // Add metadata to form data
            Map<String, Object> triggerInput = new HashMap<>(formData);
            triggerInput.put("_formSubmission", Map.of(
                "formToken", token,
                "submittedAt", System.currentTimeMillis(),
                "submittedIp", clientIp
            ));

            // Start execution with form data as trigger input
            ExecutionResponse execution = executionService.startExecution(
                trigger.getFlowId(),
                trigger.getCreatedBy(),  // Use form creator as user
                triggerInput
            );

            // Get success message from config
            String successMessage = "Thank you for your submission!";
            if (trigger.getConfig() != null && trigger.getConfig().get("successMessage") != null) {
                successMessage = trigger.getConfig().get("successMessage").toString();
            }

            String redirectUrl = null;
            if (trigger.getConfig() != null && trigger.getConfig().get("redirectUrl") != null) {
                redirectUrl = trigger.getConfig().get("redirectUrl").toString();
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", successMessage,
                "executionId", execution.getId(),
                "redirectUrl", redirectUrl != null ? redirectUrl : ""
            ));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting form: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to submit form. Please try again."));
        }
    }

    /**
     * Submit form data for a running execution (in-flow form).
     * This resumes the execution with the form data.
     */
    @PostMapping("/execution/{executionId}/submit")
    public ResponseEntity<?> submitExecutionForm(
            @PathVariable UUID executionId,
            @Valid @RequestBody FormSubmissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = userDetails != null
                ? UUID.fromString(userDetails.getUsername())
                : null;

            // Early ownership check — only the execution owner can submit in-flow forms
            if (userId != null) {
                executionService.verifyExecutionAccess(executionId, userId);
            }

            String clientIp = getClientIp(httpRequest);

            // Save form submission
            FormSubmission submission = formService.createFormSubmission(
                executionId,
                request.nodeId(),
                request.formData(),
                userId,
                clientIp
            );

            // Resume the execution with form data
            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("formData", request.formData());
            resumeData.put("submissionId", submission.getId().toString());
            resumeData.put("submittedAt", System.currentTimeMillis());

            ExecutionResponse execution = executionService.resumeExecution(
                executionId,
                resumeData,
                userId
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "submissionId", submission.getId(),
                "executionId", execution.getId(),
                "status", execution.getStatus()
            ));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting execution form: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to submit form. Please try again."));
        }
    }

    /**
     * Get form URL for a flow (authenticated endpoint).
     */
    @GetMapping("/flow/{flowId}/url")
    public ResponseEntity<?> getFormUrl(
            @PathVariable UUID flowId,
            @RequestParam @Size(max = 255) String nodeId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        // Verify the user has access to this flow before disclosing form token
        if (!flowShareService.hasAccess(flowId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return formService.getFormTriggerByFlowAndNode(flowId, nodeId)
            .map(trigger -> {
                Map<String, Object> result = new HashMap<>();
                result.put("formUrl", "/forms/" + trigger.getFormToken());
                result.put("formToken", trigger.getFormToken());
                result.put("isActive", trigger.getIsActive());
                result.put("expiresAt", trigger.getExpiresAt() != null ? trigger.getExpiresAt().toString() : null);
                result.put("submissionCount", trigger.getSubmissionCount());
                result.put("maxSubmissions", trigger.getMaxSubmissions());
                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // Validate IP format
        try {
            java.net.InetAddress.getByName(ip);
            return ip;
        } catch (java.net.UnknownHostException e) {
            return request.getRemoteAddr();
        }
    }

    // DTO records

    public record FormSubmissionRequest(
        @NotBlank @Size(max = 100) String nodeId,
        @NotNull Map<String, Object> formData
    ) {}

    public record FormDefinitionResponse(
        String token,
        String title,
        String description,
        Object fields,
        String submitButtonText,
        String successMessage
    ) {
        public static FormDefinitionResponse from(FormTrigger trigger) {
            Map<String, Object> config = trigger.getConfig();
            return new FormDefinitionResponse(
                trigger.getFormToken(),
                config != null ? (String) config.getOrDefault("formTitle", "Submit Form") : "Submit Form",
                config != null ? (String) config.get("formDescription") : null,
                config != null ? config.get("fields") : null,
                config != null ? (String) config.getOrDefault("submitButtonText", "Submit") : "Submit",
                config != null ? (String) config.getOrDefault("successMessage", "Thank you for your submission!") : "Thank you for your submission!"
            );
        }
    }
}
