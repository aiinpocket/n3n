package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.service.ExternalServiceService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FlowServiceTest extends BaseServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowShareRepository flowShareRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private DagParser dagParser;

    @Mock
    private NodeHandlerRegistry nodeHandlerRegistry;

    @Mock
    private ExternalServiceService externalServiceService;

    @InjectMocks
    private FlowService flowService;

    // ========== List/Search Tests ==========

    @Test
    void listFlows_returnsPagedFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow();
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        Page<FlowResponse> result = flowService.listFlows(userId, pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo(flow.getName());
    }

    @Test
    void listFlows_emptyPage_returnsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(Page.empty());

        // When
        Page<FlowResponse> result = flowService.listFlows(userId, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void listFlows_withVersions_enrichesWithVersionInfo() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        UUID flowId = UUID.randomUUID();
        Flow flow = TestDataFactory.createFlow("My Flow", userId);
        flow.setId(flowId);
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        FlowVersion published = TestDataFactory.createPublishedVersion(flowId, "1.0.0");
        FlowVersion latest = TestDataFactory.createFlowVersion(flowId, "2.0.0");

        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(List.of(flowId)))
                .thenReturn(List.of(latest, published));

        // When
        Page<FlowResponse> result = flowService.listFlows(userId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        FlowResponse resp = result.getContent().get(0);
        assertThat(resp.getLatestVersion()).isEqualTo("2.0.0");
        assertThat(resp.getPublishedVersion()).isEqualTo("1.0.0");
    }

    @Test
    void searchFlows_withQuery_returnsMatchingFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow("Integration Flow", userId);
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.searchByCreatedByAndQuery(userId, "Integration", pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        Page<FlowResponse> result = flowService.searchFlows(userId, "Integration", pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).contains("Integration");
    }

    @Test
    void searchFlows_emptyQuery_callsListFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow();
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        flowService.searchFlows(userId, "", pageable);

        // Then
        verify(flowRepository).findByCreatedByAndIsDeletedFalse(userId, pageable);
        verify(flowRepository, never()).searchByCreatedByAndQuery(any(), anyString(), any());
    }

    @Test
    void searchFlows_nullQuery_callsListFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(Page.empty());

        // When
        flowService.searchFlows(userId, null, pageable);

        // Then
        verify(flowRepository).findByCreatedByAndIsDeletedFalse(userId, pageable);
        verify(flowRepository, never()).searchByCreatedByAndQuery(any(), anyString(), any());
    }

    @Test
    void searchFlows_whitespaceQuery_callsListFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageable)).thenReturn(Page.empty());

        // When
        flowService.searchFlows(userId, "   ", pageable);

        // Then
        verify(flowRepository).findByCreatedByAndIsDeletedFalse(userId, pageable);
    }

    // ========== Get Flow Tests ==========

    @Test
    void getFlow_existingId_returnsFlow() {
        // Given
        Flow flow = TestDataFactory.createFlow();
        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flow.getId()))
                .thenReturn(List.of());

        // When
        FlowResponse result = flowService.getFlow(flow.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(flow.getName());
    }

    @Test
    void getFlow_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(flowRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> flowService.getFlow(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flow not found");
    }

    @Test
    void getFlow_withPublishedVersion_returnsPublishedVersionInfo() {
        // Given
        UUID flowId = UUID.randomUUID();
        Flow flow = TestDataFactory.createFlow("Test", UUID.randomUUID());
        flow.setId(flowId);

        FlowVersion published = TestDataFactory.createPublishedVersion(flowId, "1.0.0");

        when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(flow));
        when(flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId))
                .thenReturn(List.of(published));

        // When
        FlowResponse result = flowService.getFlow(flowId);

        // Then
        assertThat(result.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(result.getPublishedVersion()).isEqualTo("1.0.0");
    }

    // ========== Create Flow Tests ==========

    @Test
    void createFlow_validRequest_createsFlow() {
        // Given
        CreateFlowRequest request = TestDataFactory.createFlowRequest("New Flow");
        UUID userId = UUID.randomUUID();

        when(flowRepository.existsByNameAndIsDeletedFalse("New Flow")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> {
            Flow f = invocation.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        // When
        FlowResponse result = flowService.createFlow(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Flow");
        verify(flowRepository).save(any(Flow.class));
    }

    @Test
    void createFlow_duplicateName_throwsException() {
        // Given
        CreateFlowRequest request = TestDataFactory.createFlowRequest("Existing Flow");
        UUID userId = UUID.randomUUID();

        when(flowRepository.existsByNameAndIsDeletedFalse("Existing Flow")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> flowService.createFlow(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createFlow_setsCreatedByFromUserId() {
        // Given
        CreateFlowRequest request = TestDataFactory.createFlowRequest("Created Flow");
        UUID userId = UUID.randomUUID();

        when(flowRepository.existsByNameAndIsDeletedFalse("Created Flow")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(inv -> {
            Flow f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        // When
        flowService.createFlow(request, userId);

        // Then
        verify(flowRepository).save(argThat(flow -> userId.equals(flow.getCreatedBy())));
    }

    // ========== Update Flow Tests ==========

    @Test
    void updateFlow_existingFlow_updatesName() {
        // Given
        Flow flow = TestDataFactory.createFlow("Old Name", UUID.randomUUID());
        UpdateFlowRequest request = new UpdateFlowRequest();
        request.setName("New Name");

        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowRepository.existsByNameAndIsDeletedFalse("New Name")).thenReturn(false);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FlowResponse result = flowService.updateFlow(flow.getId(), request);

        // Then
        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void updateFlow_duplicateName_throwsException() {
        // Given
        Flow flow = TestDataFactory.createFlow("Original", UUID.randomUUID());
        UpdateFlowRequest request = new UpdateFlowRequest();
        request.setName("Existing Name");

        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowRepository.existsByNameAndIsDeletedFalse("Existing Name")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> flowService.updateFlow(flow.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateFlow_sameName_doesNotCheckDuplicate() {
        // Given
        Flow flow = TestDataFactory.createFlow("Same Name", UUID.randomUUID());
        UpdateFlowRequest request = new UpdateFlowRequest();
        request.setName("Same Name");

        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FlowResponse result = flowService.updateFlow(flow.getId(), request);

        // Then
        verify(flowRepository, never()).existsByNameAndIsDeletedFalse(anyString());
        assertThat(result.getName()).isEqualTo("Same Name");
    }

    @Test
    void updateFlow_nullName_updatesDescriptionOnly() {
        // Given
        Flow flow = TestDataFactory.createFlow("Keep Name", UUID.randomUUID());
        UpdateFlowRequest request = new UpdateFlowRequest();
        request.setDescription("New Description");

        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FlowResponse result = flowService.updateFlow(flow.getId(), request);

        // Then
        assertThat(result.getName()).isEqualTo("Keep Name");
        assertThat(result.getDescription()).isEqualTo("New Description");
    }

    @Test
    void updateFlow_nonExisting_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UpdateFlowRequest request = new UpdateFlowRequest();
        request.setName("X");
        when(flowRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> flowService.updateFlow(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Delete Flow Tests ==========

    @Test
    void deleteFlow_existingFlow_softDeletes() {
        // Given
        Flow flow = TestDataFactory.createFlow();
        when(flowRepository.findByIdAndIsDeletedFalse(flow.getId())).thenReturn(Optional.of(flow));
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        flowService.deleteFlow(flow.getId());

        // Then
        verify(flowRepository).save(argThat(f -> f.getIsDeleted()));
    }

    @Test
    void deleteFlow_nonExistingFlow_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(flowRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> flowService.deleteFlow(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Version Tests ==========

    @Test
    void listVersions_existingFlow_returnsVersions() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion v1 = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        FlowVersion v2 = TestDataFactory.createFlowVersion(flowId, "1.0.1");

        when(flowRepository.existsById(flowId)).thenReturn(true);
        when(flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId))
                .thenReturn(List.of(v2, v1));

        // When
        List<FlowVersionResponse> result = flowService.listVersions(flowId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.1");
    }

    @Test
    void listVersions_nonExistingFlow_throwsException() {
        // Given
        UUID flowId = UUID.randomUUID();
        when(flowRepository.existsById(flowId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> flowService.listVersions(flowId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getVersion_existing_returnsVersion() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion v = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(v));

        // When
        FlowVersionResponse result = flowService.getVersion(flowId, "1.0.0");

        // Then
        assertThat(result.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void getVersion_nonExisting_throwsException() {
        // Given
        UUID flowId = UUID.randomUUID();
        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "9.9.9"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> flowService.getVersion(flowId, "9.9.9"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPublishedVersion_existing_returnsPublished() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion v = TestDataFactory.createPublishedVersion(flowId, "1.0.0");
        when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                .thenReturn(Optional.of(v));

        // When
        FlowVersionResponse result = flowService.getPublishedVersion(flowId);

        // Then
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getStatus()).isEqualTo("published");
    }

    @Test
    void getPublishedVersion_noPublished_throwsException() {
        // Given
        UUID flowId = UUID.randomUUID();
        when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> flowService.getPublishedVersion(flowId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No published version");
    }

    @Test
    void saveVersion_newVersion_createsVersion() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SaveVersionRequest request = TestDataFactory.createSaveVersionRequest("1.0.0");

        when(flowRepository.existsById(flowId)).thenReturn(true);
        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.empty());
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(invocation -> {
            FlowVersion v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        // When
        FlowVersionResponse result = flowService.saveVersion(flowId, request, userId);

        // Then
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getStatus()).isEqualTo("draft");
    }

    @Test
    void saveVersion_existingDraftVersion_updatesDefinition() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FlowVersion existing = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        Map<String, Object> newDef = TestDataFactory.createComplexFlowDefinition();

        SaveVersionRequest request = new SaveVersionRequest();
        request.setVersion("1.0.0");
        request.setDefinition(newDef);

        when(flowRepository.existsById(flowId)).thenReturn(true);
        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(existing));
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        FlowVersionResponse result = flowService.saveVersion(flowId, request, userId);

        // Then
        verify(flowVersionRepository).save(argThat(v -> v.getDefinition() == newDef));
    }

    @Test
    void saveVersion_existingPublishedVersion_throwsException() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FlowVersion published = TestDataFactory.createPublishedVersion(flowId, "1.0.0");

        SaveVersionRequest request = new SaveVersionRequest();
        request.setVersion("1.0.0");
        request.setDefinition(TestDataFactory.createSimpleFlowDefinition());

        when(flowRepository.existsById(flowId)).thenReturn(true);
        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(published));

        // When/Then
        assertThatThrownBy(() -> flowService.saveVersion(flowId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify a non-draft version");
    }

    @Test
    void saveVersion_nonExistingFlow_throwsException() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SaveVersionRequest request = TestDataFactory.createSaveVersionRequest("1.0.0");
        when(flowRepository.existsById(flowId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> flowService.saveVersion(flowId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publishVersion_draftVersion_publishes() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                .thenReturn(Optional.empty());
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FlowVersionResponse result = flowService.publishVersion(flowId, "1.0.0");

        // Then
        assertThat(result.getStatus()).isEqualTo("published");
    }

    @Test
    void publishVersion_deprecatesOldVersion() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion newVersion = TestDataFactory.createFlowVersion(flowId, "2.0.0");
        FlowVersion oldVersion = TestDataFactory.createPublishedVersion(flowId, "1.0.0");

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "2.0.0"))
                .thenReturn(Optional.of(newVersion));
        when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                .thenReturn(Optional.of(oldVersion));
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        flowService.publishVersion(flowId, "2.0.0");

        // Then
        verify(flowVersionRepository, times(2)).save(any(FlowVersion.class));
    }

    @Test
    void publishVersion_alreadyPublished_returnsWithoutChange() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion published = TestDataFactory.createPublishedVersion(flowId, "1.0.0");

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(published));

        // When
        FlowVersionResponse result = flowService.publishVersion(flowId, "1.0.0");

        // Then
        assertThat(result.getStatus()).isEqualTo("published");
        verify(flowVersionRepository, never()).save(any());
    }

    // ========== Validation Tests ==========

    @Test
    void validateFlow_validDefinition_returnsValid() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");

        DagParser.ParseResult parseResult = new DagParser.ParseResult();
        parseResult.setValid(true);
        parseResult.setErrors(new ArrayList<>());
        parseResult.setWarnings(new ArrayList<>());
        parseResult.setEntryPoints(List.of("node1"));
        parseResult.setExitPoints(List.of("node2"));
        parseResult.setExecutionOrder(List.of("node1", "node2"));
        parseResult.setDependencies(new HashMap<>());

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(dagParser.parse(any())).thenReturn(parseResult);

        // When
        FlowValidationResponse result = flowService.validateFlow(flowId, "1.0.0");

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getEntryPoints()).contains("node1");
    }

    @Test
    void validateFlow_invalidDefinition_returnsErrors() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");

        DagParser.ParseResult parseResult = new DagParser.ParseResult();
        parseResult.setValid(false);
        parseResult.setErrors(List.of("Cycle detected"));
        parseResult.setWarnings(new ArrayList<>());
        parseResult.setEntryPoints(new ArrayList<>());
        parseResult.setExitPoints(new ArrayList<>());
        parseResult.setExecutionOrder(new ArrayList<>());
        parseResult.setDependencies(new HashMap<>());

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(dagParser.parse(any())).thenReturn(parseResult);

        // When
        FlowValidationResponse result = flowService.validateFlow(flowId, "1.0.0");

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("Cycle detected");
    }

    @Test
    void validateDefinition_directDefinition_returnsResult() {
        // Given
        Map<String, Object> definition = TestDataFactory.createSimpleFlowDefinition();
        DagParser.ParseResult parseResult = new DagParser.ParseResult();
        parseResult.setValid(true);
        parseResult.setErrors(new ArrayList<>());
        parseResult.setWarnings(List.of("Unused node"));
        parseResult.setEntryPoints(List.of("node1"));
        parseResult.setExitPoints(List.of("node2"));
        parseResult.setExecutionOrder(List.of("node1", "node2"));
        parseResult.setDependencies(new HashMap<>());

        when(dagParser.parse(definition)).thenReturn(parseResult);

        // When
        FlowValidationResponse result = flowService.validateDefinition(definition);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).contains("Unused node");
    }

    // ========== Pinned Data Tests ==========

    @Test
    void getPinnedData_hasPinnedData_returnsData() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(Map.of("node1", Map.of("key", "value")));

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        Map<String, Object> result = flowService.getPinnedData(flowId, "1.0.0");

        // Then
        assertThat(result).containsKey("node1");
    }

    @Test
    void getPinnedData_noPinnedData_returnsEmptyMap() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(null);

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        Map<String, Object> result = flowService.getPinnedData(flowId, "1.0.0");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void pinNodeData_validRequest_pinsData() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(new HashMap<>());

        PinDataRequest request = new PinDataRequest();
        request.setNodeId("node1");
        request.setData(Map.of("output", "test-value"));

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        flowService.pinNodeData(flowId, "1.0.0", request);

        // Then
        verify(flowVersionRepository).save(argThat(v ->
            v.getPinnedData().containsKey("node1")
        ));
    }

    @Test
    void pinNodeData_immutablePinnedData_createsMutableCopy() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(Map.of("existing", Map.of("data", "old")));

        PinDataRequest request = new PinDataRequest();
        request.setNodeId("newNode");
        request.setData(Map.of("output", "new-value"));

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        // When - should not throw even with immutable initial map
        flowService.pinNodeData(flowId, "1.0.0", request);

        // Then
        verify(flowVersionRepository).save(any(FlowVersion.class));
    }

    @Test
    void unpinNodeData_existingPin_removesPin() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        Map<String, Object> pinnedData = new HashMap<>();
        pinnedData.put("node1", Map.of("data", "value"));
        pinnedData.put("node2", Map.of("data", "value2"));
        version.setPinnedData(pinnedData);

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(flowVersionRepository.save(any(FlowVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        flowService.unpinNodeData(flowId, "1.0.0", "node1");

        // Then
        verify(flowVersionRepository).save(argThat(v ->
            !v.getPinnedData().containsKey("node1") && v.getPinnedData().containsKey("node2")
        ));
    }

    @Test
    void unpinNodeData_noPinnedData_doesNothing() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(null);

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        flowService.unpinNodeData(flowId, "1.0.0", "node1");

        // Then
        verify(flowVersionRepository, never()).save(any());
    }

    @Test
    void unpinNodeData_emptyPinnedData_doesNothing() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setPinnedData(Map.of());

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        flowService.unpinNodeData(flowId, "1.0.0", "node1");

        // Then
        verify(flowVersionRepository, never()).save(any());
    }

    // ========== Upstream Outputs Tests ==========

    @Test
    void getUpstreamOutputs_noDefinition_returnsEmpty() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setDefinition(null);

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        List<UpstreamNodeOutput> result = flowService.getUpstreamOutputs(flowId, "1.0.0", "node2");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getUpstreamOutputs_noNodesOrEdges_returnsEmpty() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        version.setDefinition(Map.of());

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));

        // When
        List<UpstreamNodeOutput> result = flowService.getUpstreamOutputs(flowId, "1.0.0", "node2");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getUpstreamOutputs_withUpstreamNode_returnsNodeOutput() {
        // Given
        UUID flowId = UUID.randomUUID();
        FlowVersion version = TestDataFactory.createFlowVersion(flowId, "1.0.0");
        // definition already has node1 -> node2

        NodeHandler mockHandler = mock(NodeHandler.class);
        when(mockHandler.getInterfaceDefinition()).thenReturn(Map.of());

        when(flowVersionRepository.findByFlowIdAndVersion(flowId, "1.0.0"))
                .thenReturn(Optional.of(version));
        when(nodeHandlerRegistry.findHandler(anyString())).thenReturn(Optional.of(mockHandler));

        // When
        List<UpstreamNodeOutput> result = flowService.getUpstreamOutputs(flowId, "1.0.0", "node2");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeId()).isEqualTo("node1");
    }
}
