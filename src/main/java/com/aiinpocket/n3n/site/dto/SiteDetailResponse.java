package com.aiinpocket.n3n.site.dto;

import lombok.Builder;

import java.util.List;

/**
 * 網站詳情：摘要 + 檔案 metadata 列表。
 */
@Builder
public record SiteDetailResponse(
        SiteResponse site,
        List<SiteFileMeta> files
) {
}
