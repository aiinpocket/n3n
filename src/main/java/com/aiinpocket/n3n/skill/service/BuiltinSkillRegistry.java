package com.aiinpocket.n3n.skill.service;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for built-in skills.
 * Automatically registers all BuiltinSkill implementations and syncs with database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuiltinSkillRegistry {

    private final List<BuiltinSkill> builtinSkills;
    private final SkillRepository skillRepository;

    private final Map<String, BuiltinSkill> skillMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Registering {} built-in skills", builtinSkills.size());

        for (BuiltinSkill skill : builtinSkills) {
            registerSkill(skill);
        }

        syncWithDatabase();
    }

    private void registerSkill(BuiltinSkill skill) {
        String name = skill.getName();
        if (skillMap.containsKey(name)) {
            log.warn("Duplicate skill name: {}, overwriting", name);
        }
        skillMap.put(name, skill);
        log.debug("Registered built-in skill: {}", name);
    }

    /**
     * Sync built-in skills with database.
     * Creates or updates skill records for all registered built-in skills.
     */
    private void syncWithDatabase() {
        for (BuiltinSkill skill : skillMap.values()) {
            try {
                Optional<Skill> existing = skillRepository.findByName(skill.getName());

                if (existing.isPresent()) {
                    // Update existing skill
                    Skill entity = existing.get();
                    entity.setDisplayName(skill.getDisplayName());
                    entity.setDescription(skill.getDescription());
                    entity.setCategory(skill.getCategory());
                    entity.setIcon(skill.getIcon());
                    entity.setInputSchema(skill.getInputSchema());
                    entity.setOutputSchema(skill.getOutputSchema());
                    skillRepository.save(entity);
                    log.debug("Updated built-in skill in database: {}", skill.getName());
                } else {
                    // Create new skill
                    Skill entity = Skill.builder()
                        .name(skill.getName())
                        .displayName(skill.getDisplayName())
                        .description(skill.getDescription())
                        .category(skill.getCategory())
                        .icon(skill.getIcon())
                        .isBuiltin(true)
                        .isEnabled(true)
                        .implementationType("java")
                        .implementationConfig(Map.of("class", skill.getClass().getName()))
                        .inputSchema(skill.getInputSchema())
                        .outputSchema(skill.getOutputSchema())
                        .visibility("public")
                        .build();
                    skillRepository.save(entity);
                    log.debug("Created built-in skill in database: {}", skill.getName());
                }
            } catch (Exception e) {
                log.error("Failed to sync skill {} with database: {}", skill.getName(), e.getMessage());
            }
        }
    }

    /**
     * Get a built-in skill by name.
     */
    public Optional<BuiltinSkill> getSkill(String name) {
        return Optional.ofNullable(skillMap.get(name));
    }

    /**
     * Check if a skill is a built-in skill.
     */
    public boolean isBuiltinSkill(String name) {
        return skillMap.containsKey(name);
    }

    /**
     * Get all built-in skill names.
     */
    public Set<String> getBuiltinSkillNames() {
        return Collections.unmodifiableSet(skillMap.keySet());
    }

    /**
     * Get all built-in skills.
     */
    public Collection<BuiltinSkill> getAllBuiltinSkills() {
        return Collections.unmodifiableCollection(skillMap.values());
    }

    /**
     * Execute a built-in skill by name.
     */
    public SkillResult executeSkill(String name, Map<String, Object> input) {
        BuiltinSkill skill = skillMap.get(name);
        if (skill == null) {
            return SkillResult.failure("SKILL_NOT_FOUND", "Built-in skill not found: " + name);
        }

        try {
            return skill.execute(input);
        } catch (Exception e) {
            log.error("Error executing skill {}: {}", name, e.getMessage(), e);
            return SkillResult.failure(e);
        }
    }
}
