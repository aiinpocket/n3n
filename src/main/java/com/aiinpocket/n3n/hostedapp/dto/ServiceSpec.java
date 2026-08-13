package com.aiinpocket.n3n.hostedapp.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Manifest 中的單一服務。
 *
 * @param name        服務名稱（compose service key；dockerfile 型態為 "app"）
 * @param image       映像（與 build 擇一）
 * @param build       build context 相對路徑（zip 內子目錄；"." = 根目錄）
 * @param ports       容器內埠清單（host 側發佈由平台控制，來源 zip 的 host-port 一律忽略）
 * @param environment 服務宣告的環境變數（值可含 ${VAR} 佔位，部署時代換）
 * @param dependsOn   啟動順序參考（僅作排序，不做健康檢查等完整 compose 語意）
 */
@Builder
public record ServiceSpec(
        String name,
        String image,
        String build,
        List<Integer> ports,
        Map<String, String> environment,
        List<String> dependsOn
) {
}
