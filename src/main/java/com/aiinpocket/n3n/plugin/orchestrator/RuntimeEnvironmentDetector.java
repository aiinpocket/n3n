package com.aiinpocket.n3n.plugin.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 執行環境自動偵測器
 *
 * 偵測邏輯（優先順序）：
 * 1. KUBERNETES_SERVICE_HOST 環境變數（K8s Pod 自動注入）
 * 2. K8s Service Account token 路徑
 * 3. Docker socket 存在
 * 4. docker CLI 可用
 * 5. UNKNOWN
 */
@Component
@Slf4j
public class RuntimeEnvironmentDetector {

    public enum RuntimeEnvironment {
        KUBERNETES, DOCKER, UNKNOWN
    }

    public RuntimeEnvironment detect() {
        // 1. K8s 環境變數（Pod 內自動注入）
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            log.info("Detected Kubernetes environment (KUBERNETES_SERVICE_HOST present)");
            return RuntimeEnvironment.KUBERNETES;
        }

        // 2. K8s Service Account 掛載路徑
        if (Files.exists(Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token"))) {
            log.info("Detected Kubernetes environment (service account token found)");
            return RuntimeEnvironment.KUBERNETES;
        }

        // 3. Docker socket
        if (Files.exists(Path.of("/var/run/docker.sock"))) {
            log.info("Detected Docker environment (docker.sock found)");
            return RuntimeEnvironment.DOCKER;
        }

        // 4. docker CLI
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("Detected Docker environment (docker CLI available)");
                return RuntimeEnvironment.DOCKER;
            }
        } catch (Exception e) {
            // docker 指令不存在
        }

        log.warn("Cannot detect runtime environment, defaulting to UNKNOWN");
        return RuntimeEnvironment.UNKNOWN;
    }
}
