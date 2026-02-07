package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.service.repository.ExternalServiceRepository;
import com.aiinpocket.n3n.service.repository.ServiceEndpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExternalServiceNodeHandlerTest {

    @Mock
    private ExternalServiceRepository serviceRepository;

    @Mock
    private ServiceEndpointRepository endpointRepository;

    private ExternalServiceNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExternalServiceNodeHandler(serviceRepository, endpointRepository, new ObjectMapper());
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsExternalService() {
            assertThat(handler.getType()).isEqualTo("externalService");
        }

        @Test
        void getDisplayName_containsExternalService() {
            assertThat(handler.getDisplayName()).contains("External Service");
        }

        @Test
        void getCategory_returnsIntegrations() {
            assertThat(handler.getCategory()).isEqualTo("Integrations");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsApi() {
            assertThat(handler.getIcon()).isEqualTo("api");
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
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

    // ==================== Validation - Missing Fields ====================

    @Nested
    @DisplayName("Validation - Missing serviceId/endpointId")
    class ValidationMissingFields {

        @Test
        void execute_missingServiceId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpointId", UUID.randomUUID().toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("service");
        }

        @Test
        void execute_missingEndpointId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", UUID.randomUUID().toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("endpoint");
        }

        @Test
        void execute_emptyServiceId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", "");
            config.put("endpointId", UUID.randomUUID().toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_emptyEndpointId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", UUID.randomUUID().toString());
            config.put("endpointId", "");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_emptyConfig_fails() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Validation - Invalid Format ====================

    @Nested
    @DisplayName("Validation - Invalid ID Format")
    class ValidationInvalidFormat {

        @Test
        void execute_invalidServiceIdFormat_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", "not-a-uuid");
            config.put("endpointId", UUID.randomUUID().toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("invalid");
        }

        @Test
        void execute_invalidEndpointIdFormat_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", UUID.randomUUID().toString());
            config.put("endpointId", "not-a-uuid");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("invalid");
        }

        @Test
        void execute_validIdsButServiceNotFound_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("serviceId", UUID.randomUUID().toString());
            config.put("endpointId", UUID.randomUUID().toString());

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("ext-svc-1")
                .nodeType("externalService")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
