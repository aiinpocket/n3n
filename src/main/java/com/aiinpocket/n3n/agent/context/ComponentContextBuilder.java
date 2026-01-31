package com.aiinpocket.n3n.agent.context;

import com.aiinpocket.n3n.component.entity.Component;
import com.aiinpocket.n3n.component.entity.ComponentVersion;
import com.aiinpocket.n3n.component.repository.ComponentRepository;
import com.aiinpocket.n3n.component.repository.ComponentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 元件上下文建構器
 *
 * 負責建構 AI 可理解的元件上下文，讓 AI 知道系統中有哪些可用的元件。
 * 這是避免 AI「重複造輪子」的關鍵機制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentContextBuilder {

    private final ComponentRepository componentRepository;
    private final ComponentVersionRepository componentVersionRepository;
    private final ComponentContextCache contextCache;

    /**
     * 建構完整的元件上下文
     *
     * @return 元件上下文 Map（可直接序列化為 JSON）
     */
    public Map<String, Object> buildContext() {
        // 嘗試從快取取得
        Optional<Map<String, Object>> cached = contextCache.get();
        if (cached.isPresent()) {
            log.debug("Returning cached component context");
            return cached.get();
        }

        log.info("Building component context...");

        // 取得所有未刪除的元件
        List<Component> components = componentRepository.findByIsDeletedFalse(
            PageRequest.of(0, 1000)
        ).getContent();

        List<Map<String, Object>> registeredComponents = new ArrayList<>();
        Set<String> categories = new HashSet<>();

        for (Component component : components) {
            // 取得活躍版本
            Optional<ComponentVersion> activeVersion = componentVersionRepository
                .findByComponentIdAndStatus(component.getId(), "active");

            if (activeVersion.isEmpty()) {
                // 嘗試取得最新版本
                List<ComponentVersion> versions = componentVersionRepository
                    .findByComponentIdOrderByCreatedAtDesc(component.getId());
                if (versions.isEmpty()) {
                    continue; // 沒有任何版本，跳過
                }
                activeVersion = Optional.of(versions.get(0));
            }

            ComponentVersion version = activeVersion.get();
            Map<String, Object> componentEntry = buildComponentEntry(component, version);
            registeredComponents.add(componentEntry);

            if (component.getCategory() != null) {
                categories.add(component.getCategory());
            }
        }

        // 建構上下文
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("registeredComponents", registeredComponents);
        context.put("availableCategories", categories.stream().sorted().collect(Collectors.toList()));
        context.put("totalComponents", registeredComponents.size());

        // 快取結果
        contextCache.put(context);

        log.info("Built component context with {} components", registeredComponents.size());
        return context;
    }

    /**
     * 建構適用於 AI System Prompt 的元件描述文字
     *
     * @return 人類可讀的元件描述
     */
    public String buildContextPrompt() {
        Map<String, Object> context = buildContext();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components =
            (List<Map<String, Object>>) context.get("registeredComponents");

        if (components == null || components.isEmpty()) {
            return "目前系統中沒有已註冊的元件。你可以建議使用者建立新的元件。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下已註冊的元件來建構流程：\n\n");

        // 按類別分組
        Map<String, List<Map<String, Object>>> byCategory = components.stream()
            .collect(Collectors.groupingBy(
                c -> (String) c.getOrDefault("category", "其他")
            ));

        for (Map.Entry<String, List<Map<String, Object>>> entry : byCategory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");

            for (Map<String, Object> comp : entry.getValue()) {
                sb.append("### ").append(comp.get("name"));
                if (comp.get("displayName") != null) {
                    sb.append(" (").append(comp.get("displayName")).append(")");
                }
                sb.append("\n");

                if (comp.get("description") != null) {
                    sb.append(comp.get("description")).append("\n");
                }

                // 輸入
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> inputs =
                    (List<Map<String, Object>>) comp.get("inputs");
                if (inputs != null && !inputs.isEmpty()) {
                    sb.append("- **輸入**: ");
                    sb.append(inputs.stream()
                        .map(i -> formatPort(i))
                        .collect(Collectors.joining(", ")));
                    sb.append("\n");
                }

                // 輸出
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> outputs =
                    (List<Map<String, Object>>) comp.get("outputs");
                if (outputs != null && !outputs.isEmpty()) {
                    sb.append("- **輸出**: ");
                    sb.append(outputs.stream()
                        .map(o -> formatPort(o))
                        .collect(Collectors.joining(", ")));
                    sb.append("\n");
                }

                sb.append("\n");
            }
        }

        sb.append("\n---\n\n");
        sb.append("**重要**：優先使用以上現有元件。只有當現有元件無法滿足需求時，才建議新增元件。");
        sb.append("如果建議新元件，請說明為什麼現有元件不適用。\n");

        return sb.toString();
    }

    /**
     * 取得元件總數
     */
    public int getComponentCount() {
        return (int) componentRepository.findByIsDeletedFalse(PageRequest.of(0, 1)).getTotalElements();
    }

    /**
     * 根據名稱搜尋元件
     *
     * @param query 搜尋關鍵字
     * @return 匹配的元件列表
     */
    public List<Map<String, Object>> searchComponents(String query) {
        Map<String, Object> context = buildContext();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components =
            (List<Map<String, Object>>) context.get("registeredComponents");

        if (components == null || query == null || query.isEmpty()) {
            return components != null ? components : List.of();
        }

        String lowerQuery = query.toLowerCase();
        return components.stream()
            .filter(c -> {
                String name = (String) c.get("name");
                String displayName = (String) c.get("displayName");
                String description = (String) c.get("description");

                return (name != null && name.toLowerCase().contains(lowerQuery)) ||
                       (displayName != null && displayName.toLowerCase().contains(lowerQuery)) ||
                       (description != null && description.toLowerCase().contains(lowerQuery));
            })
            .collect(Collectors.toList());
    }

    /**
     * 建構單個元件的上下文項目
     */
    private Map<String, Object> buildComponentEntry(Component component, ComponentVersion version) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", component.getId().toString());
        entry.put("name", component.getName());
        entry.put("displayName", component.getDisplayName());
        entry.put("description", component.getDescription());
        entry.put("category", component.getCategory());
        entry.put("activeVersion", version.getVersion());

        // 解析 interfaceDef
        Map<String, Object> interfaceDef = version.getInterfaceDef();
        if (interfaceDef != null) {
            entry.put("inputs", parsePortDefinitions(interfaceDef, "inputs"));
            entry.put("outputs", parsePortDefinitions(interfaceDef, "outputs"));
        }

        // 解析 configSchema
        Map<String, Object> configSchema = version.getConfigSchema();
        if (configSchema != null) {
            entry.put("configOptions", parseConfigOptions(configSchema));
        }

        return entry;
    }

    /**
     * 解析輸入/輸出端口定義
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePortDefinitions(Map<String, Object> interfaceDef, String key) {
        Object ports = interfaceDef.get(key);
        if (ports == null) {
            return List.of();
        }

        if (ports instanceof List) {
            return ((List<Map<String, Object>>) ports).stream()
                .map(this::normalizePortDefinition)
                .collect(Collectors.toList());
        }

        // 如果是 Map 格式（name -> definition）
        if (ports instanceof Map) {
            Map<String, Object> portMap = (Map<String, Object>) ports;
            return portMap.entrySet().stream()
                .map(e -> {
                    Map<String, Object> port = new LinkedHashMap<>();
                    port.put("name", e.getKey());
                    if (e.getValue() instanceof Map) {
                        port.putAll((Map<String, Object>) e.getValue());
                    } else if (e.getValue() instanceof String) {
                        port.put("type", e.getValue());
                    }
                    return normalizePortDefinition(port);
                })
                .collect(Collectors.toList());
        }

        return List.of();
    }

    /**
     * 正規化端口定義
     */
    private Map<String, Object> normalizePortDefinition(Map<String, Object> port) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", port.getOrDefault("name", "unknown"));
        normalized.put("type", port.getOrDefault("type", "any"));
        if (port.containsKey("required")) {
            normalized.put("required", port.get("required"));
        }
        if (port.containsKey("default")) {
            normalized.put("default", port.get("default"));
        }
        if (port.containsKey("description")) {
            normalized.put("description", port.get("description"));
        }
        return normalized;
    }

    /**
     * 解析配置選項
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseConfigOptions(Map<String, Object> configSchema) {
        Object properties = configSchema.get("properties");
        if (properties == null || !(properties instanceof Map)) {
            return List.of();
        }

        Map<String, Object> propMap = (Map<String, Object>) properties;
        return propMap.entrySet().stream()
            .map(e -> {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("name", e.getKey());
                if (e.getValue() instanceof Map) {
                    Map<String, Object> propDef = (Map<String, Object>) e.getValue();
                    option.put("type", propDef.getOrDefault("type", "string"));
                    if (propDef.containsKey("default")) {
                        option.put("default", propDef.get("default"));
                    }
                    if (propDef.containsKey("description")) {
                        option.put("description", propDef.get("description"));
                    }
                }
                return option;
            })
            .collect(Collectors.toList());
    }

    /**
     * 格式化端口顯示
     */
    private String formatPort(Map<String, Object> port) {
        String name = (String) port.get("name");
        String type = (String) port.get("type");
        Boolean required = (Boolean) port.get("required");

        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (type != null) {
            sb.append(" (").append(type).append(")");
        }
        if (Boolean.FALSE.equals(required)) {
            sb.append(" [可選]");
        }
        return sb.toString();
    }
}
