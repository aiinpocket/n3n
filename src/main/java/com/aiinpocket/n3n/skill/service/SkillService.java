package com.aiinpocket.n3n.skill.service;

import com.aiinpocket.n3n.common.constant.Status;
import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.dto.CreateSkillRequest;
import com.aiinpocket.n3n.skill.dto.SkillDto;
import com.aiinpocket.n3n.skill.dto.UpdateSkillRequest;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillExecutor skillExecutor;
    private final BuiltinSkillRegistry builtinSkillRegistry;

    /**
     * Get all accessible skills for a user.
     */
    public List<SkillDto> getAccessibleSkills(UUID userId) {
        return skillRepository.findEnabledAccessibleSkills(userId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all built-in skills.
     */
    public List<SkillDto> getBuiltinSkills() {
        return skillRepository.findByIsBuiltinTrue()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get skills by category.
     */
    public List<SkillDto> getSkillsByCategory(String category) {
        return skillRepository.findByCategory(category)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all available categories.
     */
    public List<String> getCategories() {
        return skillRepository.findAllCategories();
    }

    /**
     * Get skill by ID.
     */
    public Optional<SkillDto> getSkill(UUID id) {
        return skillRepository.findById(id).map(this::toDto);
    }

    /**
     * Get skill by name.
     */
    public Optional<SkillDto> getSkillByName(String name) {
        return skillRepository.findByName(name).map(this::toDto);
    }

    /**
     * Create a custom skill.
     */
    @Transactional
    public SkillDto createSkill(CreateSkillRequest request, UUID ownerId) {
        if (skillRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Skill with name already exists: " + request.getName());
        }

        Skill skill = Skill.builder()
            .name(request.getName())
            .displayName(request.getDisplayName())
            .description(request.getDescription())
            .category(request.getCategory())
            .icon(request.getIcon())
            .isBuiltin(false)
            .isEnabled(true)
            .implementationType(request.getImplementationType())
            .implementationConfig(request.getImplementationConfig())
            .inputSchema(request.getInputSchema())
            .outputSchema(request.getOutputSchema())
            .ownerId(ownerId)
            .visibility(request.getVisibility() != null ? request.getVisibility() : Status.Visibility.PRIVATE)
            .build();

        skill = skillRepository.save(skill);
        log.info("Created custom skill: {}", skill.getName());

        return toDto(skill);
    }

    /**
     * Update a custom skill.
     */
    @Transactional
    public SkillDto updateSkill(UUID id, UpdateSkillRequest request, UUID userId) {
        Skill skill = skillRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));

        if (skill.getIsBuiltin()) {
            throw new IllegalArgumentException("Cannot modify built-in skill");
        }

        if (!skill.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to modify this skill");
        }

        if (request.getDisplayName() != null) {
            skill.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            skill.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            skill.setCategory(request.getCategory());
        }
        if (request.getIcon() != null) {
            skill.setIcon(request.getIcon());
        }
        if (request.getIsEnabled() != null) {
            skill.setIsEnabled(request.getIsEnabled());
        }
        if (request.getImplementationConfig() != null) {
            skill.setImplementationConfig(request.getImplementationConfig());
        }
        if (request.getInputSchema() != null) {
            skill.setInputSchema(request.getInputSchema());
        }
        if (request.getOutputSchema() != null) {
            skill.setOutputSchema(request.getOutputSchema());
        }
        if (request.getVisibility() != null) {
            skill.setVisibility(request.getVisibility());
        }

        skill = skillRepository.save(skill);
        log.info("Updated skill: {}", skill.getName());

        return toDto(skill);
    }

    /**
     * Delete a custom skill.
     */
    @Transactional
    public void deleteSkill(UUID id, UUID userId) {
        Skill skill = skillRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));

        if (skill.getIsBuiltin()) {
            throw new IllegalArgumentException("Cannot delete built-in skill");
        }

        if (!skill.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this skill");
        }

        skillRepository.delete(skill);
        log.info("Deleted skill: {}", skill.getName());
    }

    /**
     * Execute a skill directly (for testing).
     */
    public SkillResult executeSkill(UUID skillId, Map<String, Object> input, UUID userId) {
        return skillExecutor.execute(skillId, input, null, null, userId);
    }

    /**
     * Execute a skill by name.
     */
    public SkillResult executeSkillByName(String skillName, Map<String, Object> input, UUID userId) {
        return skillExecutor.executeByName(skillName, input, null, null, userId);
    }

    /**
     * Get skill summary for AI assistant.
     */
    public List<Map<String, Object>> getSkillSummariesForAI() {
        return skillRepository.findByIsEnabledTrue()
            .stream()
            .map(skill -> Map.<String, Object>of(
                "name", skill.getName(),
                "displayName", skill.getDisplayName(),
                "description", skill.getDescription() != null ? skill.getDescription() : "",
                "category", skill.getCategory(),
                "inputSchema", skill.getInputSchema()
            ))
            .collect(Collectors.toList());
    }

    private SkillDto toDto(Skill skill) {
        return SkillDto.builder()
            .id(skill.getId())
            .name(skill.getName())
            .displayName(skill.getDisplayName())
            .description(skill.getDescription())
            .category(skill.getCategory())
            .icon(skill.getIcon())
            .isBuiltin(skill.getIsBuiltin())
            .isEnabled(skill.getIsEnabled())
            .implementationType(skill.getImplementationType())
            .inputSchema(skill.getInputSchema())
            .outputSchema(skill.getOutputSchema())
            .visibility(skill.getVisibility())
            .createdAt(skill.getCreatedAt())
            .updatedAt(skill.getUpdatedAt())
            .build();
    }
}
