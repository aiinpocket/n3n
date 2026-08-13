package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import com.aiinpocket.n3n.hostedapp.dto.ServiceSpec;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * docker-compose.yml 解析為 AppManifest。
 *
 * 安全驗證（任一違反即整包拒絕）：
 *   - privileged / cap_add / devices / network_mode / pid / ipc 一律禁止
 *   - volumes 僅允許 named volume；host path bind mount 一律拒絕
 *   - 服務數上限 4
 *   - ports 的 host 側一律忽略（對外埠由平台配置），僅取容器內埠
 */
@Service
public class ComposeManifestParser {

    private static final int MAX_SERVICES = 4;

    /**
     * @param yamlContent compose 檔內容
     * @param files       zip 完整內容（驗證 build context 是否存在 Dockerfile）
     */
    public AppManifest parse(String yamlContent, Map<String, byte[]> files) {
        Map<String, Object> root = loadYaml(yamlContent);
        Map<String, Object> services = asMap(root.get("services"));
        if (services.isEmpty()) {
            throw new IllegalArgumentException("compose 檔內沒有任何 services 定義");
        }
        if (services.size() > MAX_SERVICES) {
            throw new IllegalArgumentException(
                    "compose 服務數量過多（" + services.size() + "，上限 " + MAX_SERVICES + "）");
        }

        List<ServiceSpec> specs = new ArrayList<>();
        ParamCollector params = new ParamCollector();
        String webService = null;
        Integer internalPort = null;

        for (Map.Entry<String, Object> entry : services.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> service = asMap(entry.getValue());
            rejectForbiddenKeys(name, service);
            rejectHostBindVolumes(name, service.get("volumes"));

            ServiceSpec spec = toServiceSpec(name, service, files);
            specs.add(spec);
            collectParams(params, spec.environment());

            if (webService == null && !spec.ports().isEmpty()) {
                webService = name;
                internalPort = spec.ports().get(0);
            }
        }

        return AppManifest.builder()
                .type(AppManifest.TYPE_COMPOSE)
                .services(List.copyOf(specs))
                .params(params.toList())
                .webService(webService)
                .internalPort(internalPort)
                .build();
    }

    // ---------- 解析 ----------

    private Map<String, Object> loadYaml(String content) {
        try {
            // SafeConstructor：僅還原基本型別，杜絕 YAML 反序列化 gadget
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object root = yaml.load(content);
            Map<String, Object> map = asMap(root);
            if (map.isEmpty()) {
                throw new IllegalArgumentException("compose 檔內容為空或不是 YAML 物件");
            }
            return map;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("compose 檔不是合法的 YAML: " + e.getMessage(), e);
        }
    }

    private ServiceSpec toServiceSpec(String name, Map<String, Object> service,
                                      Map<String, byte[]> files) {
        String image = service.get("image") == null ? null : String.valueOf(service.get("image"));
        String build = parseBuildContext(service.get("build"));
        if (image == null && build == null) {
            throw new IllegalArgumentException(
                    "服務 " + name + " 缺少 image 或 build，無法部署");
        }
        if (build != null) {
            validateBuildContext(name, build, files);
        }
        return ServiceSpec.builder()
                .name(name)
                .image(image)
                .build(build)
                .ports(parseContainerPorts(name, service.get("ports")))
                .environment(parseEnvironment(name, service.get("environment")))
                .dependsOn(parseDependsOn(service.get("depends_on")))
                .build();
    }

    /** build 可為字串（context 路徑）或物件（取 context 欄位） */
    private String parseBuildContext(Object build) {
        if (build == null) {
            return null;
        }
        String context;
        if (build instanceof Map<?, ?> map) {
            Object ctx = map.get("context");
            context = ctx == null ? "." : String.valueOf(ctx);
        } else {
            context = String.valueOf(build);
        }
        return normalizeContext(context);
    }

