package com.aiinpocket.n3n.housekeeping.repository;

import com.aiinpocket.n3n.housekeeping.entity.HousekeepingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HousekeepingJobRepository extends JpaRepository<HousekeepingJob, UUID> {

    /**
     * Find latest jobs.
     */
    Page<HousekeepingJob> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Find latest job by type.
     */
    Optional<HousekeepingJob> findFirstByJobTypeOrderByStartedAtDesc(String jobType);

    /**
     * Check if a job is currently running.
     */
    boolean existsByJobTypeAndStatus(String jobType, String status);
}
