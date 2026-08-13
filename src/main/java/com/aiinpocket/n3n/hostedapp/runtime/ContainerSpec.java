package com.aiinpocket.n3n.hostedapp.runtime;

import lombok.Builder;

import java.util.Map;

/**
 * 建立容器的完整描述。硬化限制（memory / cpu / cap-drop / no-new-privileges /
 * pids-limit）為必填欄位，由呼叫端統一給值，實作端無條件套用——
 * 不存在「不硬化」的建立路徑。
 *
 * @param name          容器名稱（n3napp-{slug}-{service}）
 * @param image         映像
 * @param network       加入的 bridge network
 * @param networkAlias  network 內 DNS 別名（= compose service 名，服務間互連用）
 * @param env           環境變數（已代換佔位符、已解密秘密參數）
 * @param labels        標籤（n3n.app.id / n3n.app.owner）——之後清理一律以 label 篩選
 * @param memoryBytes   記憶體上限
 * @param cpus          CPU 上限（0.5 = 半顆）
 * @param pidsLimit     process 數上限
 * @param hostPort      對外發佈埠（null = 不發佈）
 * @param containerPort hostPort 對應的容器內埠（hostPort 非 null 時必填）
 */
@Builder
public record ContainerSpec(
        String name,
        String image,
        String network,
        String networkAlias,
        Map<String, String> env,
        Map<String, String> labels,
        long memoryBytes,
        double cpus,
        long pidsLimit,
        Integer hostPort,
        Integer containerPort
) {
}
