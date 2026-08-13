package com.aiinpocket.n3n.hostedapp.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hosted Apps（沙盒動態應用）設定。
 *
 * 功能預設關閉（n3n.apps.enabled=false）：執行使用者上傳的容器等同
 * 任意程式碼執行，必須由營運者明確啟用。停用時不會建立 Docker client
 * （DockerContainerRuntime 以 @ConditionalOnProperty 掛載），
 * 所有 API 一律回 404。
 */
@Component
@Getter
public class HostedAppProperties {

    @Value("${n3n.apps.enabled:false}")
    private boolean enabled;

    @Value("${n3n.apps.docker-host:unix:///var/run/docker.sock}")
    private String dockerHost;

    /** 專用 bridge network（不存在時自動建立），使用者容器彼此互通但不接觸平台網路 */
    @Value("${n3n.apps.network:n3n-apps}")
    private String network;

    @Value("${n3n.apps.max-per-user:2}")
    private int maxPerUser;

    @Value("${n3n.apps.memory-mb:512}")
    private long memoryMb;

    @Value("${n3n.apps.cpus:0.5}")
    private double cpus;

    @Value("${n3n.apps.max-zip-mb:100}")
    private int maxZipMb;

    /** 對外埠 fallback 配置範圍，格式 start-end */
    @Value("${n3n.apps.port-range:28000-28999}")
    private String portRange;

    /**
     * 子網域反向代理的目標解析模式：
     *   container —— 代理到 {containerName}:{internalPort}（平台容器須與
     *                n3n-apps network 相連，見 docker-compose.apps.yml）
     *   host-port —— 代理到 127.0.0.1:{hostPort}（平台直接跑在主機上的
     *                開發 / 非 compose 部署用）
     */
    @Value("${n3n.apps.proxy-target:container}")
    private String proxyTarget;

    public static final String PROXY_TARGET_CONTAINER = "container";
    public static final String PROXY_TARGET_HOST_PORT = "host-port";

    private int portRangeStart;
    private int portRangeEnd;

    @PostConstruct
    void parsePortRange() {
        String[] parts = portRange == null ? new String[0] : portRange.trim().split("-");
        if (parts.length != 2) {
            throw new IllegalStateException(
                    "n3n.apps.port-range 格式錯誤（須為 start-end）: " + portRange);
        }
        try {
            portRangeStart = Integer.parseInt(parts[0].trim());
            portRangeEnd = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "n3n.apps.port-range 須為數字範圍（start-end）: " + portRange);
        }
        if (portRangeStart < 1024 || portRangeEnd > 65535 || portRangeStart > portRangeEnd) {
            throw new IllegalStateException(
                    "n3n.apps.port-range 範圍不合法（1024-65535 且 start <= end）: " + portRange);
        }
    }

    public long maxZipBytes() {
        return (long) maxZipMb * 1024 * 1024;
    }
}
