package com.aiinpocket.n3n.scheduler.repository;

import com.aiinpocket.n3n.scheduler.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    Optional<Schedule> findByIdAndCreatedBy(UUID id, UUID createdBy);

    long countByCreatedBy(UUID createdBy);

    Optional<Schedule> findByQuartzScheduleId(String quartzScheduleId);

    void deleteByFlowId(UUID flowId);
}
