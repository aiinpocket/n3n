package com.aiinpocket.n3n.skill.repository;

import com.aiinpocket.n3n.skill.entity.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByName(String name);

    boolean existsByName(String name);

    List<Skill> findByIsBuiltinTrue();

    List<Skill> findByCategory(String category);

    List<Skill> findByIsEnabledTrue();

    @Query("SELECT s FROM Skill s WHERE s.isBuiltin = true OR s.ownerId = :ownerId OR s.visibility = 'public'")
    List<Skill> findAccessibleSkills(UUID ownerId);

    @Query("SELECT s FROM Skill s WHERE s.isEnabled = true AND (s.isBuiltin = true OR s.ownerId = :ownerId OR s.visibility = 'public')")
    List<Skill> findEnabledAccessibleSkills(UUID ownerId);

    Page<Skill> findByOwnerId(UUID ownerId, Pageable pageable);

    @Query("SELECT DISTINCT s.category FROM Skill s WHERE s.isEnabled = true")
    List<String> findAllCategories();
}
