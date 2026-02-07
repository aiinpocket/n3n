package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowExportServiceTest extends BaseServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FlowExportService flowExportService;

    private UUID flowId;
    private UUID userId;
    private Flow testFlow;
    private FlowVersion testVersion;

    @BeforeEach
    void setUp() {
        flowId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testFlow = Flow.builder()
            .id(flowId)
            .name("Test Flow")
            .description("A test flow")
            .createdBy(userId)
            .isDeleted(false)
            .build();

        Map<String, Object> definition = Map.of(
            "nodes", List.of(
                Map.of("id", "node1", "data", Map.of("label", "HTTP Request")),
                Map.of("id", "node2", "data", Map.of("label", "Output"))
            ),
            "edges", List.of()
        );

        testVersion = FlowVersion.builder()
            .flowId(flowId)
            .version("1.0.0")
            .definition(definition)
            .settings(Map.of())
            .build();
    }

    @Nested
    @DisplayName("Export Flow")
    class ExportFlow {

        @Test
        void exportFlow_validFlow_returnsExportPackage() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).email("test@example.com").name("Test").build()
            ));

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.0");
            assertThat(result.getExportedAt()).isNotNull();
            assertThat(result.getFlow()).isNotNull();
            assertThat(result.getFlow().getName()).isEqualTo("Test Flow");
            assertThat(result.getChecksum()).isNotNull().isNotBlank();
        }

        @Test
        void exportFlow_nonExistingFlow_throwsException() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowExportService.exportFlow(flowId, "1.0.0", userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void exportFlow_nonExistingVersion_throwsException() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "9.9.9")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowExportService.exportFlow(flowId, "9.9.9", userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void exportFlow_emailMasked_masksCorrectly() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).email("john@example.com").name("John").build()
            ));

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result.getExportedBy()).startsWith("jo");
            assertThat(result.getExportedBy()).contains("***");
            assertThat(result.getExportedBy()).contains("@example.com");
        }

        @Test
        void exportFlow_userNotFound_usesAnonymous() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result.getExportedBy()).isEqualTo("anonymous");
        }

        @Test
        void exportFlow_withComponentNodes_extractsDependencies() {
            Map<String, Object> definition = Map.of(
                "nodes", List.of(
                    Map.of("id", "node1", "data", Map.of(
                        "label", "My Component",
                        "componentName", "my-component",
                        "componentVersion", "1.0.0"
                    ))
                ),
                "edges", List.of()
            );
            testVersion.setDefinition(definition);

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result.getDependencies().getComponents()).hasSize(1);
            assertThat(result.getDependencies().getComponents().get(0).getName()).isEqualTo("my-component");
        }

        @Test
        void exportFlow_withCredentialNodes_extractsPlaceholders() {
            UUID credId = UUID.randomUUID();
            Map<String, Object> definition = Map.of(
                "nodes", List.of(
                    Map.of("id", "node1", "data", Map.of(
                        "label", "API Call",
                        "credentialId", credId.toString()
                    ))
                ),
                "edges", List.of()
            );
            testVersion.setDefinition(definition);

            Credential cred = Credential.builder()
                .id(credId).name("My API Key").type("api_key").build();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result.getDependencies().getCredentialPlaceholders()).hasSize(1);
            assertThat(result.getDependencies().getCredentialPlaceholders().get(0).getCredentialName())
                .isEqualTo("My API Key");
        }

        @Test
        void exportFlow_checksumIsConsistent() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            FlowExportPackage result1 = flowExportService.exportFlow(flowId, "1.0.0", userId);
            FlowExportPackage result2 = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result1.getChecksum()).isEqualTo(result2.getChecksum());
        }

        @Test
        void exportFlow_noNodesInDefinition_returnsEmptyDependencies() {
            testVersion.setDefinition(Map.of("edges", List.of()));

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0")).thenReturn(Optional.of(testVersion));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            FlowExportPackage result = flowExportService.exportFlow(flowId, "1.0.0", userId);

            assertThat(result.getDependencies().getComponents()).isEmpty();
            assertThat(result.getDependencies().getCredentialPlaceholders()).isEmpty();
        }
    }
}
