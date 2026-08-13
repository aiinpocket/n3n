package com.aiinpocket.n3n.site.service;

import java.util.List;

/**
 * DNS 查詢的注入縫（seam）：自訂網域驗證用。
 * 測試可 stub，正式環境由 JndiDnsLookup 實作。
 */
public interface DnsLookup {

    /**
     * 查詢指定名稱的 TXT 記錄。查不到（NXDOMAIN / 無記錄）時回空清單。
     *
     * @throws IllegalStateException DNS 基礎設施錯誤（非「查無記錄」）
     */
    List<String> txtRecords(String name);

    /**
     * 名稱是否可解析到位址（存在 A / AAAA / CNAME 記錄）。
     */
    boolean resolves(String name);
}
