package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.service.ExecutionApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalNodeHandlerTest {
    @Mock private ExecutionApprovalService approvalService;
    private ApprovalNodeHandler handler;

    @BeforeEach void setUp() { handler = new ApprovalNodeHandler(approvalService); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("approval"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("Approval"); }
        @Test void getCategory() { assertThat(handler.getCategory()).isEqualTo("Flow Control"); }
        @Test void supportsAsync() { assertThat(handler.supportsAsync()).isTrue(); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
    }

    @Nested @DisplayName("Resume with Approval Data")
    class ResumeWithApprovalData {
        @Test void execute_resumeWithApproved_returnsApprovedBranch() {
            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("approvalStatus", "approved");
            resumeData.put("approvalId", UUID.randomUUID().toString());
            resumeData.put("approvedBy", "user1");

            Map<String, Object> globalContext = new HashMap<>();
            globalContext.put("_resumeData", resumeData);

            Map<String, Object> config = new HashMap<>();
            config.put("approvedBranch", "yes");
            config.put("rejectedBranch", "no");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("approval-1").nodeType("approval")
                    .nodeConfig(new HashMap<>(config)).globalContext(globalContext)
                    .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("status", "approved");
            assertThat(result.getOutput()).containsEntry("branch", "yes");
        }

        @Test void execute_resumeWithRejected_returnsRejectedBranch() {
            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("approvalStatus", "rejected");
            resumeData.put("approvalId", UUID.randomUUID().toString());

            Map<String, Object> globalContext = new HashMap<>();
            globalContext.put("_resumeData", resumeData);

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("approval-1").nodeType("approval")
                    .nodeConfig(new HashMap<>()).globalContext(globalContext)
                    .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("status", "rejected");
            assertThat(result.getOutput()).containsEntry("branch", "rejected");
        }
    }

    @Nested @DisplayName("New Approval Creation")
    class NewApproval {
        @Test void execute_noExisting_createsNewApproval() {
            when(approvalService.getApprovalForExecution(any(), any())).thenReturn(Optional.empty());
            when(approvalService.createApproval(any(), any(), any(), anyInt(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test: unable to create"));

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("approval-1").nodeType("approval")
                    .nodeConfig(new HashMap<>())
                    .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }
}
