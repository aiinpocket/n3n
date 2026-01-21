package com.aiinpocket.n3n.workspace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    @Builder.Default
    private String role = "member";

    @Column(name = "joined_at")
    @Builder.Default
    private Instant joinedAt = Instant.now();
}
