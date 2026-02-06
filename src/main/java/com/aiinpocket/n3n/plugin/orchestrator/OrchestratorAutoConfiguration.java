package com.aiinpocket.n3n.plugin.orchestrator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 容器編排自動配置
 *
 * 處理 n3n.orchestrator.type=auto 的動態偵測邏輯，
 * 以及 K8s 模式下的 KubernetesClient Bean 提供。
 */
@Configuration
@Slf4j
public class OrchestratorAutoConfiguration {

    /**
     * 當 orchestrator.type=auto 時，動態偵測環境並建立對應的編排器。
     */
    @Bean
    @ConditionalOnProperty(name = "n3n.orchestrator.type", havingValue = "auto")
    public ContainerOrchestrator autoDetectedOrchestrator(
            RuntimeEnvironmentDetector detector,
            TrustedRegistryValidator registryValidator,
            @Autowired(required = false) KubernetesClient kubernetesClient) {

        RuntimeEnvironmentDetector.RuntimeEnvironment env = detector.detect();
        return switch (env) {
            case KUBERNETES -> {
                if (kubernetesClient == null) {
                    log.warn("Detected K8s but no KubernetesClient available, falling back to Docker");
                    yield new DockerContainerOrchestrator(registryValidator);
                }
                log.info("Auto-detected Kubernetes environment, using KubernetesContainerOrchestrator");
                yield new KubernetesContainerOrchestrator(kubernetesClient, registryValidator);
            }
            case DOCKER, UNKNOWN -> {
                log.info("Auto-detected Docker/unknown environment, using DockerContainerOrchestrator");
                yield new DockerContainerOrchestrator(registryValidator);
            }
        };
    }

    /**
     * K8s 模式下提供 KubernetesClient Bean
     */
    @Bean
    @ConditionalOnProperty(name = "n3n.orchestrator.type", havingValue = "kubernetes")
    @ConditionalOnClass(name = "io.fabric8.kubernetes.client.KubernetesClient")
    public KubernetesClient kubernetesClient() {
        log.info("Creating KubernetesClient for kubernetes orchestrator mode");
        return new KubernetesClientBuilder().build();
    }

    /**
     * auto 模式下，如果偵測到 K8s 也需要提供 KubernetesClient
     */
    @Bean("autoKubernetesClient")
    @ConditionalOnProperty(name = "n3n.orchestrator.type", havingValue = "auto")
    @ConditionalOnClass(name = "io.fabric8.kubernetes.client.KubernetesClient")
    public KubernetesClient autoKubernetesClient(RuntimeEnvironmentDetector detector) {
        if (detector.detect() == RuntimeEnvironmentDetector.RuntimeEnvironment.KUBERNETES) {
            log.info("Auto mode: Creating KubernetesClient for detected K8s environment");
            return new KubernetesClientBuilder().build();
        }
        return null;
    }
}