    private String normalizeContext(String context) {
        String normalized = context.trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isEmpty() || normalized.equals(".")) {
            return ".";
        }
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException(
                    "build context 只能是 zip 內的相對路徑: " + context);
        }
        return normalized;
    }

    private void validateBuildContext(String name, String context, Map<String, byte[]> files) {
        String dockerfilePath = ".".equals(context) ? "Dockerfile" : context + "/Dockerfile";
        if (!files.containsKey(dockerfilePath)) {
            throw new IllegalArgumentException(
                    "服務 " + name + " 的 build context（" + context + "）內找不到 Dockerfile");
        }
    }

    /**
     * ports 支援短語法（"8080:80"、"80"、"127.0.0.1:8080:80"、80、"80/tcp"）
     * 與長語法（target/published 物件）。host 側一律忽略，只取容器內埠。
     */
    private List<Integer> parseContainerPorts(String name, Object ports) {
        List<Integer> result = new ArrayList<>();
        if (ports == null) {
            return result;
        }
        if (!(ports instanceof List<?> list)) {
            throw new IllegalArgumentException("服務 " + name + " 的 ports 格式不正確");
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object target = map.get("target");
                if (target != null) {
                    result.add(parsePort(name, String.valueOf(target)));
                }
                continue;
            }
            String value = String.valueOf(item).trim();
            String withoutProto = value.split("/")[0];
            String[] segments = withoutProto.split(":");
            // 短語法最後一段即容器內埠（[host:]container 或 [ip:host:]container）
            result.add(parsePort(name, segments[segments.length - 1]));
        }
        return result;
    }

    private int parsePort(String name, String raw) {
        try {
            // 埠範圍語法（"3000-3005"）取起始埠
            int port = Integer.parseInt(raw.split("-")[0].trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "服務 " + name + " 的 port 不是合法數字: " + raw);
        }
    }

    /** environment 支援 map 形式與 "KEY=value" / "KEY" 清單形式 */
    private Map<String, String> parseEnvironment(String name, Object environment) {
        Map<String, String> env = new LinkedHashMap<>();
        if (environment == null) {
            return env;
        }
        if (environment instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                env.put(String.valueOf(entry.getKey()),
                        value == null ? null : String.valueOf(value));
            }
            return env;
        }
        if (environment instanceof List<?> list) {
            for (Object item : list) {
                String line = String.valueOf(item);
                int eq = line.indexOf('=');
                if (eq < 0) {
                    env.put(line.trim(), null);
                } else {
                    env.put(line.substring(0, eq).trim(), line.substring(eq + 1));
                }
            }
            return env;
        }
        throw new IllegalArgumentException("服務 " + name + " 的 environment 格式不正確");
    }

    private List<String> parseDependsOn(Object dependsOn) {
        if (dependsOn == null) {
            return List.of();
        }
        if (dependsOn instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (dependsOn instanceof Map<?, ?> map) {
            return map.keySet().stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** 環境變數 → 參數：空值/null 為必填；${...} 佔位符依形式判定 */
    private void collectParams(ParamCollector params, Map<String, String> environment) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                params.add(ParamSpec.builder()
                        .name(entry.getKey())
                        .required(true)
                        .secret(ParamSpec.isSecretName(entry.getKey()))
                        .build());
                continue;
            }
            params.addAll(ComposePlaceholders.extract(value));
        }
    }

    // ---------- 安全驗證 ----------

    private void rejectForbiddenKeys(String name, Map<String, Object> service) {
        if (isTruthy(service.get("privileged"))) {
            throw new IllegalArgumentException(
                    "服務 " + name + " 要求 privileged 模式，沙盒環境不允許");
        }
        rejectPresent(name, service, "cap_add", "額外的 Linux capability");
        rejectPresent(name, service, "devices", "存取主機裝置");
        rejectPresent(name, service, "network_mode", "自訂 network_mode（含 host 網路）");
        rejectPresent(name, service, "pid", "共享主機 PID namespace");
        rejectPresent(name, service, "ipc", "共享主機 IPC namespace");
        rejectPresent(name, service, "security_opt", "自訂 security_opt");
        rejectPresent(name, service, "userns_mode", "自訂 user namespace");
    }

    private void rejectPresent(String name, Map<String, Object> service, String key, String reason) {
        if (service.containsKey(key)) {
            throw new IllegalArgumentException(
                    "服務 " + name + " 使用了 " + key + "（" + reason + "），沙盒環境不允許");
        }
    }

    /** volumes 僅允許 named volume；任何 host path bind 一律拒絕 */
    private void rejectHostBindVolumes(String name, Object volumes) {
        if (volumes == null) {
            return;
        }
        if (!(volumes instanceof List<?> list)) {
            throw new IllegalArgumentException("服務 " + name + " 的 volumes 格式不正確");
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                // 長語法：type: bind 一律拒絕
                if ("bind".equals(map.get("type"))) {
                    throw new IllegalArgumentException(
                            "服務 " + name + " 掛載了主機路徑（bind mount），沙盒環境不允許");
                }
                continue;
            }
            String value = String.valueOf(item).trim();
            String source = value.split(":")[0];
            if (source.startsWith("/") || source.startsWith(".") || source.startsWith("~")) {
                throw new IllegalArgumentException(
                        "服務 " + name + " 掛載了主機路徑（" + source + "），僅允許 named volume");
            }
        }
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("compose 檔結構不正確（預期為物件）");
    }
}
