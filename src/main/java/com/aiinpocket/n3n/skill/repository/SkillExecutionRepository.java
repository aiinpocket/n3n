package com.aiinpocket.n3n.skill.repository;

import com.aiinpocket.n3n.skill.entity.SkillExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillExecutionRepository extends JpaRepository<SkillExecution, UUID> {

    List<SkillExecution> findByExecutionId(UUID executionId);

    Page<SkillExecution> findBySkillId(UUID skillId, Pageable pageable);

    List<SkillExecution> findByNodeExecutionId(UUID nodeExecutionId);
}
