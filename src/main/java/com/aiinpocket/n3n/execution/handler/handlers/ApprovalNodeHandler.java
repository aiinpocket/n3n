package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.service.ExecutionApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for Wait for Approval node.
 *
 * This node pauses execution until an approval is submitted.
 * Supports three approval modes:
 * - any: First approver wins (approve or reject)
 * - all: All required approvers must approve
 * - majority: More than 50% must approve
 *
 * Config options:
 * - message: Message to display to approvers
 * - requiredApprovers: Number of approvers required (default: 1)
 * - approvalMode: "any", "all", or "majority" (default: "any")
 * - expiresInMinutes: Optional expiration time
 * - approvedBranch: Branch name when approved (default: "approved")
 * - rejectedBranch: Branch name when rejected (default: "rejected")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalNodeHandler extends AbstractNodeHandler {

    private final ExecutionApprovalService approvalService;

    @Override
    public String getType() {
        return "approval";
    }

    @Override
    public String getDisplayName() {
        return "Wait for Approval";
    }

    @Override
    public String getDescription() {
        return "Pauses workflow execution and waits for human approval before continuing. Supports multiple approval modes.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "user-check";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String message = getStringConfig(context, "message", "Approval required for workflow execution");
        int requiredApprovers = getIntConfig(context, "requiredApprovers", 1);
        String approvalMode = getStringConfig(context, "approvalMode", "any");
        int expiresInMinutes = getIntConfig(context, "expiresInMinutes", 0);
        String approvedBranch = getStringConfig(context, "approvedBranch", "approved");
        String rejectedBranch = getStringConfig(context, "rejectedBranch", "rejected");

        // Check if we're resuming with approval data
        Map<String, Object> resumeData = getResumeData(context);
        if (resumeData != null && !resumeData.isEmpty()) {
            return handleResume(context, resumeData, approvedBranch, rejectedBranch);
        }

        // Check if approval already exists
        Optional<ExecutionApproval> existingApproval = approvalService.getApprovalForExecution(
            context.getExecutionId(), context.getNodeId());

        if (existingApproval.isPresent()) {
            ExecutionApproval approval = existingApproval.get();
            if (approval.isResolved()) {
                // Already resolved, return result based on status
                return createResultFromApproval(approval, approvedBranch, rejectedBranch);
            }
            // Still pending, pause again
            return createPauseResult(approval);
        }

        // Create new approval
        try {
            // Build metadata from input
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("flowId", context.getFlowId() != null ? context.getFlowId().toString() : null);
            metadata.put("nodeId", context.getNodeId());
            metadata.put("inputData", context.getInputData());

            ExecutionApproval approval = approvalService.createApproval(
                context.getExecutionId(),
                context.getNodeId(),
                message,
                requiredApprovers,
                approvalMode,
                expiresInMinutes > 0 ? expiresInMinutes : null,
                metadata
            );

            log.info("Created approval request: id={}, executionId={}, nodeId={}",
                approval.getId(), context.getExecutionId(), context.getNodeId());

            return createPauseResult(approval);
        } catch (Exception e) {
            log.error("Failed to create approval: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Failed to create approval: " + e.getMessage());
        }
    }

    /**
     * Handle resume from approval submission.
     */
    private NodeExecutionResult handleResume(NodeExecutionContext context, Map<String, Object> resumeData,
                                              String approvedBranch, String rejectedBranch) {
        String approvalStatus = (String) resumeData.get("approvalStatus");
        String approvalId = (String) resumeData.get("approvalId");

        log.info("Resuming approval node: executionId={}, nodeId={}, status={}",
            context.getExecutionId(), context.getNodeId(), approvalStatus);

        Map<String, Object> output = new HashMap<>();
        output.put("approvalId", approvalId);
        output.put("status", approvalStatus);
        output.put("resumedAt", System.currentTimeMillis());

        // Copy any additional data from resume
        if (resumeData.containsKey("comment")) {
            output.put("comment", resumeData.get("comment"));
        }
        if (resumeData.containsKey("approvedBy")) {
            output.put("approvedBy", resumeData.get("approvedBy"));
        }
        if (resumeData.containsKey("rejectedBy")) {
            output.put("rejectedBy", resumeData.get("rejectedBy"));
        }

        // Determine which branch to follow
        String branch = "approved".equals(approvalStatus) ? approvedBranch : rejectedBranch;
        output.put("branch", branch);

        return NodeExecutionResult.withBranches(output, List.of(branch));
    }

    /**
     * Create result from existing resolved approval.
     */
    private NodeExecutionResult createResultFromApproval(ExecutionApproval approval,
                                                          String approvedBranch, String rejectedBranch) {
        Map<String, Object> output = new HashMap<>();
        output.put("approvalId", approval.getId().toString());
        output.put("status", approval.getStatus());
        output.put("approvedCount", approval.getApprovedCount());
        output.put("rejectedCount", approval.getRejectedCount());
        output.put("resolvedAt", approval.getResolvedAt() != null ? approval.getResolvedAt().toString() : null);

        String branch;
        if ("approved".equals(approval.getStatus())) {
            branch = approvedBranch;
        } else if ("rejected".equals(approval.getStatus()) || "expired".equals(approval.getStatus())) {
            branch = rejectedBranch;
        } else {
            // Cancelled or other status
            return NodeExecutionResult.failure("Approval was " + approval.getStatus());
        }

        output.put("branch", branch);
        return NodeExecutionResult.withBranches(output, List.of(branch));
    }

    /**
     * Create pause result to wait for approval.
     */
    private NodeExecutionResult createPauseResult(ExecutionApproval approval) {
        Map<String, Object> resumeCondition = new HashMap<>();
        resumeCondition.put("type", "approval");
        resumeCondition.put("approvalId", approval.getId().toString());
        resumeCondition.put("approvalMode", approval.getApprovalMode());
        resumeCondition.put("requiredApprovers", approval.getRequiredApprovers());

        Map<String, Object> partialOutput = new HashMap<>();
        partialOutput.put("approvalId", approval.getId().toString());
        partialOutput.put("status", "pending");
        partialOutput.put("createdAt", approval.getCreatedAt().toString());

        String pauseReason = String.format("Waiting for approval: %s",
            approval.getMessage() != null ? approval.getMessage() : "Approval required");

        return NodeExecutionResult.pause(pauseReason, resumeCondition, partialOutput);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getResumeData(NodeExecutionContext context) {
        Map<String, Object> globalContext = context.getGlobalContext();
        if (globalContext != null && globalContext.containsKey("_resumeData")) {
            return (Map<String, Object>) globalContext.get("_resumeData");
        }
        return null;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "message", Map.of(
                    "type", "string",
                    "title", "Approval Message",
                    "description", "Message to display to approvers",
                    "default", "Please review and approve this workflow execution"
                ),
                "requiredApprovers", Map.of(
                    "type", "integer",
                    "title", "Required Approvers",
                    "description", "Number of approvers required",
                    "default", 1,
                    "minimum", 1
                ),
                "approvalMode", Map.of(
                    "type", "string",
                    "title", "Approval Mode",
                    "enum", List.of("any", "all", "majority"),
                    "enumNames", List.of("Any (first vote decides)", "All must approve", "Majority (>50%)"),
                    "default", "any"
                ),
                "expiresInMinutes", Map.of(
                    "type", "integer",
                    "title", "Expiration (minutes)",
                    "description", "Optional timeout in minutes. 0 means no expiration",
                    "default", 0,
                    "minimum", 0
                ),
                "approvedBranch", Map.of(
                    "type", "string",
                    "title", "Approved Branch",
                    "description", "Output branch when approved",
                    "default", "approved"
                ),
                "rejectedBranch", Map.of(
                    "type", "string",
                    "title", "Rejected Branch",
                    "description", "Output branch when rejected",
                    "default", "rejected"
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
                Map.of("name", "approved", "type", "any", "description", "Taken when approval is granted"),
                Map.of("name", "rejected", "type", "any", "description", "Taken when approval is rejected or expired")
            )
        );
    }
}
