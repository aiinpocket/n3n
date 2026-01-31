package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.base.BaseRepositoryTest;
import com.aiinpocket.n3n.flow.entity.Flow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private FlowRepository flowRepository;

    @Test
    void findByIsDeletedFalse_activeFlows_returnsOnlyActive() {
        // Given
        UUID userId = UUID.randomUUID();
        Flow activeFlow = createFlow("Active Flow", userId, false);
        Flow deletedFlow = createFlow("Deleted Flow", userId, true);
        entityManager.persist(activeFlow);
        entityManager.persist(deletedFlow);
        entityManager.flush();

        // When
        Page<Flow> result = flowRepository.findByIsDeletedFalse(PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Active Flow");
    }

    @Test
    void findByIdAndIsDeletedFalse_activeFlow_returnsFlow() {
        // Given
        Flow flow = createFlow("Test Flow", UUID.randomUUID(), false);
        entityManager.persist(flow);
        flushAndClear();

        // When
        Optional<Flow> result = flowRepository.findByIdAndIsDeletedFalse(flow.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Flow");
    }

    @Test
    void findByIdAndIsDeletedFalse_deletedFlow_returnsEmpty() {
        // Given
        Flow flow = createFlow("Deleted Flow", UUID.randomUUID(), true);
        entityManager.persist(flow);
        flushAndClear();

        // When
        Optional<Flow> result = flowRepository.findByIdAndIsDeletedFalse(flow.getId());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void existsByNameAndIsDeletedFalse_existingName_returnsTrue() {
        // Given
        Flow flow = createFlow("Unique Name", UUID.randomUUID(), false);
        entityManager.persist(flow);
        flushAndClear();

        // When
        boolean exists = flowRepository.existsByNameAndIsDeletedFalse("Unique Name");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByNameAndIsDeletedFalse_deletedFlowName_returnsFalse() {
        // Given
        Flow flow = createFlow("Deleted Name", UUID.randomUUID(), true);
        entityManager.persist(flow);
        flushAndClear();

        // When
        boolean exists = flowRepository.existsByNameAndIsDeletedFalse("Deleted Name");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void existsByNameAndIsDeletedFalse_nonExistingName_returnsFalse() {
        // When
        boolean exists = flowRepository.existsByNameAndIsDeletedFalse("Non-existing");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void searchFlows_matchingName_returnsResults() {
        // Given
        UUID userId = UUID.randomUUID();
        Flow flow1 = createFlow("API Integration", userId, false);
        Flow flow2 = createFlow("Data Processing", userId, false);
        Flow flow3 = createFlow("Integration Test", userId, false);
        entityManager.persist(flow1);
        entityManager.persist(flow2);
        entityManager.persist(flow3);
        entityManager.flush();

        // When
        Page<Flow> result = flowRepository.searchFlows("Integration", PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Flow::getName)
                .containsExactlyInAnyOrder("API Integration", "Integration Test");
    }

    @Test
    void searchFlows_matchingDescription_returnsResults() {
        // Given
        UUID userId = UUID.randomUUID();
        Flow flow = createFlow("My Flow", userId, false);
        flow.setDescription("This flow handles payments");
        entityManager.persist(flow);
        flushAndClear();

        // When
        Page<Flow> result = flowRepository.searchFlows("payments", PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDescription()).contains("payments");
    }

    @Test
    void searchFlows_caseInsensitive_returnsResults() {
        // Given
        Flow flow = createFlow("EMAIL Notification", UUID.randomUUID(), false);
        entityManager.persist(flow);
        flushAndClear();

        // When
        Page<Flow> result = flowRepository.searchFlows("email", PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchFlows_excludesDeletedFlows() {
        // Given
        UUID userId = UUID.randomUUID();
        Flow activeFlow = createFlow("Active Search Flow", userId, false);
        Flow deletedFlow = createFlow("Deleted Search Flow", userId, true);
        entityManager.persist(activeFlow);
        entityManager.persist(deletedFlow);
        entityManager.flush();

        // When
        Page<Flow> result = flowRepository.searchFlows("Search Flow", PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Active Search Flow");
    }

    @Test
    void findByCreatedByAndIsDeletedFalse_returnsUserFlows() {
        // Given
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        Flow flow1 = createFlow("User1 Flow 1", user1Id, false);
        Flow flow2 = createFlow("User1 Flow 2", user1Id, false);
        Flow flow3 = createFlow("User2 Flow", user2Id, false);
        entityManager.persist(flow1);
        entityManager.persist(flow2);
        entityManager.persist(flow3);
        entityManager.flush();

        // When
        Page<Flow> result = flowRepository.findByCreatedByAndIsDeletedFalse(user1Id, PageRequest.of(0, 10));

        // Then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Flow::getName)
                .containsExactlyInAnyOrder("User1 Flow 1", "User1 Flow 2");
    }

    // ========== Helper Methods ==========

    private Flow createFlow(String name, UUID createdBy, boolean isDeleted) {
        return Flow.builder()
                .name(name)
                .description("Test description for " + name)
                .createdBy(createdBy)
                .visibility("private")
                .isDeleted(isDeleted)
                .build();
    }
}
