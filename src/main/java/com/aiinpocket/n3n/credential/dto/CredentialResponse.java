package com.aiinpocket.n3n.credential.dto;

import com.aiinpocket.n3n.credential.entity.Credential;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CredentialResponse {
    private UUID id;
    private String name;
    private String type;
    private String description;
    private UUID ownerId;
    private UUID workspaceId;
    private String visibility;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public static CredentialResponse from(Credential credential) {
        return CredentialResponse.builder()
            .id(credential.getId())
            .name(credential.getName())
            .type(credential.getType())
            .description(credential.getDescription())
            .ownerId(credential.getOwnerId())
            .workspaceId(credential.getWorkspaceId())
            .visibility(credential.getVisibility())
            .metadata(credential.getMetadata())
            .createdAt(credential.getCreatedAt())
            .updatedAt(credential.getUpdatedAt())
            .build();
    }
}
