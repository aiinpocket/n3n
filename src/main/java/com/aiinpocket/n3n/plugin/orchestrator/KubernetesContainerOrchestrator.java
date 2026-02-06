package com.aiinpocket.n3n.plugin.orchestrator;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Kubernetes 容器編排實作
 *
 * 使用 fabric8 kubernetes-client 管理 Plugin Pod/Service。
 * 當 n3n.orchestrator.type=kubernetes 時啟用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "n3n.orchestrator.type", havingValue = "kubernetes")
public class KubernetesContainerOrchestrator implements ContainerOrchestrator {

    @Value("${n3n.k8s.namespace:n3n}")
    private String namespace;

    @Value("${n3n.k8s.plugin-namespace:n3n-plugins}")
    private String pluginNamespace;

    @Value("${n3n.k8s.service-account:n3n-plugin-manager}")
    private String serviceAccount;

    @Value("${n3n.plugin.cpu-limit:1.0}")
    private double cpuLimit;

    @Value("${n3n.plugin.memory-limit:512m}")
    private String memoryLimit;

    private final KubernetesClient kubernetesClient;
    private final TrustedRegistryValidator registryValidator;

    private static final int DEFAULT_CONTAINER_PORT = 8080;

    public KubernetesContainerOrchestrator(KubernetesClient kubernetesClient,
                                            TrustedRegistryValidator registryValidator) {
        this.kubernetesClient = kubernetesClient;
        this.registryValidator = registryValidator;
    }

    @Override
    public String getOrchestratorType() {
        return "kubernetes";
    }

