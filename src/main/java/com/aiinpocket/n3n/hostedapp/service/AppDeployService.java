package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ServiceSpec;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.hostedapp.runtime.ContainerRuntime;
import com.aiinpocket.n3n.hostedapp.runtime.ContainerSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hosted App 的容器部署引擎：build/pull → 硬化容器 → 啟動，全部經由
 * ContainerRuntime 介面（測試 mock、不需 Docker）。部署在 Virtual Thread
 * 上非同步進行，狀態與錯誤訊息寫回 hosted_apps。
 *
 * 每個容器一律套用：memory/cpu 上限、cap-drop ALL（僅常規最小能力集）、
 * no-new-privileges、pids-limit 256、restart unless-stopped、無 bind mount、
 * 專用 bridge network（network-alias = service 名，服務間 DNS 互通）。
 * 清理一律以 label（n3n.app.id）篩選，絕不觸碰非本平台建立的容器。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppDeployService {

    public static final String LABEL_APP_ID = "n3n.app.id";
    public static final String LABEL_APP_OWNER = "n3n.app.owner";

    private static final long PIDS_LIMIT = 256;

    private final ObjectProvider<ContainerRuntime> runtimeProvider;
    private final HostedAppRepository repository;
    private final HostedAppProperties properties;
    private final AppZipReader zipReader;
    private final AppParamCrypto paramCrypto;
    private final AppPortAllocator portAllocator;
    private final ObjectMapper objectMapper;

    /** 非同步部署（呼叫端已將狀態設為 deploying） */
    public void deployAsync(UUID appId) {
        Thread.ofVirtual().name("app-deploy-" + appId).start(() -> {
            HostedApp app = repository.findById(appId).orElse(null);
            if (app == null) {
                log.warn("Deploy skipped, app no longer exists: {}", appId);
                return;
            }
            deployNow(app);
        });
    }

    /** 同步部署（deployAsync 的實體；package-private 供測試直接呼叫） */
    void deployNow(HostedApp app) {
        try {
            doDeploy(app);
        } catch (Exception e) {
            log.error("Hosted app deploy failed: id={}, slug={}", app.getId(), app.getSlug(), e);
            markFailed(app, e);
        }
    }

    private void doDeploy(HostedApp app) {
        ContainerRuntime runtime = requireRuntime();
        runtime.ensureNetwork(properties.getNetwork());

        // 冪等重部署：先清掉本 app 既有容器（以 label 篩選，不碰其他容器）
        removeContainersByLabel(runtime, app.getId());

        Map<String, byte[]> files = zipReader.read(app.getZipData());
        AppManifest manifest = objectMapper.convertValue(app.getManifest(), AppManifest.class);
        Map<String, String> params = resolvedParams(app, manifest);

        Integer hostPort = app.getHostPort() != null
                ? app.getHostPort()
                : portAllocator.allocate(usedPorts(app.getId()));

        Map<String, String> labels = Map.of(
                LABEL_APP_ID, app.getId().toString(),
                LABEL_APP_OWNER, app.getOwnerId().toString());

        List<String> containerIds = new ArrayList<>();
        try {
            for (ServiceSpec service : orderByDependsOn(manifest.services())) {
                String image = resolveImage(runtime, app, service, files, labels);
                boolean isWeb = service.name().equals(manifest.webService());
                ContainerSpec spec = ContainerSpec.builder()
                        .name(containerName(app.getSlug(), service.name()))
                        .image(image)
                        .network(properties.getNetwork())
                        .networkAlias(service.name())
                        .env(serviceEnv(manifest, service, params))
                        .labels(labels)
                        .memoryBytes(properties.getMemoryMb() * 1024 * 1024)
                        .cpus(properties.getCpus())
                        .pidsLimit(PIDS_LIMIT)
                        .hostPort(isWeb ? hostPort : null)
                        .containerPort(isWeb ? manifest.internalPort() : null)
                        .build();
                String containerId = runtime.createContainer(spec);
                containerIds.add(containerId);
                runtime.startContainer(containerId);
            }
        } catch (Exception e) {
            // 部分失敗：清掉這次建立的容器，避免殘留
            removeContainersByLabel(runtime, app.getId());
            throw e;
        }

        app.setContainerIds(containerIds);
        app.setHostPort(hostPort);
        app.setStatus(AppStatus.RUNNING);
        app.setErrorMessage(null);
        repository.save(app);
        log.info("Hosted app deployed: id={}, slug={}, containers={}, hostPort={}",
                app.getId(), app.getSlug(), containerIds.size(), hostPort);
    }

    /** 停止全部容器（狀態轉換由呼叫端負責） */
    public void stopApp(HostedApp app) {
        ContainerRuntime runtime = requireRuntime();
        for (String containerId : containerIdsOf(app)) {
            runtime.stopContainer(containerId);
        }
    }

    /** 啟動既有容器 */
    public void startApp(HostedApp app) {
        ContainerRuntime runtime = requireRuntime();
        for (String containerId : containerIdsOf(app)) {
            runtime.startContainer(containerId);
        }
    }

    /** 移除 app 的全部容器與我們建置的映像（一律以 label 篩選） */
    public void removeApp(HostedApp app) {
        ContainerRuntime runtime = requireRuntime();
        removeContainersByLabel(runtime, app.getId());
        runtime.removeImagesByLabel(LABEL_APP_ID, app.getId().toString());
    }

    /** 聚合全部容器的最後 N 行 log */
    public String logs(HostedApp app, int lines) {
        ContainerRuntime runtime = requireRuntime();
        StringBuilder out = new StringBuilder();
        for (String containerId : containerIdsOf(app)) {
            out.append(runtime.tailLogs(containerId, lines));
        }
        return out.toString();
    }

    // ---------- internals ----------

    private ContainerRuntime requireRuntime() {
        ContainerRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) {
            throw new IllegalStateException("容器執行環境未啟用（n3n.apps.enabled=false）");
        }
        return runtime;
    }

    private void markFailed(HostedApp app, Exception e) {
        try {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            app.setStatus(AppStatus.FAILED);
            app.setErrorMessage(message.length() > 2000 ? message.substring(0, 2000) : message);
            repository.save(app);
        } catch (Exception saveError) {
            log.error("Failed to persist failure state for app {}", app.getId(), saveError);
        }
    }

    private void removeContainersByLabel(ContainerRuntime runtime, UUID appId) {
        for (String containerId : runtime.findContainerIdsByLabel(LABEL_APP_ID, appId.toString())) {
            runtime.stopContainer(containerId);
            runtime.removeContainer(containerId);
        }
    }

    private List<String> containerIdsOf(HostedApp app) {
        return app.getContainerIds() == null ? List.of() : app.getContainerIds();
    }

    private Set<Integer> usedPorts(UUID selfId) {
        return repository.findByHostPortIsNotNull().stream()
                .filter(other -> !other.getId().equals(selfId))
                .map(HostedApp::getHostPort)
                .collect(Collectors.toSet());
    }

    /** 使用者參數（解密後）+ manifest 預設值補齊 */
    private Map<String, String> resolvedParams(HostedApp app, AppManifest manifest) {
        Map<String, String> params = new LinkedHashMap<>(paramCrypto.decrypt(app.getParams()));
        if (manifest.params() != null) {
            manifest.params().forEach(spec -> {
                if (spec.defaultValue() != null) {
                    params.putIfAbsent(spec.name(), spec.defaultValue());
                }
            });
        }
        return params;
    }

    /**
     * 服務環境變數：compose 型態取服務宣告的 environment 並代換 ${...} 佔位、
     * 空值項以使用者參數補上；dockerfile 型態直接以全部參數作為 env。
     */
    private Map<String, String> serviceEnv(AppManifest manifest, ServiceSpec service,
                                           Map<String, String> params) {
        if (AppManifest.TYPE_DOCKERFILE.equals(manifest.type())) {
            return params;
        }
        Map<String, String> env = new LinkedHashMap<>();
        Map<String, String> declared = service.environment() == null
                ? Map.of() : service.environment();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                env.put(entry.getKey(), params.getOrDefault(entry.getKey(), ""));
            } else {
                env.put(entry.getKey(), ComposePlaceholders.substitute(value, params));
            }
        }
        return env;
    }

    /** image 服務直接 pull；build 服務以 zip 內 context 建置（帶 app label） */
    private String resolveImage(ContainerRuntime runtime, HostedApp app, ServiceSpec service,
                                Map<String, byte[]> files, Map<String, String> labels) {
        if (service.image() != null) {
            runtime.pullImage(service.image());
            return service.image();
        }
        String imageTag = containerName(app.getSlug(), service.name()) + ":latest";
        runtime.buildImage(imageTag, contextFiles(files, service.build()), labels);
        return imageTag;
    }

    /** 取 build context 子目錄的檔案（路徑改為相對 context） */
    private Map<String, byte[]> contextFiles(Map<String, byte[]> files, String context) {
        if (context == null || ".".equals(context)) {
            return files;
        }
        String prefix = context.endsWith("/") ? context : context + "/";
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("build context 目錄不存在或為空: " + context);
        }
        return result;
    }

    /**
     * 依 depends_on 做簡易排序（非完整 compose 引擎）：反覆挑出依賴皆已
     * 排入的服務；偵測到循環時退回宣告順序。
     */
    List<ServiceSpec> orderByDependsOn(List<ServiceSpec> services) {
        List<ServiceSpec> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<ServiceSpec> remaining = new ArrayList<>(services);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (ServiceSpec service : new ArrayList<>(remaining)) {
                List<String> deps = service.dependsOn() == null ? List.of() : service.dependsOn();
                boolean ready = deps.stream()
                        .filter(dep -> services.stream().anyMatch(s -> s.name().equals(dep)))
                        .allMatch(placed::contains);
                if (ready) {
                    ordered.add(service);
                    placed.add(service.name());
                    remaining.remove(service);
                    progressed = true;
                }
            }
            if (!progressed) {
                log.warn("depends_on 存在循環，退回宣告順序");
                ordered.addAll(remaining);
                break;
            }
        }
        return ordered;
    }

    public static String containerName(String slug, String serviceName) {
        return "n3napp-" + slug + "-" + serviceName;
    }
}
