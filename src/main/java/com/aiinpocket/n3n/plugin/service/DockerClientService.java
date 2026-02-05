package com.aiinpocket.n3n.plugin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Docker 客戶端服務
 * 使用 Docker CLI 執行容器操作
 *
 * 安全功能：
 * - CPU 和記憶體資源限制
 * - Docker Content Trust 映像簽章驗證
 * - 可信任 Registry 白名單
 *
 * 注意：生產環境應使用 docker-java 客戶端庫
 * 目前使用 CLI 以簡化依賴
 */
@Slf4j
@Service
public class DockerClientService {

    @Value("${n3n.docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${n3n.docker.enabled:true}")
    private boolean dockerEnabled;

    @Value("${n3n.docker.network:n3n-network}")
    private String dockerNetwork;

    // 資源限制配置
    @Value("${n3n.plugin.cpu-limit:1.0}")
    private double cpuLimit;

    @Value("${n3n.plugin.memory-limit:512m}")
    private String memoryLimit;

    // 安全考量：Swap 限制應與記憶體限制相同以防止 swap 濫用
    @Value("${n3n.plugin.memory-swap-limit:512m}")
    private String memorySwapLimit;

    // 安全考量：降低 PID 限制以防止 fork bomb 攻擊
    @Value("${n3n.plugin.pids-limit:50}")
    private int pidsLimit;

    // 安全配置：預設啟用 Content Trust 進行映像簽章驗證
    @Value("${n3n.docker.content-trust:true}")
    private boolean contentTrustEnabled;

    @Value("${n3n.docker.trusted-registries:ghcr.io/n3n,docker.io/n3n}")
    private String trustedRegistriesConfig;

    /**
     * 檢查 Docker 是否可用
     */
    public boolean isDockerAvailable() {
        if (!dockerEnabled) return false;

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("Docker is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 取得可信任的 Registry 列表
     */
    private List<String> getTrustedRegistries() {
        return Arrays.stream(trustedRegistriesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 驗證映像是否來自可信任的 Registry
     *
     * @param image 映像名稱
     * @return true 如果映像來自可信任的 Registry
     */
    public boolean isFromTrustedRegistry(String image) {
        List<String> trustedRegistries = getTrustedRegistries();
        if (trustedRegistries.isEmpty()) {
            log.warn("No trusted registries configured, allowing all images");
            return true;
        }

        for (String registry : trustedRegistries) {
            if (image.startsWith(registry + "/") || image.startsWith(registry + ":")) {
                return true;
            }
        }

        // Docker Hub 官方映像（無前綴）
        if (!image.contains("/") || image.startsWith("library/")) {
            return trustedRegistries.stream().anyMatch(r -> r.contains("docker.io"));
        }

        log.warn("Image {} is not from a trusted registry. Trusted registries: {}", image, trustedRegistries);
        return false;
    }

    /**
     * 拉取 Docker 映像
     *
     * 安全功能：
     * - 驗證映像來自可信任的 Registry
     * - 可選啟用 Docker Content Trust 進行簽章驗證
     *
     * @param image 映像名稱
     * @param tag 映像標籤
     * @param progressCallback 進度回調 (progress 0-1, statusMessage)
     * @throws SecurityException 如果映像不是來自可信任的 Registry
     */
    public void pullImage(String image, String tag, BiConsumer<Double, String> progressCallback) {
        String fullImage = image + ":" + tag;

        // 驗證可信任的 Registry
        if (!isFromTrustedRegistry(image)) {
            throw new SecurityException("Image " + image + " is not from a trusted registry. " +
                    "Allowed registries: " + getTrustedRegistries());
        }

        log.info("Pulling Docker image: {} (Content Trust: {})", fullImage, contentTrustEnabled);

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "pull", fullImage);

            // 設定 Docker Content Trust 環境變數
            if (contentTrustEnabled) {
                pb.environment().put("DOCKER_CONTENT_TRUST", "1");
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 讀取輸出並解析進度
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                double progress = 0;
                while ((line = reader.readLine()) != null) {
                    log.debug("Docker pull: {}", line);

                    // 解析進度
                    if (line.contains("Pulling")) {
                        progressCallback.accept(0.1, "開始拉取映像");
                    } else if (line.contains("Downloading")) {
                        progress = Math.min(progress + 0.1, 0.7);
                        progressCallback.accept(progress, "下載中...");
                    } else if (line.contains("Extracting")) {
                        progress = Math.min(progress + 0.1, 0.9);
                        progressCallback.accept(progress, "解壓中...");
                    } else if (line.contains("Pull complete") || line.contains("Downloaded newer")) {
                        progressCallback.accept(1.0, "拉取完成");
                    }
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Docker pull timed out");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Docker pull failed with exit code: " + process.exitValue());
            }

            log.info("Successfully pulled image: {}", fullImage);

        } catch (Exception e) {
            log.error("Failed to pull Docker image: {}", fullImage, e);
            throw new RuntimeException("Failed to pull image: " + e.getMessage(), e);
        }
    }

    /**
     * 建立並啟動容器
     *
     * 安全功能：
     * - CPU 限制（預設 1.0 核心）
     * - 記憶體限制（預設 512MB）
     * - Swap 限制（預設 1GB）
     * - 行程數限制（預設 100）
     * - 禁用特權模式
     * - 禁用新特權獲取
     *
     * @param image 映像名稱
     * @param containerName 容器名稱
     * @param envVars 環境變數
     * @return 容器資訊
     */
    public ContainerInfo createAndStartContainer(String image, String containerName,
                                                  Map<String, String> envVars) {
        log.info("Creating container: {} from image: {} (CPU: {}, Memory: {})",
                containerName, image, cpuLimit, memoryLimit);

        try {
            // 先停止並移除同名容器（如果存在）
            stopAndRemoveContainer(containerName);

            // 建立容器
            ProcessBuilder pb = new ProcessBuilder();
            pb.command().add("docker");
            pb.command().add("run");
            pb.command().add("-d");
            pb.command().add("--name");
            pb.command().add(containerName);
            pb.command().add("-P"); // 自動分配 port
            pb.command().add("--network");
            pb.command().add(dockerNetwork);
            pb.command().add("--restart");
            pb.command().add("unless-stopped");

            // 資源限制
            pb.command().add("--cpus");
            pb.command().add(String.valueOf(cpuLimit));
            pb.command().add("--memory");
            pb.command().add(memoryLimit);
            pb.command().add("--memory-swap");
            pb.command().add(memorySwapLimit);
            pb.command().add("--pids-limit");
            pb.command().add(String.valueOf(pidsLimit));

            // 安全限制
            pb.command().add("--security-opt");
            pb.command().add("no-new-privileges:true");
            pb.command().add("--cap-drop");
            pb.command().add("ALL");
            // 安全考量：不添加任何 capabilities，使用高端口 (>1024) 代替低端口

            // 唯讀根檔案系統（可選，某些容器可能需要寫入）
            // pb.command().add("--read-only");

            // 添加環境變數
            for (Map.Entry<String, String> env : envVars.entrySet()) {
                pb.command().add("-e");
                pb.command().add(env.getKey() + "=" + env.getValue());
            }

            pb.command().add(image);

            pb.redirectErrorStream(true);
            Process process = pb.start();

            String containerId;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                containerId = reader.readLine();
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                throw new RuntimeException("Failed to create container");
            }

            // 取得容器 port
            Integer port = getContainerPort(containerId);

            log.info("Container {} created with ID: {}, port: {}", containerName, containerId, port);
            return new ContainerInfo(containerId, port, containerName);

        } catch (Exception e) {
            log.error("Failed to create container: {}", containerName, e);
            throw new RuntimeException("Failed to create container: " + e.getMessage(), e);
        }
    }

    /**
     * 等待容器健康
     *
     * @param containerId 容器 ID
     * @param timeoutSeconds 超時秒數
     * @return 是否健康
     */
    public boolean waitForHealthy(String containerId, int timeoutSeconds) {
        log.info("Waiting for container {} to become healthy", containerId);

        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                // 檢查容器狀態
                ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "-f", "{{.State.Status}}", containerId);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String status;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    status = reader.readLine();
                }
                process.waitFor();

