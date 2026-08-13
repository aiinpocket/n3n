package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import com.aiinpocket.n3n.hostedapp.dto.ServiceSpec;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HostedAppService：功能旗標關閉行為、必填參數驗證、擁有者隔離（404）、
 * 每人數量上限、狀態轉換、移除委派。
 */
@ExtendWith(MockitoExtension.class)
class HostedAppServiceTest {

    @Mock
    private HostedAppRepository repository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private HostedAppProperties properties;
    @Mock
    private AppZipAnalyzer analyzer;
    @Mock
    private AppParamCrypto paramCrypto;
    @Mock
    private AppDeployService deployService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HostedAppService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID appId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HostedAppService(repository, siteRepository, properties, analyzer,
                paramCrypto, deployService, objectMapper);
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getMaxPerUser()).thenReturn(2);
    }

    private AppManifest manifest(ParamSpec... params) {
        return AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.of(ServiceSpec.builder()
                        .name("web").image("nginx").ports(List.of(80))
                        .environment(Map.of()).dependsOn(List.of()).build()))
                .params(List.of(params))
                .webService("web")
                .internalPort(80)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manifestMap(AppManifest manifest) {
        return objectMapper.convertValue(manifest, Map.class);
    }

    private HostedApp app(String status, AppManifest manifest) {
        return HostedApp.builder()
                .id(appId).ownerId(ownerId).name("demo").slug("demo-abcd")
                .appType("compose").status(status)
                .manifest(manifestMap(manifest))
                .zipData(new byte[]{1})
                .build();
    }

    // ---------- 功能旗標 ----------

    @Test
    @DisplayName("功能關閉：所有操作一律 404（不洩漏功能存在性）")
    void featureDisabledReturns404() {
        when(properties.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.list(ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.analyze(new byte[]{1}))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.create(ownerId, "demo", new byte[]{1}))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deploy(appId, ownerId, Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.remove(appId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.isEnabled()).isFalse();
    }

    // ---------- 建立 ----------

    @Test
    @DisplayName("每人應用數量達上限即拒絕建立")
    void perUserLimitEnforced() {
        when(repository.countByOwnerId(ownerId)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(ownerId, "demo", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上限");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("建立成功：manifest 持久化、狀態 created")
    void createPersistsManifest() {
        when(repository.countByOwnerId(ownerId)).thenReturn(0L);
        when(repository.existsBySlug(any())).thenReturn(false);
        when(analyzer.analyze(any(byte[].class))).thenReturn(manifest());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HostedApp app = service.create(ownerId, "My App", new byte[]{1, 2});

        assertThat(app.getStatus()).isEqualTo(AppStatus.CREATED);
        assertThat(app.getAppType()).isEqualTo("compose");
        assertThat(app.getManifest()).isNotNull();
        assertThat(app.getInternalPort()).isEqualTo(80);
        assertThat(app.getSlug()).startsWith("my-app-");
    }

    @Test
    @DisplayName("slug 與站台碰撞：sites 已占用的 slug 不可用（雙向命名空間互斥）")
    void slugCollidingWithSiteIsRejected() {
        when(repository.countByOwnerId(ownerId)).thenReturn(0L);
        when(analyzer.analyze(any(byte[].class))).thenReturn(manifest());
        when(repository.existsBySlug(any())).thenReturn(false);
        // 站台側永遠回「已存在」→ 所有候選 slug 都被判定碰撞
        when(siteRepository.existsBySlug(any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(ownerId, "My App", new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("無法產生唯一");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("slug 未與站台碰撞時正常建立（有查過 sites）")
    void slugChecksSiteNamespace() {
        when(repository.countByOwnerId(ownerId)).thenReturn(0L);
        when(analyzer.analyze(any(byte[].class))).thenReturn(manifest());
        when(repository.existsBySlug(any())).thenReturn(false);
        when(siteRepository.existsBySlug(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HostedApp app = service.create(ownerId, "My App", new byte[]{1});

        assertThat(app.getSlug()).startsWith("my-app-");
        verify(siteRepository).existsBySlug(app.getSlug());
    }

    // ---------- 擁有者隔離 ----------

    @Test
    @DisplayName("非擁有者存取一律 404")
    void ownershipEnforced() {
        when(repository.findByIdAndOwnerId(appId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(appId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deploy(appId, ownerId, Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.logs(appId, ownerId, 100))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- 部署 ----------

    @Test
    @DisplayName("缺少必填參數：拒絕部署並點名缺哪些")
    void missingRequiredParamsRejected() {
        AppManifest manifest = manifest(
                ParamSpec.builder().name("DB_PASSWORD").required(true).secret(true).build(),
                ParamSpec.builder().name("LOG_LEVEL").defaultValue("info").build());
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.CREATED, manifest)));

        assertThatThrownBy(() -> service.deploy(appId, ownerId, Map.of("LOG_LEVEL", "debug")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DB_PASSWORD");
        verify(deployService, never()).deployAsync(any());
    }

    @Test
    @DisplayName("部署成功：秘密參數加密、狀態 deploying、觸發非同步部署")
    void deployEncryptsAndGoesAsync() {
        AppManifest manifest = manifest(
                ParamSpec.builder().name("DB_PASSWORD").required(true).secret(true).build());
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.CREATED, manifest)));
        when(paramCrypto.encryptSecrets(anyMap(), anyList()))
                .thenReturn(Map.of("DB_PASSWORD", "enc:v1:xxx"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HostedApp result = service.deploy(appId, ownerId, Map.of("DB_PASSWORD", "s3cret"));

        assertThat(result.getStatus()).isEqualTo(AppStatus.DEPLOYING);
        assertThat(result.getParams()).containsEntry("DB_PASSWORD", "enc:v1:xxx");
        verify(paramCrypto).encryptSecrets(Map.of("DB_PASSWORD", "s3cret"), manifest.params());
        verify(deployService).deployAsync(appId);
    }

    @Test
    @DisplayName("部署中不可重複部署")
    void deployingStateRejectsRedeploy() {
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.DEPLOYING, manifest())));

        assertThatThrownBy(() -> service.deploy(appId, ownerId, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("部署中");
    }

    // ---------- 狀態轉換 ----------

    @Test
    @DisplayName("stop：僅 running 可停止；start：僅 stopped 可啟動")
    void statusTransitions() {
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.RUNNING, manifest())));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HostedApp stopped = service.stop(appId, ownerId);
        assertThat(stopped.getStatus()).isEqualTo(AppStatus.STOPPED);
        verify(deployService).stopApp(any());

        // 已停止的 app 不可再 stop
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.STOPPED, manifest())));
        assertThatThrownBy(() -> service.stop(appId, ownerId))
                .isInstanceOf(IllegalStateException.class);

        HostedApp started = service.start(appId, ownerId);
        assertThat(started.getStatus()).isEqualTo(AppStatus.RUNNING);
        verify(deployService).startApp(any());

        // created 狀態不可 start
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.CREATED, manifest())));
        assertThatThrownBy(() -> service.start(appId, ownerId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("remove：先清容器/映像（label 篩選）再刪資料列")
    void removeDelegatesCleanup() {
        HostedApp app = app(AppStatus.RUNNING, manifest());
        when(repository.findByIdAndOwnerId(appId, ownerId)).thenReturn(Optional.of(app));

        service.remove(appId, ownerId);

        ArgumentCaptor<HostedApp> captor = ArgumentCaptor.forClass(HostedApp.class);
        verify(deployService).removeApp(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(appId);
        verify(repository).delete(app);
    }

    @Test
    @DisplayName("logs：行數限制在 1..500")
    void logsClampLines() {
        when(repository.findByIdAndOwnerId(appId, ownerId))
                .thenReturn(Optional.of(app(AppStatus.RUNNING, manifest())));
        when(deployService.logs(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("ok");

        service.logs(appId, ownerId, 9999);
        verify(deployService).logs(any(), org.mockito.ArgumentMatchers.eq(500));
    }
}
