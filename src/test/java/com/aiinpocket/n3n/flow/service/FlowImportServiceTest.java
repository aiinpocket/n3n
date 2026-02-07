package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.component.repository.ComponentRepository;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.dto.export.ComponentDependency;
import com.aiinpocket.n3n.flow.dto.export.CredentialPlaceholder;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportPreviewResponse;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportRequest;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowImport;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowImportRepository;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowImportServiceTest extends BaseServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private FlowImportRepository importRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private ComponentRepository componentRepository;

    @Mock
    private DagParser dagParser;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FlowImportService flowImportService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    /**
     * Compute SHA-256 checksum the same way FlowImportService does internally.
     */
    private String computeChecksum(FlowExportPackage pkg) throws Exception {
        Map<String, Object> content = Map.of(
                "flow", pkg.getFlow(),
                "dependencies", pkg.getDependencies()
        );
        String json = objectMapper.writeValueAsString(content);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Build a valid FlowExportPackage with correct checksum.
     */
    private FlowExportPackage createValidPackage(Map<String, Object> definition) throws Exception {
        FlowExportPackage pkg = FlowExportPackage.builder()
                .version("1.0")
                .exportedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .exportedBy("test@example.com")
                .flow(FlowExportPackage.FlowData.builder()
                        .name("Test Flow")
                        .description("A test flow for import")
                        .definition(definition)
                        .settings(Map.of())
                        .build())
                .dependencies(FlowExportPackage.FlowDependencies.builder()
                        .components(List.of())
                        .credentialPlaceholders(List.of())
                        .build())
                .build();

        pkg.setChecksum(computeChecksum(pkg));
        return pkg;
    }

    private FlowExportPackage createValidPackage() throws Exception {
        Map<String, Object> definition = Map.of(
                "nodes", List.of(
                        Map.of("id", "node1", "type", "trigger", "data", Map.of("label", "Start")),
                        Map.of("id", "node2", "type", "action", "data", Map.of("label", "Action"))
                ),
                "edges", List.of(
                        Map.of("id", "edge1", "source", "node1", "target", "node2")
                )
        );
        return createValidPackage(definition);
    }

    private DagParser.ParseResult createValidParseResult() {
        DagParser.ParseResult result = new DagParser.ParseResult();
        result.setValid(true);
        result.setExecutionOrder(List.of("node1", "node2"));
        return result;
    }

    private DagParser.ParseResult createInvalidParseResult(String... errors) {
        DagParser.ParseResult result = new DagParser.ParseResult();
        result.setValid(false);
        for (String error : errors) {
            result.addError(error);
        }
        return result;
    }

    // =====================================================================
    // previewImport tests
    // =====================================================================

    @Nested
    @DisplayName("previewImport")
    class PreviewImport {

        @Test
        @DisplayName("Valid package returns correct preview with canImport=true")
        void validPackage_returnsPreview() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result).isNotNull();
            assertThat(result.getFlowName()).isEqualTo("Test Flow");
            assertThat(result.getDescription()).isEqualTo("A test flow for import");
            assertThat(result.getNodeCount()).isEqualTo(2);
            assertThat(result.getEdgeCount()).isEqualTo(1);
            assertThat(result.isCanImport()).isTrue();
            assertThat(result.getBlockers()).isEmpty();
        }

        @Test
        @DisplayName("Invalid checksum throws IllegalArgumentException")
        void invalidChecksum_throws() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.setChecksum("invalid_checksum_value");

            assertThatThrownBy(() -> flowImportService.previewImport(pkg, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checksum");
        }

        @Test
        @DisplayName("Null checksum throws IllegalArgumentException")
        void nullChecksum_throws() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.setChecksum(null);

            assertThatThrownBy(() -> flowImportService.previewImport(pkg, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checksum");
        }

        @Test
        @DisplayName("Invalid DAG adds errors to blockers")
        void invalidDag_includesBlockers() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            DagParser.ParseResult invalidResult = createInvalidParseResult("Cycle detected in flow");
            when(dagParser.parse(any())).thenReturn(invalidResult);

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.isCanImport()).isFalse();
            assertThat(result.getBlockers()).contains("Cycle detected in flow");
        }

        @Test
        @DisplayName("Unresolvable component dependency adds blocker")
        void unresolvableComponent_addBlocker() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.getDependencies().setComponents(List.of(
                    ComponentDependency.builder()
                            .name("missing-component")
                            .version("1.0.0")
                            .image(null) // no image means can't auto-install
                            .build()
            ));
            // Recompute checksum after modifying dependencies
            pkg.setChecksum(computeChecksum(pkg));

            when(componentRepository.existsByNameAndIsDeletedFalse("missing-component")).thenReturn(false);
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.isCanImport()).isFalse();
            assertThat(result.getBlockers()).isNotEmpty();
            assertThat(result.getComponentStatuses()).hasSize(1);
            assertThat(result.getComponentStatuses().get(0).getName()).isEqualTo("missing-component");
            assertThat(result.getComponentStatuses().get(0).isInstalled()).isFalse();
            assertThat(result.getComponentStatuses().get(0).isCanAutoInstall()).isFalse();
        }

        @Test
        @DisplayName("Installed component reports installed=true and no blocker")
        void installedComponent_noBlocker() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.getDependencies().setComponents(List.of(
                    ComponentDependency.builder()
                            .name("installed-component")
                            .version("1.0.0")
                            .image("docker.io/comp:1.0.0")
                            .build()
            ));
            pkg.setChecksum(computeChecksum(pkg));

            when(componentRepository.existsByNameAndIsDeletedFalse("installed-component")).thenReturn(true);
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.isCanImport()).isTrue();
            assertThat(result.getComponentStatuses()).hasSize(1);
            assertThat(result.getComponentStatuses().get(0).isInstalled()).isTrue();
        }

        @Test
        @DisplayName("Auto-installable component (with image) is not a blocker")
        void autoInstallableComponent_noBlocker() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.getDependencies().setComponents(List.of(
                    ComponentDependency.builder()
                            .name("new-component")
                            .version("2.0.0")
                            .image("ghcr.io/comp:2.0.0")
                            .build()
            ));
            pkg.setChecksum(computeChecksum(pkg));

            when(componentRepository.existsByNameAndIsDeletedFalse("new-component")).thenReturn(false);
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.isCanImport()).isTrue();
            assertThat(result.getComponentStatuses().get(0).isCanAutoInstall()).isTrue();
        }

        @Test
        @DisplayName("Credential placeholders are analyzed with compatible credentials")
        void credentialPlaceholders_analyzed() throws Exception {
            UUID credId = UUID.randomUUID();
            FlowExportPackage pkg = createValidPackage();
            pkg.getDependencies().setCredentialPlaceholders(List.of(
                    CredentialPlaceholder.builder()
                            .nodeId("node1")
                            .nodeName("API Node")
                            .credentialType("api_key")
                            .credentialName("My API Key")
                            .build()
            ));
            pkg.setChecksum(computeChecksum(pkg));

            Credential cred = Credential.builder()
                    .id(credId).name("Existing API Key").type("api_key")
                    .ownerId(userId).encryptedData(new byte[0]).encryptionIv(new byte[0])
                    .build();
            when(credentialRepository.findByOwnerIdAndType(userId, "api_key"))
                    .thenReturn(List.of(cred));
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.getCredentialRequirements()).hasSize(1);
            FlowImportPreviewResponse.CredentialRequirement req = result.getCredentialRequirements().get(0);
            assertThat(req.getNodeId()).isEqualTo("node1");
            assertThat(req.getCredentialType()).isEqualTo("api_key");
            assertThat(req.getCompatibleCredentials()).hasSize(1);
            assertThat(req.getCompatibleCredentials().get(0).getName()).isEqualTo("Existing API Key");
        }

        @Test
        @DisplayName("Empty definition returns 0 nodes and 0 edges")
        void emptyDefinition_zeroNodesAndEdges() throws Exception {
            FlowExportPackage pkg = createValidPackage(Map.of());
            when(dagParser.parse(any())).thenReturn(createValidParseResult());

            FlowImportPreviewResponse result = flowImportService.previewImport(pkg, userId);

            assertThat(result.getNodeCount()).isZero();
            assertThat(result.getEdgeCount()).isZero();
        }
    }

    // =====================================================================
    // importFlow tests
    // =====================================================================

    @Nested
    @DisplayName("importFlow")
    class ImportFlow {

        @Test
        @DisplayName("Creates flow, version, and import record")
        void createsFlowAndVersion() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId)
                    .name("Test Flow (Imported)")
                    .description("A test flow for import")
                    .createdBy(userId)
                    .build();

            when(flowRepository.existsByNameAndIsDeletedFalse(anyString())).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            FlowResponse result = flowImportService.importFlow(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(flowId);

            verify(flowRepository).save(any(Flow.class));
            verify(flowVersionRepository).save(any(FlowVersion.class));
            verify(importRepository).save(any(FlowImport.class));
        }

        @Test
        @DisplayName("Uses custom name when newFlowName is provided")
        void usesCustomName() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("My Custom Name")
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("My Custom Name").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("My Custom Name")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<Flow> flowCaptor = ArgumentCaptor.forClass(Flow.class);
            verify(flowRepository).save(flowCaptor.capture());
            assertThat(flowCaptor.getValue().getName()).isEqualTo("My Custom Name");
        }

        @Test
        @DisplayName("Default name appends (Imported) when no custom name given")
        void defaultNameAppendsImported() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName(null)
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Test Flow (Imported)").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("Test Flow (Imported)")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<Flow> flowCaptor = ArgumentCaptor.forClass(Flow.class);
            verify(flowRepository).save(flowCaptor.capture());
            assertThat(flowCaptor.getValue().getName()).isEqualTo("Test Flow (Imported)");
        }

        @Test
        @DisplayName("Duplicate name appends timestamp")
        void duplicateName_appendsTimestamp() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("Existing Flow")
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Existing Flow - 123456").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("Existing Flow")).thenReturn(true);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<Flow> flowCaptor = ArgumentCaptor.forClass(Flow.class);
            verify(flowRepository).save(flowCaptor.capture());
            // Name should start with "Existing Flow - " and have a timestamp appended
            assertThat(flowCaptor.getValue().getName()).startsWith("Existing Flow - ");
        }

        @Test
        @DisplayName("Invalid checksum in importFlow throws exception")
        void invalidChecksum_throws() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            pkg.setChecksum("tampered_checksum");
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .build();

            assertThatThrownBy(() -> flowImportService.importFlow(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checksum");
        }

        @Test
        @DisplayName("Version is created with version 1.0.0 and status draft")
        void versionCreatedCorrectly() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Test Flow (Imported)").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse(anyString())).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowVersion> versionCaptor = ArgumentCaptor.forClass(FlowVersion.class);
            verify(flowVersionRepository).save(versionCaptor.capture());
            FlowVersion savedVersion = versionCaptor.getValue();
            assertThat(savedVersion.getFlowId()).isEqualTo(flowId);
            assertThat(savedVersion.getVersion()).isEqualTo("1.0.0");
            assertThat(savedVersion.getStatus()).isEqualTo("draft");
            assertThat(savedVersion.getCreatedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Import record is saved with correct fields")
        void importRecordSaved() throws Exception {
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Test Flow (Imported)").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse(anyString())).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowImport> importCaptor = ArgumentCaptor.forClass(FlowImport.class);
            verify(importRepository).save(importCaptor.capture());
            FlowImport savedImport = importCaptor.getValue();
            assertThat(savedImport.getFlowId()).isEqualTo(flowId);
            assertThat(savedImport.getPackageVersion()).isEqualTo("1.0");
            assertThat(savedImport.getPackageChecksum()).isEqualTo(pkg.getChecksum());
            assertThat(savedImport.getOriginalFlowName()).isEqualTo("Test Flow");
            assertThat(savedImport.getImportedBy()).isEqualTo(userId);
            assertThat(savedImport.getStatus()).isEqualTo(FlowImport.STATUS_RESOLVED);
        }

        @Test
        @DisplayName("Credential mappings remap credentialId in definition nodes")
        void credentialMappings_remapDefinition() throws Exception {
            UUID newCredId = UUID.randomUUID();
            Map<String, Object> definition = new HashMap<>();
            definition.put("nodes", List.of(
                    Map.of("id", "node1", "type", "action",
                            "data", Map.of("label", "HTTP", "credentialId", "old-cred-id")),
                    Map.of("id", "node2", "type", "output",
                            "data", Map.of("label", "Output"))
            ));
            definition.put("edges", List.of(
                    Map.of("id", "edge1", "source", "node1", "target", "node2")
            ));

            FlowExportPackage pkg = createValidPackage(definition);
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("Credential Test")
                    .credentialMappings(Map.of("node1", newCredId))
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Credential Test").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("Credential Test")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowVersion> versionCaptor = ArgumentCaptor.forClass(FlowVersion.class);
            verify(flowVersionRepository).save(versionCaptor.capture());
            Map<String, Object> savedDefinition = versionCaptor.getValue().getDefinition();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) savedDefinition.get("nodes");
            @SuppressWarnings("unchecked")
            Map<String, Object> node1Data = (Map<String, Object>) nodes.get(0).get("data");
            assertThat(node1Data.get("credentialId")).isEqualTo(newCredId.toString());
        }

        @Test
        @DisplayName("No credential mappings removes credentialId references")
        void noCredentialMappings_removesReferences() throws Exception {
            Map<String, Object> definition = new HashMap<>();
            definition.put("nodes", List.of(
                    Map.of("id", "node1", "type", "action",
                            "data", Map.of("label", "HTTP", "credentialId", "old-cred-id"))
            ));
            definition.put("edges", List.of());

            FlowExportPackage pkg = createValidPackage(definition);
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("No Cred Test")
                    .credentialMappings(null)
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("No Cred Test").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("No Cred Test")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowVersion> versionCaptor = ArgumentCaptor.forClass(FlowVersion.class);
            verify(flowVersionRepository).save(versionCaptor.capture());
            Map<String, Object> savedDefinition = versionCaptor.getValue().getDefinition();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) savedDefinition.get("nodes");
            @SuppressWarnings("unchecked")
            Map<String, Object> node1Data = (Map<String, Object>) nodes.get(0).get("data");
            assertThat(node1Data).doesNotContainKey("credentialId");
        }

        @Test
        @DisplayName("Import record stores credential mappings as string map")
        void importRecord_storesCredentialMappings() throws Exception {
            UUID credId = UUID.randomUUID();
            FlowExportPackage pkg = createValidPackage();
            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("Mapping Test")
                    .credentialMappings(Map.of("node1", credId))
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Mapping Test").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("Mapping Test")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowImport> importCaptor = ArgumentCaptor.forClass(FlowImport.class);
            verify(importRepository).save(importCaptor.capture());
            Map<String, String> mappings = importCaptor.getValue().getCredentialMappings();
            assertThat(mappings).containsEntry("node1", credId.toString());
        }

        @Test
        @DisplayName("Settings from package are preserved in version")
        void settingsPreservedInVersion() throws Exception {
            Map<String, Object> definition = Map.of(
                    "nodes", List.of(Map.of("id", "n1", "type", "trigger", "data", Map.of())),
                    "edges", List.of()
            );

            FlowExportPackage pkg = FlowExportPackage.builder()
                    .version("1.0")
                    .exportedAt(Instant.now())
                    .exportedBy("test")
                    .flow(FlowExportPackage.FlowData.builder()
                            .name("Settings Test")
                            .description("desc")
                            .definition(definition)
                            .settings(Map.of("timeout", 30, "retries", 3))
                            .build())
                    .dependencies(FlowExportPackage.FlowDependencies.builder()
                            .components(List.of())
                            .credentialPlaceholders(List.of())
                            .build())
                    .build();
            pkg.setChecksum(computeChecksum(pkg));

            FlowImportRequest request = FlowImportRequest.builder()
                    .packageData(pkg)
                    .newFlowName("Settings Test")
                    .build();

            UUID flowId = UUID.randomUUID();
            Flow savedFlow = Flow.builder()
                    .id(flowId).name("Settings Test").createdBy(userId).build();

            when(flowRepository.existsByNameAndIsDeletedFalse("Settings Test")).thenReturn(false);
            when(flowRepository.save(any(Flow.class))).thenReturn(savedFlow);
            when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(importRepository.save(any(FlowImport.class))).thenAnswer(inv -> inv.getArgument(0));

            flowImportService.importFlow(request, userId);

            ArgumentCaptor<FlowVersion> versionCaptor = ArgumentCaptor.forClass(FlowVersion.class);
            verify(flowVersionRepository).save(versionCaptor.capture());
            assertThat(versionCaptor.getValue().getSettings()).containsEntry("timeout", 30);
            assertThat(versionCaptor.getValue().getSettings()).containsEntry("retries", 3);
        }
    }
}
