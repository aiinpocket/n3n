package com.aiinpocket.n3n.artifact.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Artifact 分頁列表回應：items + 總筆數 + 總用量。
 */
@Value
@Builder
public class ArtifactListResponse {

    List<ArtifactResponse> items;

    /** 符合條件的總筆數。 */
    long total;

    /** 使用者所有 artifacts 的總大小（bytes）。 */
    long totalSizeBytes;
}