    @Override
    public boolean isAvailable() {
        try {
            kubernetesClient.namespaces().withName(pluginNamespace).get();
            return true;
        } catch (Exception e) {
            log.warn("Kubernetes not available or plugin namespace {} not found: {}",
                    pluginNamespace, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isFromTrustedRegistry(String image) {
        return registryValidator.isFromTrustedRegistry(image);
    }

    @Override
    public void pullImage(String image, String tag, BiConsumer<Double, String> progressCallback) {
        // K8s 中由 kubelet 自動拉取映像，這裡只做驗證
        if (!isFromTrustedRegistry(image)) {
            throw new SecurityException("Image " + image + " is not from a trusted registry. " +
                    "Allowed registries: " + registryValidator.getTrustedRegistries());
        }
        progressCallback.accept(0.5, "映像將由 Kubernetes 自動拉取");
        progressCallback.accept(1.0, "映像驗證完成");
    }

    @Override
    public ContainerInfo createAndStart(String image, String name, Map<String, String> envVars) {
        String sanitizedName = sanitizeK8sName(name);
        String nodeType = envVars.getOrDefault("N3N_PLUGIN_TYPE", "unknown");

        log.info("Creating K8s Deployment+Service: {} from image: {} in namespace: {}",
                sanitizedName, image, pluginNamespace);

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", sanitizedName);
        labels.put("app.kubernetes.io/managed-by", "n3n");
        labels.put("n3n/plugin", "true");
        labels.put("n3n/node-type", nodeType);

        // 建立環境變數列表
        List<EnvVar> envVarList = envVars.entrySet().stream()
                .map(e -> new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build())
                .collect(Collectors.toList());

        try {
            // 先清理舊的資源
            stopAndRemove(sanitizedName);

            // 建立 Deployment
            Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata()
                        .withName(sanitizedName)
                        .withNamespace(pluginNamespace)
                        .withLabels(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                            .withMatchLabels(Map.of("app", sanitizedName))
                        .endSelector()
                        .withNewTemplate()
                            .withNewMetadata()
                                .withLabels(labels)
                            .endMetadata()
                            .withNewSpec()
                                .withServiceAccountName(serviceAccount)
                                .addNewContainer()
                                    .withName("plugin")
                                    .withImage(image)
                                    .withEnv(envVarList)
                                    .withNewResources()
                                        .withLimits(Map.of(
                                                "cpu", new Quantity(String.valueOf(cpuLimit)),
                                                "memory", new Quantity(memoryLimit)
                                        ))
                                        .withRequests(Map.of(
                                                "cpu", new Quantity(String.valueOf(cpuLimit / 2)),
                                                "memory", new Quantity(memoryLimit)
                                        ))
                                    .endResources()
                                    .addNewPort()
                                        .withContainerPort(DEFAULT_CONTAINER_PORT)
                                        .withName("http")
                                    .endPort()
                                    .withNewLivenessProbe()
                                        .withNewHttpGet()
                                            .withPath("/health")
                                            .withPort(new IntOrString(DEFAULT_CONTAINER_PORT))
                                        .endHttpGet()
                                        .withInitialDelaySeconds(30)
                                        .withPeriodSeconds(10)
                                    .endLivenessProbe()
                                    .withNewReadinessProbe()
                                        .withNewHttpGet()
                                            .withPath("/health")
                                            .withPort(new IntOrString(DEFAULT_CONTAINER_PORT))
                                        .endHttpGet()
                                        .withInitialDelaySeconds(10)
                                        .withPeriodSeconds(5)
                                    .endReadinessProbe()
                                    .withNewSecurityContext()
                                        .withRunAsNonRoot(true)
                                        .withAllowPrivilegeEscalation(false)
                                        .withNewCapabilities()
                                            .withDrop(List.of("ALL"))
                                        .endCapabilities()
                                    .endSecurityContext()
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();

            kubernetesClient.apps().deployments()
                    .inNamespace(pluginNamespace)
                    .resource(deployment)
                    .create();

            // 建立 ClusterIP Service
            Service service = new ServiceBuilder()
                    .withNewMetadata()
                        .withName(sanitizedName)
                        .withNamespace(pluginNamespace)
                        .withLabels(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withSelector(Map.of("app", sanitizedName))
                        .addNewPort()
                            .withPort(DEFAULT_CONTAINER_PORT)
                            .withTargetPort(new IntOrString(DEFAULT_CONTAINER_PORT))
                            .withName("http")
                        .endPort()
                        .withType("ClusterIP")
                    .endSpec()
                    .build();

            kubernetesClient.services()
                    .inNamespace(pluginNamespace)
                    .resource(service)
                    .create();

            log.info("K8s Deployment+Service created: {} in namespace {}", sanitizedName, pluginNamespace);
            return new ContainerInfo(sanitizedName, DEFAULT_CONTAINER_PORT, sanitizedName);

        } catch (Exception e) {
            log.error("Failed to create K8s resources for: {}", sanitizedName, e);
            throw new RuntimeException("Failed to create K8s resources: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean waitForHealthy(String containerId, int timeoutSeconds) {
        log.info("Waiting for K8s Deployment {} to become ready (timeout: {}s)", containerId, timeoutSeconds);
        try {
            kubernetesClient.apps().deployments()
                    .inNamespace(pluginNamespace)
                    .withName(containerId)
                    .waitUntilReady(timeoutSeconds, TimeUnit.SECONDS);
            log.info("K8s Deployment {} is ready", containerId);
            return true;
        } catch (Exception e) {
            log.warn("K8s Deployment {} did not become ready within {} seconds: {}",
                    containerId, timeoutSeconds, e.getMessage());
            return false;
        }
    }

    @Override
    public void stop(String containerId) {
        log.info("Scaling down K8s Deployment: {}", containerId);
        try {
            kubernetesClient.apps().deployments()
                    .inNamespace(pluginNamespace)
                    .withName(containerId)
                    .scale(0);
        } catch (Exception e) {
            log.warn("Failed to scale down Deployment {}: {}", containerId, e.getMessage());
        }
    }

    @Override
    public void stopAndRemove(String name) {
        try {
            kubernetesClient.apps().deployments()
                    .inNamespace(pluginNamespace)
                    .withName(name)
                    .delete();
            kubernetesClient.services()
                    .inNamespace(pluginNamespace)
                    .withName(name)
                    .delete();
            log.debug("K8s resources cleaned up: {}", name);
        } catch (Exception e) {
            log.debug("K8s resource {} might not exist: {}", name, e.getMessage());
        }
    }

    @Override
    public String getLogs(String containerId, int tailLines) {
        try {
            // 取得 Deployment 對應的 Pod
            var pods = kubernetesClient.pods()
                    .inNamespace(pluginNamespace)
                    .withLabel("app", containerId)
                    .list().getItems();

            if (pods.isEmpty()) {
                return "No pods found for deployment: " + containerId;
            }

            String podName = pods.get(0).getMetadata().getName();
            return kubernetesClient.pods()
                    .inNamespace(pluginNamespace)
                    .withName(podName)
                    .tailingLines(tailLines)
                    .getLog();
        } catch (Exception e) {
            log.error("Failed to get K8s logs for: {}", containerId, e);
            return "Failed to get logs: " + e.getMessage();
        }
    }

    @Override
    public List<ContainerStatus> listPluginContainers() {
        try {
            return kubernetesClient.apps().deployments()
                    .inNamespace(pluginNamespace)
                    .withLabel("n3n/plugin", "true")
                    .list().getItems().stream()
                    .map(d -> new ContainerStatus(
                            d.getMetadata().getName(),
                            d.getMetadata().getName(),
                            getDeploymentState(d),
                            DEFAULT_CONTAINER_PORT,
                            d.getMetadata().getLabels(),
                            d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list K8s plugin deployments", e);
            return List.of();
        }
    }

    @Override
    public String getServiceEndpoint(String containerId) {
        return "http://" + containerId + "." + pluginNamespace + ".svc.cluster.local:" + DEFAULT_CONTAINER_PORT;
    }

    private String sanitizeK8sName(String name) {
        String sanitized = name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("--+", "-")
                .replaceAll("^-|-$", "");
        return sanitized.substring(0, Math.min(sanitized.length(), 63));
    }

    private String getDeploymentState(Deployment deployment) {
        if (deployment.getStatus() == null) return "unknown";

        Integer readyReplicas = deployment.getStatus().getReadyReplicas();
        Integer replicas = deployment.getStatus().getReplicas();

        if (readyReplicas != null && readyReplicas > 0) return "running";
        if (replicas != null && replicas > 0) return "starting";

        List<DeploymentCondition> conditions = deployment.getStatus().getConditions();
        if (conditions != null) {
            for (DeploymentCondition condition : conditions) {
                if ("Available".equals(condition.getType()) && "False".equals(condition.getStatus())) {
                    return "unavailable";
                }
            }
        }
        return "pending";
    }
}