                if ("running".equals(status)) {
                    // 嘗試連接容器的服務端點
                    Integer port = getContainerPort(containerId);
                    if (port != null && isPortResponding(port)) {
                        log.info("Container {} is healthy", containerId);
                        return true;
                    }
                } else if ("exited".equals(status) || "dead".equals(status)) {
                    log.error("Container {} is in {} state", containerId, status);
                    return false;
                }

                Thread.sleep(1000);
            } catch (Exception e) {
                log.debug("Health check failed: {}", e.getMessage());
            }
        }

        log.warn("Container {} did not become healthy within {} seconds", containerId, timeoutSeconds);
        return false;
    }

    /**
     * 停止容器
     */
    public void stopContainer(String containerId) {
        log.info("Stopping container: {}", containerId);

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerId);
            pb.start().waitFor(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * 停止並移除容器
     */
    public void stopAndRemoveContainer(String containerName) {
        try {
            ProcessBuilder stop = new ProcessBuilder("docker", "stop", containerName);
            stop.start().waitFor(30, TimeUnit.SECONDS);

            ProcessBuilder rm = new ProcessBuilder("docker", "rm", containerName);
            rm.start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 容器可能不存在，忽略錯誤
            log.debug("Container {} might not exist: {}", containerName, e.getMessage());
        }
    }

    /**
     * 取得容器日誌
     */
    public String getContainerLogs(String containerId, int tailLines) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "logs", "--tail", String.valueOf(tailLines), containerId);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                }
            }
            process.waitFor();

            return logs.toString();
        } catch (Exception e) {
            log.error("Failed to get container logs: {}", containerId, e);
            return "Failed to get logs: " + e.getMessage();
        }
    }

    /**
     * 取得容器暴露的 port
     */
    private Integer getContainerPort(String containerId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "port", containerId);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains(":")) {
                    // 格式: 8080/tcp -> 0.0.0.0:32768
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        return Integer.parseInt(parts[parts.length - 1].trim());
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("Failed to get container port: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 檢查 port 是否響應
     */
    private boolean isPortResponding(int port) {
        try {
            URL url = new URL("http://localhost:" + port + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 容器資訊
     */
    public record ContainerInfo(String containerId, Integer port, String name) {}
}
