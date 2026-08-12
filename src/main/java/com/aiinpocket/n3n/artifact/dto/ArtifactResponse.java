package com.aiinpocket.n3n.artifact.dto;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Artifact 列表項目回應 DTO。
 */
@Value
@Builder
public class ArtifactResponse {

    UUID id;
    String filename;
    String mimeType;
    long sizeBytes;
    String sourceNodeType;
    UUID flowId;
    UUID executionId;
    String nodeId;
    Instant createdAt;

    public static ArtifactResponse from(Artifact artifact) {
        return ArtifactResponse.builder()
                .id(artifact.getId())
                .filename(artifact.getFilename())
                .mimeType(artifact.getMimeType())
                .sizeBytes(artifact.getSizeBytes())
                .sourceNodeType(artifact.getSourceNodeType())
                .flowId(artifact.getFlowId())
                .executionId(artifact.getExecutionId())
                .nodeId(artifact.getNodeId())
                .createdAt(artifact.getCreatedAt())
                .build();
    }
}
