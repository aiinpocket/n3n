package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.service.FormService;
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
class FormNodeHandlerTest {
    @Mock private FormService formService;
    private FormNodeHandler handler;

    @BeforeEach void setUp() { handler = new FormNodeHandler(formService); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("form"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).isEqualTo("Form"); }
        @Test void getCategory() { assertThat(handler.getCategory()).isEqualTo("Flow Control"); }
        @Test void supportsAsync() { assertThat(handler.supportsAsync()).isTrue(); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
    }

    @Nested @DisplayName("Resume with Form Data")
    class ResumeWithFormData {
        @Test void execute_resumeWithFormData_returnsFormOutput() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "Alice");
            formData.put("email", "alice@example.com");

            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("formData", formData);

            Map<String, Object> globalContext = new HashMap<>();
            globalContext.put("_resumeData", resumeData);

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("form-1").nodeType("form")
                    .nodeConfig(new HashMap<>()).globalContext(globalContext)
                    .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("name", "Alice");
            assertThat(result.getOutput()).containsEntry("email", "alice@example.com");
        }
    }

    @Nested @DisplayName("Pause for Form Input")
    class PauseForInput {
        @Test void execute_noResumeNoExisting_pausesExecution() {
            when(formService.getFormSubmission(any(), any())).thenReturn(Optional.empty());

            Map<String, Object> config = new HashMap<>();
            config.put("formTitle", "Test Form");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("form-1").nodeType("form")
                    .nodeConfig(new HashMap<>(config))
                    .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isPauseRequested()).isTrue();
        }
    }
}
