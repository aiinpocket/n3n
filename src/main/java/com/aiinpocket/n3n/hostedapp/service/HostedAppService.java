package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import com.aiinpocket.n3n.hostedapp.entity.AppStatus;
import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import com.aiinpocket.n3n.hostedapp.repository.HostedAppRepository;
import com.aiinpocket.n3n.site.repository.SiteRepository;
import com.aiinpocket.n3n.site.service.SiteSlugs;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosted App 核心服務：CRUD、參數驗證、狀態轉換、擁有者隔離。
 *
 * 所有操作先過 requireEnabled()——功能關閉時一律丟 ResourceNotFoundException
 * （404，不洩漏功能存在性）。非擁有者存取同樣回 404（沿用 site 模組模式）。
 * Docker 相關動作全部委派 AppDeployService（經 ContainerRuntime 介面）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HostedAppService {

    private static final int MAX_SLUG_ATTEMPTS = 5;
    private static final int MAX_LOG_LINES = 500;

    private final HostedAppRepository repository;
    private final SiteRepository siteRepository;
    private final HostedAppProperties properties;
    private final AppZipAnalyzer analyzer;
    private final AppParamCrypto paramCrypto;
    private final AppDeployService deployService;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 功能未啟用時所有端點一律 404 */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResourceNotFoundException("小應用功能未啟用");
        }
    }

    /** 僅分析、不持久化（UI 上傳前預覽參數表單用） */
    public AppManifest analyze(byte[] zipBytes) {
        requireEnabled();
        return analyzer.analyze(zipBytes);
    }

    @Transactional
    public HostedApp create(UUID ownerId, String name, byte[] zipBytes) {
        requireEnabled();
        if (ownerId == null) {
            throw new IllegalArgumentException("擁有者不可為空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("請提供應用名稱");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("應用名稱過長（上限 200 字元）");
        }
        if (repository.countByOwnerId(ownerId) >= properties.getMaxPerUser()) {
            throw new IllegalArgumentException(
                    "已達每人應用數量上限（" + properties.getMaxPerUser() + " 個）");
        }

        AppManifest manifest = analyzer.analyze(zipBytes);
        HostedApp app = HostedApp.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(name.trim())
                .slug(generateUniqueSlug(name))
                .appType(manifest.type())
                .status(AppStatus.CREATED)
                .manifest(toMap(manifest))
                .internalPort(manifest.internalPort())
                .zipData(zipBytes)
                .build();
        app = repository.save(app);
        log.info("Hosted app created: id={}, slug={}, type={}, owner={}",
                app.getId(), app.getSlug(), app.getAppType(), ownerId);
        return app;
    }

    @Transactional(readOnly = true)
    public List<HostedApp> list(UUID ownerId) {
        requireEnabled();
        return repository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    /** 非擁有者一律 404（不洩漏存在性） */
    @Transactional(readOnly = true)
    public HostedApp getOwned(UUID id, UUID ownerId) {
        requireEnabled();
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到應用: " + id));
    }

    /**
     * 部署：驗證必填參數 → 秘密參數加密存放 → 狀態 deploying →
     * 非同步（Virtual Thread）建置與啟動容器。可重複呼叫（冪等重部署）。
     */
    @Transactional
    public HostedApp deploy(UUID id, UUID ownerId, Map<String, String> userParams) {
        HostedApp app = getOwned(id, ownerId);
        if (AppStatus.DEPLOYING.equals(app.getStatus())) {
            throw new IllegalStateException("應用正在部署中，請稍候再試");
        }

        AppManifest manifest = manifestOf(app);
        Map<String, String> params = userParams == null ? Map.of() : userParams;
        validateRequiredParams(manifest, params);

        app.setParams(paramCrypto.encryptSecrets(params, manifest.params()));
        app.setStatus(AppStatus.DEPLOYING);
        app.setErrorMessage(null);
        app = repository.save(app);

        deployService.deployAsync(app.getId());
        return app;
    }

    @Transactional
    public HostedApp stop(UUID id, UUID ownerId) {
        HostedApp app = getOwned(id, ownerId);
        if (!AppStatus.RUNNING.equals(app.getStatus())) {
            throw new IllegalStateException("僅執行中的應用可以停止（目前狀態: " + app.getStatus() + "）");
        }
        deployService.stopApp(app);
        app.setStatus(AppStatus.STOPPED);
        return repository.save(app);
    }

    @Transactional
    public HostedApp start(UUID id, UUID ownerId) {
        HostedApp app = getOwned(id, ownerId);
        if (!AppStatus.STOPPED.equals(app.getStatus())) {
            throw new IllegalStateException("僅已停止的應用可以啟動（目前狀態: " + app.getStatus() + "）");
        }
        deployService.startApp(app);
        app.setStatus(AppStatus.RUNNING);
        return repository.save(app);
    }

    /** 移除：容器與我們建置的映像（label 篩選）一併清除，再刪資料列 */
    @Transactional
    public void remove(UUID id, UUID ownerId) {
        HostedApp app = getOwned(id, ownerId);
        deployService.removeApp(app);
        repository.delete(app);
        log.info("Hosted app removed: id={}, slug={}, owner={}", id, app.getSlug(), ownerId);
    }

    @Transactional(readOnly = true)
    public String logs(UUID id, UUID ownerId, Integer lines) {
        HostedApp app = getOwned(id, ownerId);
        int tail = lines == null ? 200 : Math.max(1, Math.min(lines, MAX_LOG_LINES));
        return deployService.logs(app, tail);
    }

    // ---------- internals ----------

    private void validateRequiredParams(AppManifest manifest, Map<String, String> params) {
        if (manifest.params() == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (ParamSpec spec : manifest.params()) {
            if (spec.required()) {
                String value = params.get(spec.name());
                if (value == null || value.isBlank()) {
                    missing.add(spec.name());
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少必填參數: " + String.join(", ", missing));
        }
    }

    private AppManifest manifestOf(HostedApp app) {
        if (app.getManifest() == null) {
            throw new IllegalStateException("應用缺少 manifest，請重新上傳 zip");
        }
        return objectMapper.convertValue(app.getManifest(), AppManifest.class);
    }

    private Map<String, Object> toMap(AppManifest manifest) {
        return objectMapper.convertValue(manifest,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    /**
     * slug 規則沿用 site 模組（kebab-case + 4 碼隨機尾碼）。
     * 唯一性同時對 hosted_apps 與 sites 檢查——兩者共用同一個
     * {slug}.{base-domain} 命名空間，不得互相碰撞。
     */
    private String generateUniqueSlug(String name) {
        for (int i = 0; i < MAX_SLUG_ATTEMPTS; i++) {
            String candidate = SiteSlugs.generate(name);
            try {
                SiteSlugs.validate(candidate);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!repository.existsBySlug(candidate) && !siteRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("無法產生唯一的應用代號，請重試");
    }
}
