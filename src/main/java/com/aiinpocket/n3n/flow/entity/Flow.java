package com.aiinpocket.n3n.flow.entity;

import com.aiinpocket.n3n.common.constant.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(nullable = false)
    @Builder.Default
    private String visibility = Status.Visibility.PRIVATE;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Version
    @Column(name = "opt_lock_version")
    @Builder.Default
    private Long optLockVersion = 0L;
}
