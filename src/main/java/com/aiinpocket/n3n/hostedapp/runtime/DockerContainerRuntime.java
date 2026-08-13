package com.aiinpocket.n3n.hostedapp.runtime;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ContainerRuntime 的 Docker 實作（docker-java）。
 *
 * 僅在 n3n.apps.enabled=true 時建立 bean——功能關閉時完全不接觸
 * Docker socket。所有容器一律套用 ContainerSpec 的硬化限制：
 * cap-drop ALL（僅加回 CHOWN/SETUID/SETGID/DAC_OVERRIDE/FOWNER/KILL/NET_BIND_SERVICE 常規集）、no-new-privileges、
 * pids-limit、記憶體/CPU 上限、restart unless-stopped、無任何 bind mount。
 */
@Component
@ConditionalOnProperty(name = "n3n.apps.enabled", havingValue = "true")
@Slf4j
public class DockerContainerRuntime implements ContainerRuntime {

    private static final int STOP_TIMEOUT_SECONDS = 10;
    private static final long PULL_TIMEOUT_MINUTES = 10;
    private static final long BUILD_TIMEOUT_MINUTES = 15;
    private static final long LOGS_TIMEOUT_SECONDS = 30;

    private final DockerClient client;
    private final DockerHttpClient httpClient;

    public DockerContainerRuntime(HostedAppProperties properties) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getDockerHost())
                .build();
        this.httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(15))
                .build();
        this.client = DockerClientImpl.getInstance(config, httpClient);
        log.info("DockerContainerRuntime initialized (host={})", properties.getDockerHost());
    }

    @PreDestroy
    void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("Failed to close Docker http client", e);
        }
    }

    @Override
    public void ensureNetwork(String name) {
        boolean exists = client.listNetworksCmd().withNameFilter(name).exec().stream()
                .anyMatch(n -> name.equals(n.getName()));
        if (!exists) {
            client.createNetworkCmd().withName(name).withDriver("bridge").exec();
            log.info("Created hosted-app network: {}", name);
        }
    }

    @Override
    public void pullImage(String image) {
        try {
            client.pullImageCmd(image).start()
                    .awaitCompletion(PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("拉取映像被中斷: " + image, e);
        }
    }

    @Override
    public String buildImage(String imageTag, Map<String, byte[]> files, Map<String, String> labels) {
        byte[] tar = toTar(files);
        return client.buildImageCmd()
                .withTarInputStream(new ByteArrayInputStream(tar))
                .withTags(Set.of(imageTag))
                .withLabels(labels)
                .exec(new BuildImageResultCallback())
                .awaitImageId(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String createContainer(ContainerSpec spec) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(spec.memoryBytes())
                .withNanoCPUs((long) (spec.cpus() * 1_000_000_000L))
                .withCapDrop(Capability.ALL)
                // 官方映像（nginx/mysql/wordpress...）的 entrypoint 普遍需要
                // chown/setuid/setgid 來降權啟動，全部拔掉會直接 crash loop。
                // 保留這組常規最小能力；真正危險的（SYS_ADMIN、NET_RAW、MKNOD...）仍被 drop。
                .withCapAdd(Capability.CHOWN, Capability.DAC_OVERRIDE, Capability.FOWNER,
                        Capability.SETGID, Capability.SETUID, Capability.KILL,
                        Capability.NET_BIND_SERVICE)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withPidsLimit(spec.pidsLimit())
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart())
                .withNetworkMode(spec.network());

        List<ExposedPort> exposedPorts = new ArrayList<>();
        if (spec.hostPort() != null && spec.containerPort() != null) {
            ExposedPort containerPort = ExposedPort.tcp(spec.containerPort());
            exposedPorts.add(containerPort);
            hostConfig.withPortBindings(new PortBinding(
                    Ports.Binding.bindPort(spec.hostPort()), containerPort));
        }

        List<String> env = spec.env() == null ? List.of() : spec.env().entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
                .toList();

        CreateContainerResponse response = client.createContainerCmd(spec.image())
                .withName(spec.name())
                .withEnv(env)
                .withLabels(spec.labels())
                .withExposedPorts(exposedPorts)
                .withAliases(spec.networkAlias() == null
                        ? List.of() : List.of(spec.networkAlias()))
                .withHostConfig(hostConfig)
                .exec();
        return response.getId();
    }

    @Override
    public void startContainer(String containerId) {
        client.startContainerCmd(containerId).exec();
    }

    @Override
    public void stopContainer(String containerId) {
        try {
            client.stopContainerCmd(containerId).withTimeout(STOP_TIMEOUT_SECONDS).exec();
        } catch (NotFoundException | NotModifiedException e) {
            // 容器不存在或已停止：視為成功（冪等）
        }
    }

    @Override
    public void removeContainer(String containerId) {
        try {
            client.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException e) {
            // 已不存在：冪等
        }
    }

    @Override
    public List<String> findContainerIdsByLabel(String labelKey, String labelValue) {
        return client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(labelKey, labelValue))
                .exec().stream()
                .map(container -> container.getId())
                .toList();
    }

    @Override
    public void removeImagesByLabel(String labelKey, String labelValue) {
        List<Image> images = client.listImagesCmd().exec();
        for (Image image : images) {
            Map<String, String> labels = image.getLabels();
            if (labels != null && labelValue.equals(labels.get(labelKey))) {
                try {
                    client.removeImageCmd(image.getId()).withForce(true).exec();
                } catch (NotFoundException e) {
                    // 已不存在：冪等
                }
            }
        }
    }

    @Override
    public String inspectStatus(String containerId) {
        try {
            return client.inspectContainerCmd(containerId).exec().getState().getStatus();
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Override
    public String tailLogs(String containerId, int lines) {
        StringBuilder out = new StringBuilder();
        try {
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            out.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    })
                    .awaitCompletion(LOGS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讀取容器 log 被中斷", e);
        } catch (NotFoundException e) {
            return "";
        }
        return out.toString();
    }

    /**
     * 將檔案集合打包為 tar（build context）。使用 docker-java 傳遞依賴的
     * commons-compress；GNU long-name 模式支援長路徑。
     */
    private byte[] toTar(Map<String, byte[]> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar =
                     new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(out)) {
            tar.setLongFileMode(
                    org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                org.apache.commons.compress.archivers.tar.TarArchiveEntry entry =
                        new org.apache.commons.compress.archivers.tar.TarArchiveEntry(file.getKey());
                entry.setSize(file.getValue().length);
                tar.putArchiveEntry(entry);
                tar.write(file.getValue());
                tar.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("建立 build context tar 失敗", e);
        }
        return out.toByteArray();
    }
}
