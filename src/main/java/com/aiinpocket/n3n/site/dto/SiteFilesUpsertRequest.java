package com.aiinpocket.n3n.site.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批次 upsert 網站檔案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteFilesUpsertRequest {

    @NotEmpty
    private List<SiteFileUpsertEntry> files;
}
