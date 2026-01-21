package com.aiinpocket.n3n.credential.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential_shares")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "can_use")
    @Builder.Default
    private Boolean canUse = true;

    @Column(name = "can_edit")
    @Builder.Default
    private Boolean canEdit = false;

    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    @Column(name = "shared_at")
    @Builder.Default
    private Instant sharedAt = Instant.now();
}
