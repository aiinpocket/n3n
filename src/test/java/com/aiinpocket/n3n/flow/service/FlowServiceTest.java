package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.*;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FlowServiceTest extends BaseServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private DagParser dagParser;

    @InjectMocks
    private FlowService flowService;

    // ========== List/Search Tests ==========

    @Test
    void listFlows_returnsPagedFlows() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow();
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.findByIsDeletedFalse(pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        Page<FlowResponse> result = flowService.listFlows(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo(flow.getName());
    }

    @Test
    void searchFlows_withQuery_returnsMatchingFlows() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow("Integration Flow", UUID.randomUUID());
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.searchFlows("Integration", pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        Page<FlowResponse> result = flowService.searchFlows("Integration", pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).contains("Integration");
    }

    @Test
    void searchFlows_emptyQuery_callsListFlows() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Flow flow = TestDataFactory.createFlow();
        Page<Flow> flowPage = new PageImpl<>(List.of(flow));

        when(flowRepository.findByIsDeletedFalse(pageable)).thenReturn(flowPage);
        when(flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(any()))
                .thenReturn(List.of());

        // When
        flowService.searchFlows("", pageable);

        // Then
        verify(flowRepository).findByIsDeletedFalse(pageable);
        verify(flowRepository, never()).searchFlows(anyString(), any());
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
}
