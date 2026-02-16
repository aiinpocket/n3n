package com.aiinpocket.n3n.skill.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.repository.SkillExecutionRepository;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SkillExecutorTest extends BaseServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillExecutionRepository skillExecutionRepository;

    @Mock
    private BuiltinSkillRegistry builtinSkillRegistry;

    @Mock
    private WebClient.Builder webClientBuilder;

    @InjectMocks
    private SkillExecutor skillExecutor;

    // ========== Access Control Tests ==========

    @Test
    void execute_builtinSkill_allowsAnyUser() {
        UUID skillId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Skill skill = createSkill(skillId, true, UUID.randomUUID(), "private");

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(builtinSkillRegistry.executeSkill(eq("test_skill"), any())).thenReturn(SkillResult.success(Map.of()));

        SkillResult result = skillExecutor.execute(skillId, Map.of(), null, null, userId);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_ownPrivateSkill_allowed() {
        UUID skillId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Skill skill = createSkill(skillId, false, userId, "private");
        skill.setImplementationType("java");

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

        SkillResult result = skillExecutor.execute(skillId, Map.of(), null, null, userId);

        // Should not return ACCESS_DENIED (may fail for other reasons like "Custom Java skills not yet supported")
        assertThat(result.getErrorCode()).isNotEqualTo("ACCESS_DENIED");
    }

    @Test
    void execute_otherUserPrivateSkill_denied() {
        UUID skillId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        Skill skill = createSkill(skillId, false, ownerId, "private");

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

        SkillResult result = skillExecutor.execute(skillId, Map.of(), null, null, attackerId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void execute_publicSkill_allowsAnyUser() {
        UUID skillId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Skill skill = createSkill(skillId, false, ownerId, "public");
        skill.setImplementationType("java");

        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

        SkillResult result = skillExecutor.execute(skillId, Map.of(), null, null, otherUserId);

        assertThat(result.getErrorCode()).isNotEqualTo("ACCESS_DENIED");
    }

    @Test
    void executeByName_otherUserPrivateSkill_denied() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        Skill skill = createSkill(UUID.randomUUID(), false, ownerId, "private");

        when(skillRepository.findByName("secret_skill")).thenReturn(Optional.of(skill));

        SkillResult result = skillExecutor.executeByName("secret_skill", Map.of(), null, null, attackerId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void executeByName_publicSkill_allowed() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Skill skill = createSkill(UUID.randomUUID(), false, ownerId, "public");
        skill.setImplementationType("java");

        when(skillRepository.findByName("public_skill")).thenReturn(Optional.of(skill));

        SkillResult result = skillExecutor.executeByName("public_skill", Map.of(), null, null, otherUserId);

        assertThat(result.getErrorCode()).isNotEqualTo("ACCESS_DENIED");
    }

    @Test
    void execute_skillNotFound_returnsFailure() {
        UUID skillId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(skillRepository.findById(skillId)).thenReturn(Optional.empty());

        SkillResult result = skillExecutor.execute(skillId, Map.of(), null, null, userId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("SKILL_NOT_FOUND");
    }

    // ========== Helper ==========

    private Skill createSkill(UUID id, boolean isBuiltin, UUID ownerId, String visibility) {
        return Skill.builder()
                .id(id)
                .name("test_skill")
                .displayName("Test Skill")
                .category("test")
                .isBuiltin(isBuiltin)
                .isEnabled(true)
                .implementationType("java")
                .inputSchema(Map.of("type", "object"))
                .ownerId(ownerId)
                .visibility(visibility)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
