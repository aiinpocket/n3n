package com.aiinpocket.n3n.site.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 單一檔案 upsert：content（UTF-8 純文字）與 contentBase64（二進位）擇一提供。
 * contentType 可省略，由副檔名白名單推斷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteFileUpsertEntry {

    private String path;

    /** UTF-8 純文字內容（與 contentBase64 擇一） */
    private String content;

    /** Base64 編碼的二進位內容（與 content 擇一） */
    private String contentBase64;

    private String contentType;
}
