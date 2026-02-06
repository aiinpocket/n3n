package com.aiinpocket.n3n.plugin.service;

import com.aiinpocket.n3n.plugin.orchestrator.ContainerOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Docker 客戶端服務 - 薄代理
 *
 * @deprecated 請改用 {@link ContainerOrchestrator} 介面。
 * 此類別保留以維持向後相容，內部委託到 ContainerOrchestrator。
 */
@Slf4j
@Service
@Deprecated(since = "0.0.2", forRemoval = true)
public class DockerClientService {

    private final ContainerOrchestrator orchestrator;

    public DockerClientService(ContainerOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public boolean isDockerAvailable() {
        return orchestrator.isAvailable();
    }

    public boolean isFromTrustedRegistry(String image) {
        return orchestrator.isFromTrustedRegistry(image);
    }

    public void pullImage(String image, String tag, BiConsumer<Double, String> progressCallback) {
        orchestrator.pullImage(image, tag, progressCallback);
    }

    public ContainerInfo createAndStartContainer(String image, String containerName,
                                                  Map<String, String> envVars) {
        ContainerOrchestrator.ContainerInfo result = orchestrator.createAndStart(image, containerName, envVars);
        return new ContainerInfo(result.containerId(), result.port(), result.name());
    }

    public boolean waitForHealthy(String containerId, int timeoutSeconds) {
        return orchestrator.waitForHealthy(containerId, timeoutSeconds);
    }

    public void stopContainer(String containerId) {
        orchestrator.stop(containerId);
    }

    public void stopAndRemoveContainer(String containerName) {
        orchestrator.stopAndRemove(containerName);
    }

    public String getContainerLogs(String containerId, int tailLines) {
        return orchestrator.getLogs(containerId, tailLines);
    }

    /**
     * 容器資訊 - 保留以維持向後相容
     * @deprecated 請改用 {@link ContainerOrchestrator.ContainerInfo}
     */
    @Deprecated(since = "0.0.2", forRemoval = true)
    public record ContainerInfo(String containerId, Integer port, String name) {}
}
