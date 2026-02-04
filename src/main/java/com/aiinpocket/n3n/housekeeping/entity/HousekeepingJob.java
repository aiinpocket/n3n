package com.aiinpocket.n3n.housekeeping.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks housekeeping job executions for auditing.
 */
@Entity
@Table(name = "housekeeping_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HousekeepingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "started_at")
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    @Builder.Default
    private String status = "running";

    @Column(name = "records_processed")
    @Builder.Default
    private Integer recordsProcessed = 0;

    @Column(name = "records_archived")
    @Builder.Default
    private Integer recordsArchived = 0;

    @Column(name = "records_deleted")
    @Builder.Default
    private Integer recordsDeleted = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    /**
     * Mark job as completed.
     */
    public void complete() {
        this.status = "completed";
        this.completedAt = Instant.now();
    }

    /**
     * Mark job as failed.
     */
    public void fail(String errorMessage) {
        this.status = "failed";
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }
}
