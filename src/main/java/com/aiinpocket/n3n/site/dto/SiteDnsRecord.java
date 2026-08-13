package com.aiinpocket.n3n.site.dto;

/**
 * 使用者需建立的 DNS 記錄（自訂網域驗證與指向用）。
 */
public record SiteDnsRecord(String type, String host, String value) {
}
