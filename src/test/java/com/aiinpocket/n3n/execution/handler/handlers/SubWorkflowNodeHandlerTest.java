package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SubWorkflowNodeHandlerTest {

    @Mock
    private ObjectProvider<ExecutionService> executionServiceProvider;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private ExecutionRepository executionRepository;

    private SubWorkflowNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SubWorkflowNodeHandler(executionServiceProvider, flowRepository, executionRepository);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsSubWorkflow() {
            assertThat(handler.getType()).isEqualTo("subWorkflow");
        }

        @Test
        void getDisplayName_containsSubWorkflow() {
            assertThat(handler.getDisplayName()).containsIgnoringCase("sub-workflow");
        }

        @Test
        void getCategory_returnsFlowControl() {
            assertThat(handler.getCategory()).isEqualTo("Flow Control");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsWorkflow() {
            assertThat(handler.getIcon()).isEqualTo("workflow");
        }

        @Test
        void supportsAsync_returnsFalse() {
            assertThat(handler.supportsAsync()).isFalse();
        }

        @Test
        void getConfigSchema_containsProperties() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }
    }

    // ==================== Validation - Missing workflowId ====================

    @Nested
    @DisplayName("Validation - Missing workflowId")
    class ValidationMissingWorkflowId {

        @Test
        void execute_missingWorkflowId_fails() {
            Map<String, Object> config = new HashMap<>();

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("workflow id");
        }

        @Test
        void execute_emptyWorkflowId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("workflowId", "");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("workflow id");
        }

        @Test
        void execute_invalidWorkflowIdFormat_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("workflowId", "not-a-uuid");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("invalid");
        }
    }

    // ==================== Validation - Workflow Not Found ====================

    @Nested
    @DisplayName("Validation - Workflow Not Found")
    class ValidationWorkflowNotFound {

        @Test
        void execute_nonExistentWorkflowId_fails() {
            UUID randomFlowId = UUID.randomUUID();
            Map<String, Object> config = new HashMap<>();
            config.put("workflowId", randomFlowId.toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("not found");
        }

        @Test
        void execute_emptyConfig_fails() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_nullWorkflowId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("workflowId", null);

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("subworkflow-1")
                .nodeType("subWorkflow")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
