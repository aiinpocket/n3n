package com.aiinpocket.n3n.activity.repository;

import com.aiinpocket.n3n.activity.entity.UserActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {

    Page<UserActivity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<UserActivity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<UserActivity> findByActivityTypeOrderByCreatedAtDesc(String activityType, Pageable pageable);

    Page<UserActivity> findByUserIdAndActivityTypeOrderByCreatedAtDesc(UUID userId, String activityType, Pageable pageable);

    Page<UserActivity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
        String resourceType, UUID resourceId, Pageable pageable);

    Page<UserActivity> findByUserIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
        UUID userId, String resourceType, UUID resourceId, Pageable pageable);

    List<UserActivity> findByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    @Modifying
    @Query("DELETE FROM UserActivity a WHERE a.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") Instant before);

    @Query("SELECT a.activityType, COUNT(a) FROM UserActivity a WHERE a.userId = :userId GROUP BY a.activityType")
    List<Object[]> countByActivityTypeForUser(@Param("userId") UUID userId);
}
