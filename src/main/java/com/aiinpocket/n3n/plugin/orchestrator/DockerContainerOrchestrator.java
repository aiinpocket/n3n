package com.aiinpocket.n3n.plugin.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Docker 容器編排實作
 *
 * 使用 Docker CLI 管理 Plugin 容器。
 * 當 n3n.orchestrator.type=docker（預設）或未設定時啟用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "n3n.orchestrator.type", havingValue = "docker", matchIfMissing = true)
public class DockerContainerOrchestrator implements ContainerOrchestrator {

    @Value("${n3n.docker.enabled:true}")
    private boolean dockerEnabled;

    @Value("${n3n.docker.network:n3n-network}")
    private String dockerNetwork;

    @Value("${n3n.plugin.cpu-limit:1.0}")
    private double cpuLimit;

    @Value("${n3n.plugin.memory-limit:512m}")
    private String memoryLimit;

    @Value("${n3n.plugin.memory-swap-limit:512m}")
    private String memorySwapLimit;

    @Value("${n3n.plugin.pids-limit:50}")
    private int pidsLimit;

    @Value("${n3n.docker.content-trust:true}")
    private boolean contentTrustEnabled;

    private final TrustedRegistryValidator registryValidator;

    public DockerContainerOrchestrator(TrustedRegistryValidator registryValidator) {
        this.registryValidator = registryValidator;
    }

    @Override
    public String getOrchestratorType() {
        return "docker";
    }

    @Override
    public boolean isAvailable() {
        if (!dockerEnabled) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("Docker is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isFromTrustedRegistry(String image) {
        return registryValidator.isFromTrustedRegistry(image);
    }

    @Override
    public void pullImage(String image, String tag, BiConsumer<Double, String> progressCallback) {
        String fullImage = image + ":" + tag;

        if (!isFromTrustedRegistry(image)) {
            throw new SecurityException("Image " + image + " is not from a trusted registry. " +
                    "Allowed registries: " + registryValidator.getTrustedRegistries());
        }

        log.info("Pulling Docker image: {} (Content Trust: {})", fullImage, contentTrustEnabled);

        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "pull", fullImage);
            if (contentTrustEnabled) {
                pb.environment().put("DOCKER_CONTENT_TRUST", "1");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                double progress = 0;
                while ((line = reader.readLine()) != null) {
                    log.debug("Docker pull: {}", line);
                    if (line.contains("Pulling")) {
                        progressCallback.accept(0.1, "Pulling image");
                    } else if (line.contains("Downloading")) {
                        progress = Math.min(progress + 0.1, 0.7);
                        progressCallback.accept(progress, "Downloading...");
                    } else if (line.contains("Extracting")) {
                        progress = Math.min(progress + 0.1, 0.9);
                        progressCallback.accept(progress, "Extracting...");
                    } else if (line.contains("Pull complete") || line.contains("Downloaded newer")) {
                        progressCallback.accept(1.0, "Pull complete");
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
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to pull Docker image: {}", fullImage, e);
            throw new RuntimeException("Failed to pull image: " + e.getMessage(), e);
        }
    }

    @Override
    public ContainerInfo createAndStart(String image, String name, Map<String, String> envVars) {
        log.info("Creating container: {} from image: {} (CPU: {}, Memory: {})",
                name, image, cpuLimit, memoryLimit);

        try {
            stopAndRemove(name);

            List<String> cmd = new ArrayList<>();
            cmd.addAll(List.of("docker", "run", "-d", "--name", name));
            cmd.addAll(List.of("-P")); // 自動分配 port
            cmd.addAll(List.of("--network", dockerNetwork));
            cmd.addAll(List.of("--restart", "unless-stopped"));

            // 資源限制
            cmd.addAll(List.of("--cpus", String.valueOf(cpuLimit)));
            cmd.addAll(List.of("--memory", memoryLimit));
            cmd.addAll(List.of("--memory-swap", memorySwapLimit));
            cmd.addAll(List.of("--pids-limit", String.valueOf(pidsLimit)));

            // 安全限制
            cmd.addAll(List.of("--security-opt", "no-new-privileges:true"));
            cmd.addAll(List.of("--cap-drop", "ALL"));

            // N3N Plugin 標籤（用於服務發現）
            cmd.addAll(List.of("--label", "n3n.plugin=true"));
            String nodeType = envVars.getOrDefault("N3N_PLUGIN_TYPE", "unknown");
            cmd.addAll(List.of("--label", "n3n.node-type=" + nodeType));

            // 環境變數
            for (Map.Entry<String, String> env : envVars.entrySet()) {
                cmd.addAll(List.of("-e", env.getKey() + "=" + env.getValue()));
            }

            cmd.add(image);

            ProcessBuilder pb = new ProcessBuilder(cmd);
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

            Integer port = getContainerPort(containerId);
            log.info("Container {} created with ID: {}, port: {}", name, containerId, port);
            return new ContainerInfo(containerId, port, name);

        } catch (Exception e) {
            log.error("Failed to create container: {}", name, e);
            throw new RuntimeException("Failed to create container: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean waitForHealthy(String containerId, int timeoutSeconds) {
        log.info("Waiting for container {} to become healthy", containerId);

        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
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
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.debug("Health check failed: {}", e.getMessage());
            }
        }

        log.warn("Container {} did not become healthy within {} seconds", containerId, timeoutSeconds);
        return false;
    }

    @Override
    public void stop(String containerId) {
        log.info("Stopping container: {}", containerId);
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerId);
            pb.start().waitFor(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
        }
    }

    @Override
    public void stopAndRemove(String name) {
        try {
            ProcessBuilder stop = new ProcessBuilder("docker", "stop", name);
            stop.start().waitFor(30, TimeUnit.SECONDS);

            ProcessBuilder rm = new ProcessBuilder("docker", "rm", name);
            rm.start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Container {} might not exist: {}", name, e.getMessage());
        }
    }

    @Override
    public String getLogs(String containerId, int tailLines) {
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

    @Override
    public List<ContainerStatus> listPluginContainers() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-a",
                    "--filter", "label=n3n.plugin=true",
                    "--format", "{{.ID}}\t{{.Names}}\t{{.State}}\t{{.Image}}\t{{.Labels}}");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<ContainerStatus> containers = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 4) {
                        String id = parts[0];
                        String containerName = parts[1];
                        String state = parts[2];
                        String image = parts[3];

                        Map<String, String> labels = new HashMap<>();
                        if (parts.length >= 5) {
                            for (String label : parts[4].split(",")) {
                                String[] kv = label.split("=", 2);
                                if (kv.length == 2) {
                                    labels.put(kv[0].trim(), kv[1].trim());
                                }
                            }
                        }

                        Integer port = "running".equals(state) ? getContainerPort(id) : null;
                        containers.add(new ContainerStatus(id, containerName, state, port, labels, image));
                    }
                }
            }
            process.waitFor();
            return containers;
        } catch (Exception e) {
            log.error("Failed to list plugin containers", e);
            return List.of();
        }
    }

    @Override
    public String getServiceEndpoint(String containerId) {
        Integer port = getContainerPort(containerId);
        return port != null ? "http://localhost:" + port : null;
    }

    private Integer getContainerPort(String containerId) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "port", containerId);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains(":")) {
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
}
