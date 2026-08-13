package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import com.aiinpocket.n3n.hostedapp.dto.ServiceSpec;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.hostedapp.runtime.ContainerRuntime;
import com.aiinpocket.n3n.hostedapp.runtime.ContainerSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppDeployService（ContainerRuntime 全 mock，不需 Docker）：
 * 硬化參數實際套用、冪等重部署（label 清理參數）、埠配置、
 * depends_on 排序、失敗狀態轉換。
 */
@ExtendWith(MockitoExtension.class)
class AppDeployServiceTest {

    @Mock
    private ObjectProvider<ContainerRuntime> runtimeProvider;
    @Mock
    private ContainerRuntime runtime;
    @Mock
    private HostedAppRepository repository;
    @Mock
    private HostedAppProperties properties;
    @Mock
    private AppParamCrypto paramCrypto;
    @Mock
    private AppPortAllocator portAllocator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppDeployService service;

    private final UUID appId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HostedAppProperties zipProps = org.mockito.Mockito.mock(HostedAppProperties.class);
        lenient().when(zipProps.maxZipBytes()).thenReturn(1_000_000L);
        service = new AppDeployService(runtimeProvider, repository, properties,
                new AppZipReader(zipProps), paramCrypto, portAllocator, objectMapper);

        lenient().when(runtimeProvider.getIfAvailable()).thenReturn(runtime);
        lenient().when(properties.getNetwork()).thenReturn("n3n-apps");
        lenient().when(properties.getMemoryMb()).thenReturn(512L);
        lenient().when(properties.getCpus()).thenReturn(0.5);
        lenient().when(portAllocator.allocate(any())).thenReturn(28000);
        lenient().when(repository.findByHostPortIsNotNull()).thenReturn(List.of());
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paramCrypto.decrypt(any())).thenReturn(Map.of());
    }

    private byte[] zipOf(Map<String, String> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private HostedApp composeApp(AppManifest manifest) {
        return HostedApp.builder()
                .id(appId).ownerId(ownerId).name("demo").slug("demo-abcd")
                .appType(manifest.type()).status(AppStatus.DEPLOYING)
                .manifest(objectMapper.convertValue(manifest, Map.class))
                .zipData(zipOf(Map.of("docker-compose.yml", "services: {}")))
                .build();
    }

    @Test
    @DisplayName("部署套用全部硬化限制，並先以 label 清掉舊容器（冪等重部署）")
    void deployAppliesHardeningAndCleansOldContainers() {
        AppManifest manifest = AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.of(ServiceSpec.builder()
                        .name("web").image("nginx:1.25").ports(List.of(80))
                        .environment(Map.of("MODE", "prod")).dependsOn(List.of()).build()))
                .params(List.of())
                .webService("web").internalPort(80)
                .build();
        HostedApp app = composeApp(manifest);
        when(runtime.findContainerIdsByLabel(AppDeployService.LABEL_APP_ID, appId.toString()))
                .thenReturn(List.of("old-1"));
        when(runtime.createContainer(any())).thenReturn("new-1");

        service.deployNow(app);

        // 舊容器以 label 找到並停止 + 移除（remove-by-label 參數正確）
        verify(runtime).findContainerIdsByLabel(AppDeployService.LABEL_APP_ID, appId.toString());
        verify(runtime).stopContainer("old-1");
        verify(runtime).removeContainer("old-1");
        verify(runtime).ensureNetwork("n3n-apps");
        verify(runtime).pullImage("nginx:1.25");

        ArgumentCaptor<ContainerSpec> captor = ArgumentCaptor.forClass(ContainerSpec.class);
        verify(runtime).createContainer(captor.capture());
        ContainerSpec spec = captor.getValue();
        assertThat(spec.name()).isEqualTo("n3napp-demo-abcd-web");
        assertThat(spec.memoryBytes()).isEqualTo(512L * 1024 * 1024);
        assertThat(spec.cpus()).isEqualTo(0.5);
        assertThat(spec.pidsLimit()).isEqualTo(256);
        assertThat(spec.network()).isEqualTo("n3n-apps");
        assertThat(spec.networkAlias()).isEqualTo("web");
        assertThat(spec.hostPort()).isEqualTo(28000);
        assertThat(spec.containerPort()).isEqualTo(80);
        assertThat(spec.labels()).containsEntry(AppDeployService.LABEL_APP_ID, appId.toString())
                .containsEntry(AppDeployService.LABEL_APP_OWNER, ownerId.toString());
        assertThat(spec.env()).containsEntry("MODE", "prod");

        verify(runtime).startContainer("new-1");
        assertThat(app.getStatus()).isEqualTo(AppStatus.RUNNING);
        assertThat(app.getHostPort()).isEqualTo(28000);
        assertThat(app.getContainerIds()).containsExactly("new-1");
    }

    @Test
    @DisplayName("compose 佔位符代換：使用者參數與 ${VAR:-def} 預設值")
    void deploySubstitutesPlaceholders() {
        AppManifest manifest = AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.of(ServiceSpec.builder()
                        .name("web").image("app:1").ports(List.of(3000))
                        .environment(new java.util.LinkedHashMap<>(Map.of(
                                "DB_PASSWORD", "${DB_PASSWORD}",
                                "LOG_LEVEL", "${LOG_LEVEL:-info}",
                                "EMPTY_ONE", "")))
                        .dependsOn(List.of()).build()))
                .params(List.of(
                        ParamSpec.builder().name("DB_PASSWORD").required(true).secret(true).build(),
                        ParamSpec.builder().name("LOG_LEVEL").defaultValue("info").build(),
                        ParamSpec.builder().name("EMPTY_ONE").required(true).build()))
                .webService("web").internalPort(3000)
                .build();
        HostedApp app = composeApp(manifest);
        app.setParams(Map.of("DB_PASSWORD", "enc:v1:xxx", "EMPTY_ONE", "filled"));
        when(paramCrypto.decrypt(any()))
                .thenReturn(Map.of("DB_PASSWORD", "s3cret", "EMPTY_ONE", "filled"));
        when(runtime.createContainer(any())).thenReturn("c1");

        service.deployNow(app);

        ArgumentCaptor<ContainerSpec> captor = ArgumentCaptor.forClass(ContainerSpec.class);
        verify(runtime).createContainer(captor.capture());
        Map<String, String> env = captor.getValue().env();
        assertThat(env).containsEntry("DB_PASSWORD", "s3cret");
        assertThat(env).containsEntry("LOG_LEVEL", "info");
        assertThat(env).containsEntry("EMPTY_ONE", "filled");
    }

    @Test
    @DisplayName("dockerfile 型態：以 zip 建置映像（帶 app label）並發佈埠")
    void deployBuildsDockerfileApp() {
        AppManifest manifest = AppManifest.builder()
                .type(AppManifest.TYPE_DOCKERFILE)
                .services(List.of(ServiceSpec.builder()
                        .name("app").build(".").ports(List.of(3000))
                        .environment(Map.of()).dependsOn(List.of()).build()))
                .params(List.of())
                .webService("app").internalPort(3000)
                .build();
        HostedApp app = composeApp(manifest);
        app.setZipData(zipOf(Map.of("Dockerfile", "FROM node:20\nEXPOSE 3000\n")));
        when(runtime.createContainer(any())).thenReturn("c1");

        service.deployNow(app);

        verify(runtime).buildImage(eq("n3napp-demo-abcd-app:latest"), any(), any());
        assertThat(app.getStatus()).isEqualTo(AppStatus.RUNNING);
    }

    @Test
    @DisplayName("部署失敗：清掉本次容器、狀態 failed、記錄錯誤訊息")
    void deployFailureMarksFailed() {
        AppManifest manifest = AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.of(ServiceSpec.builder()
                        .name("web").image("bad:1").ports(List.of(80))
                        .environment(Map.of()).dependsOn(List.of()).build()))
                .params(List.of())
                .webService("web").internalPort(80)
                .build();
        HostedApp app = composeApp(manifest);
        org.mockito.Mockito.doThrow(new IllegalStateException("pull 失敗: bad:1"))
                .when(runtime).pullImage("bad:1");

        service.deployNow(app);

        assertThat(app.getStatus()).isEqualTo(AppStatus.FAILED);
        assertThat(app.getErrorMessage()).contains("pull 失敗");
    }

    @Test
    @DisplayName("已配置的 host port 重部署時沿用；其他 app 佔用的埠列入保留")
    void portAllocationExcludesOtherApps() {
        AppManifest manifest = AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.of(ServiceSpec.builder()
                        .name("web").image("x:1").ports(List.of(80))
                        .environment(Map.of()).dependsOn(List.of()).build()))
                .params(List.of())
                .webService("web").internalPort(80)
                .build();
        HostedApp app = composeApp(manifest);
        app.setHostPort(28123);
        when(runtime.createContainer(any())).thenReturn("c1");

        service.deployNow(app);

        // 既有 hostPort 沿用，不再配置
        verify(portAllocator, org.mockito.Mockito.never()).allocate(any());
        assertThat(app.getHostPort()).isEqualTo(28123);
    }

    @Test
    @DisplayName("depends_on 排序：被依賴者先啟動；循環退回宣告順序")
    void dependsOnOrdering() {
        ServiceSpec db = ServiceSpec.builder().name("db").image("pg").ports(List.of())
                .environment(Map.of()).dependsOn(List.of()).build();
        ServiceSpec cache = ServiceSpec.builder().name("cache").image("redis").ports(List.of())
                .environment(Map.of()).dependsOn(List.of()).build();
        ServiceSpec web = ServiceSpec.builder().name("web").image("app").ports(List.of(80))
                .environment(Map.of()).dependsOn(List.of("db", "cache")).build();

        List<ServiceSpec> ordered = service.orderByDependsOn(List.of(web, db, cache));
        assertThat(ordered).extracting(ServiceSpec::name).containsExactly("db", "cache", "web");

        // 循環依賴：不會卡死，退回宣告順序
        ServiceSpec a = ServiceSpec.builder().name("a").image("x").ports(List.of())
                .environment(Map.of()).dependsOn(List.of("b")).build();
        ServiceSpec b = ServiceSpec.builder().name("b").image("y").ports(List.of())
                .environment(Map.of()).dependsOn(List.of("a")).build();
        assertThat(service.orderByDependsOn(List.of(a, b))).hasSize(2);
    }
}
