package com.aiinpocket.n3n.site.dto;

import com.aiinpocket.n3n.site.entity.SiteFile;
import lombok.Builder;

import java.time.Instant;

/**
 * 檔案 metadata（不含內容），供列表顯示。
 */
@Builder
public record SiteFileMeta(
        String path,
        String contentType,
        long sizeBytes,
        Instant updatedAt
) {
    public static SiteFileMeta from(SiteFile file) {
        return SiteFileMeta.builder()
                .path(file.getPath())
                .contentType(file.getContentType())
                .sizeBytes(file.getSizeBytes())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}
