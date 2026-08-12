package com.aiinpocket.n3n.artifact.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * 建立 artifact 時的 metadata（來源節點、檔名、MIME type 等）。
 */
@Value
@Builder
public class ArtifactMeta {

    /** 原始檔名（會經過 sanitize，防止 path traversal）。 */
    String filename;

    /** MIME type，例如 audio/mpeg、video/mp4。 */
    String mimeType;

    UUID flowId;

    UUID executionId;

    String nodeId;

    /** 產生此檔案的節點類型，例如 aiTts / falAi / saveArtifact。 */
    String sourceNodeType;
}
