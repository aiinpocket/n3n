package com.aiinpocket.n3n.skill.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.dto.CreateSkillRequest;
import com.aiinpocket.n3n.skill.dto.SkillDto;
import com.aiinpocket.n3n.skill.dto.UpdateSkillRequest;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SkillServiceTest extends BaseServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillExecutor skillExecutor;

    @Mock
    private BuiltinSkillRegistry builtinSkillRegistry;

    @InjectMocks
    private SkillService skillService;

    // ========== Get Accessible Skills Tests ==========

    @Test
    void getAccessibleSkills_validUserId_returnsSkills() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill skill = createTestSkill("test_skill", false);

        when(skillRepository.findEnabledAccessibleSkills(userId))
                .thenReturn(List.of(skill));

        // When
        List<SkillDto> result = skillService.getAccessibleSkills(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test_skill");
    }

    @Test
    void getAccessibleSkills_noSkills_returnsEmptyList() {
        // Given
        UUID userId = UUID.randomUUID();
        when(skillRepository.findEnabledAccessibleSkills(userId)).thenReturn(List.of());

        // When
        List<SkillDto> result = skillService.getAccessibleSkills(userId);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Get Builtin Skills Tests ==========

    @Test
    void getBuiltinSkills_returnsBuiltinSkills() {
        // Given
        Skill builtin = createTestSkill("builtin_skill", true);
        when(skillRepository.findByIsBuiltinTrue()).thenReturn(List.of(builtin));

        // When
        List<SkillDto> result = skillService.getBuiltinSkills();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsBuiltin()).isTrue();
    }

    // ========== Get By Category Tests ==========

    @Test
    void getSkillsByCategory_validCategory_returnsMatchingSkills() {
        // Given
        Skill skill = createTestSkill("http_skill", false);
        skill.setCategory("network");

        when(skillRepository.findByCategory("network")).thenReturn(List.of(skill));

        // When
        List<SkillDto> result = skillService.getSkillsByCategory("network");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("network");
    }

    // ========== Get Skill Tests ==========

    @Test
    void getSkill_existingId_returnsSkill() {
        // Given
        Skill skill = createTestSkill("test_skill", false);
        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));

        // When
        Optional<SkillDto> result = skillService.getSkill(skill.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("test_skill");
    }

    @Test
    void getSkill_nonExistingId_returnsEmpty() {
        // Given
        UUID id = UUID.randomUUID();
        when(skillRepository.findById(id)).thenReturn(Optional.empty());

        // When
        Optional<SkillDto> result = skillService.getSkill(id);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getSkillByName_existingName_returnsSkill() {
        // Given
        Skill skill = createTestSkill("test_skill", false);
        when(skillRepository.findByName("test_skill")).thenReturn(Optional.of(skill));

        // When
        Optional<SkillDto> result = skillService.getSkillByName("test_skill");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("test_skill");
    }

    // ========== Create Skill Tests ==========

    @Test
    void createSkill_validRequest_createsSkill() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateSkillRequest request = createSkillRequest("new_skill");

        when(skillRepository.existsByName("new_skill")).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            s.setCreatedAt(Instant.now());
            return s;
        });

        // When
        SkillDto result = skillService.createSkill(request, ownerId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("new_skill");
        assertThat(result.getIsBuiltin()).isFalse();
        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    void createSkill_duplicateName_throwsException() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateSkillRequest request = createSkillRequest("existing_skill");

        when(skillRepository.existsByName("existing_skill")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> skillService.createSkill(request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createSkill_noVisibility_defaultsToPrivate() {
        // Given
        UUID ownerId = UUID.randomUUID();
        CreateSkillRequest request = createSkillRequest("new_skill");
        request.setVisibility(null);

        when(skillRepository.existsByName("new_skill")).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // When
        skillService.createSkill(request, ownerId);

        // Then
        verify(skillRepository).save(argThat(s -> "private".equals(s.getVisibility())));
    }

    // ========== Update Skill Tests ==========

    @Test
    void updateSkill_ownedSkill_updatesSuccessfully() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill skill = createTestSkill("my_skill", false);
        skill.setOwnerId(userId);

        UpdateSkillRequest request = new UpdateSkillRequest();
        request.setDisplayName("Updated Display");
        request.setDescription("Updated description");

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SkillDto result = skillService.updateSkill(skill.getId(), request, userId);

        // Then
        assertThat(result.getDisplayName()).isEqualTo("Updated Display");
        assertThat(result.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateSkill_builtinSkill_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill builtin = createTestSkill("builtin_skill", true);

        UpdateSkillRequest request = new UpdateSkillRequest();
        request.setDisplayName("Updated");

        when(skillRepository.findById(builtin.getId())).thenReturn(Optional.of(builtin));

        // When/Then
        assertThatThrownBy(() -> skillService.updateSkill(builtin.getId(), request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify built-in skill");
    }

    @Test
    void updateSkill_notOwner_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Skill skill = createTestSkill("other_skill", false);
        skill.setOwnerId(otherUserId);

        UpdateSkillRequest request = new UpdateSkillRequest();
        request.setDisplayName("Hacked");

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));

        // When/Then
        assertThatThrownBy(() -> skillService.updateSkill(skill.getId(), request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void updateSkill_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateSkillRequest request = new UpdateSkillRequest();

        when(skillRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> skillService.updateSkill(id, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void updateSkill_partialUpdate_onlyUpdatesProvidedFields() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill skill = createTestSkill("my_skill", false);
        skill.setOwnerId(userId);
        skill.setDisplayName("Original Display");
        skill.setDescription("Original Description");
        skill.setCategory("original");

        UpdateSkillRequest request = new UpdateSkillRequest();
        request.setCategory("updated");
        // displayName and description are null => should not change

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SkillDto result = skillService.updateSkill(skill.getId(), request, userId);

        // Then
        assertThat(result.getCategory()).isEqualTo("updated");
        assertThat(result.getDisplayName()).isEqualTo("Original Display");
        assertThat(result.getDescription()).isEqualTo("Original Description");
    }

    // ========== Delete Skill Tests ==========

    @Test
    void deleteSkill_ownedCustomSkill_deletesSuccessfully() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill skill = createTestSkill("my_skill", false);
        skill.setOwnerId(userId);

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));

        // When
        skillService.deleteSkill(skill.getId(), userId);

        // Then
        verify(skillRepository).delete(skill);
    }

    @Test
    void deleteSkill_builtinSkill_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill builtin = createTestSkill("builtin_skill", true);

        when(skillRepository.findById(builtin.getId())).thenReturn(Optional.of(builtin));

        // When/Then
        assertThatThrownBy(() -> skillService.deleteSkill(builtin.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete built-in skill");
    }

    @Test
    void deleteSkill_notOwner_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        Skill skill = createTestSkill("other_skill", false);
        skill.setOwnerId(UUID.randomUUID());

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));

        // When/Then
        assertThatThrownBy(() -> skillService.deleteSkill(skill.getId(), userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not authorized");
    }

    // ========== Execute Skill Tests ==========

    @Test
    void executeSkill_delegatesToSkillExecutor() {
        // Given
        UUID skillId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> input = Map.of("key", "value");
        SkillResult expectedResult = SkillResult.success(Map.of("output", "data"));

        when(skillExecutor.execute(skillId, input, null, null, userId)).thenReturn(expectedResult);

        // When
        SkillResult result = skillService.executeSkill(skillId, input, userId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("output", "data");
        verify(skillExecutor).execute(skillId, input, null, null, userId);
    }

    @Test
    void executeSkillByName_delegatesToSkillExecutor() {
        // Given
        String skillName = "test_skill";
        UUID userId = UUID.randomUUID();
        Map<String, Object> input = Map.of("key", "value");
        SkillResult expectedResult = SkillResult.success(Map.of("result", "ok"));

        when(skillExecutor.executeByName(skillName, input, null, null, userId)).thenReturn(expectedResult);

        // When
        SkillResult result = skillService.executeSkillByName(skillName, input, userId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(skillExecutor).executeByName(skillName, input, null, null, userId);
    }

    // ========== Get Categories Tests ==========

    @Test
    void getCategories_returnsCategoryList() {
        // Given
        when(skillRepository.findAllCategories()).thenReturn(List.of("network", "data", "ai"));

        // When
        List<String> result = skillService.getCategories();

        // Then
        assertThat(result).containsExactly("network", "data", "ai");
    }

    // ========== Get Skill Summaries Tests ==========

    @Test
    void getSkillSummariesForAI_returnsFormattedSummaries() {
        // Given
        Skill skill = createTestSkill("test_skill", false);
        skill.setDescription("A test skill");

        when(skillRepository.findByIsEnabledTrue()).thenReturn(List.of(skill));

        // When
        List<Map<String, Object>> result = skillService.getSkillSummariesForAI();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "test_skill");
        assertThat(result.get(0)).containsEntry("displayName", "Test Skill");
    }

    // ========== Helper Methods ==========

    private Skill createTestSkill(String name, boolean isBuiltin) {
        return Skill.builder()
                .id(UUID.randomUUID())
                .name(name)
                .displayName("Test Skill")
                .description("Test skill description")
                .category("test")
                .isBuiltin(isBuiltin)
                .isEnabled(true)
                .implementationType("java")
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "object"))
                .ownerId(UUID.randomUUID())
                .visibility("private")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CreateSkillRequest createSkillRequest(String name) {
        CreateSkillRequest request = new CreateSkillRequest();
        request.setName(name);
        request.setDisplayName("New Skill");
        request.setDescription("New skill description");
        request.setCategory("test");
        request.setImplementationType("http");
        request.setInputSchema(Map.of("type", "object"));
        request.setOutputSchema(Map.of("type", "object"));
        request.setVisibility("private");
        return request;
    }
}
