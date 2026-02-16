package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.flow.dto.export.ComponentDependency;
import com.aiinpocket.n3n.flow.dto.export.CredentialPlaceholder;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Flow 匯出服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowExportService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 匯出流程（最新版本）
     */
    @Transactional(readOnly = true)
    public FlowExportPackage exportFlowLatest(UUID flowId, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        List<FlowVersion> versions = flowVersionRepository.findByFlowIdOrderByCreatedAtDesc(flowId);
        if (versions.isEmpty()) {
            throw new ResourceNotFoundException("No versions found for flow: " + flowId);
        }
        FlowVersion latestVersion = versions.get(0);

        return exportFlow(flowId, latestVersion.getVersion(), userId);
    }

    /**
     * 匯出流程
     */
    @Transactional(readOnly = true)
    public FlowExportPackage exportFlow(UUID flowId, String version, UUID userId) {
        Flow flow = flowRepository.findByIdAndIsDeletedFalse(flowId)
                .orElseThrow(() -> new ResourceNotFoundException("Flow not found: " + flowId));

        FlowVersion flowVersion = flowVersionRepository.findByFlowIdAndVersion(flowId, version)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + version));

        Map<String, Object> definition = flowVersion.getDefinition();
        if (definition == null) {
            definition = Map.of();
        }

        // 提取元件依賴
        List<ComponentDependency> components = extractComponentDependencies(definition);

        // 提取憑證佔位符
        List<CredentialPlaceholder> credentialPlaceholders = extractCredentialPlaceholders(definition);

        // 取得匯出者 email（遮罩處理）
        String exportedBy = maskEmail(userId);

        // 建立匯出包
        FlowExportPackage pkg = FlowExportPackage.builder()
                .version("1.0")
                .exportedAt(Instant.now())
                .exportedBy(exportedBy)
                .flow(FlowExportPackage.FlowData.builder()
                        .name(flow.getName())
                        .description(flow.getDescription())
                        .definition(definition)
                        .settings(flowVersion.getSettings())
                        .build())
                .dependencies(FlowExportPackage.FlowDependencies.builder()
                        .components(components)
                        .credentialPlaceholders(credentialPlaceholders)
                        .build())
                .build();

        // 計算 checksum
        pkg.setChecksum(calculateChecksum(pkg));

        log.info("Flow exported: flowId={}, version={}, by={}", flowId, version, userId);
        return pkg;
    }

    /**
     * 提取元件依賴
     */
    @SuppressWarnings("unchecked")
    private List<ComponentDependency> extractComponentDependencies(Map<String, Object> definition) {
        List<ComponentDependency> dependencies = new ArrayList<>();
        Set<String> processedComponents = new HashSet<>();

        Object nodesObj = definition.get("nodes");
        if (!(nodesObj instanceof List)) {
            return dependencies;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;

        for (Map<String, Object> node : nodes) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null) continue;

            String componentName = (String) data.get("componentName");
            String componentVersion = (String) data.get("componentVersion");

            if (componentName == null) continue;

            String key = componentName + ":" + (componentVersion != null ? componentVersion : "latest");
            if (processedComponents.contains(key)) continue;
            processedComponents.add(key);

            // 建立元件依賴資訊
            // 注意：這裡簡化處理，實際應該從 ComponentRepository 取得完整資訊
            ComponentDependency dep = ComponentDependency.builder()
                    .name(componentName)
                    .version(componentVersion != null ? componentVersion : "latest")
                    .displayName((String) data.get("label"))
                    .category((String) data.get("category"))
                    .build();

            dependencies.add(dep);
        }

        return dependencies;
    }

    /**
     * 提取憑證佔位符
     */
    @SuppressWarnings("unchecked")
    private List<CredentialPlaceholder> extractCredentialPlaceholders(Map<String, Object> definition) {
        List<CredentialPlaceholder> placeholders = new ArrayList<>();

        Object nodesObj = definition.get("nodes");
        if (!(nodesObj instanceof List)) {
            return placeholders;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;

        // Collect all credential IDs first, then batch-load (avoids N+1)
        Map<String, List<Map<String, Object>>> nodesByCredentialId = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null) continue;
            String credentialId = (String) data.get("credentialId");
            if (credentialId == null) continue;
            nodesByCredentialId.computeIfAbsent(credentialId, k -> new ArrayList<>()).add(node);
        }

        if (nodesByCredentialId.isEmpty()) {
            return placeholders;
        }

        // Batch load all credentials in one query
        Set<UUID> credentialUuids = new HashSet<>();
        for (String id : nodesByCredentialId.keySet()) {
            try {
                credentialUuids.add(UUID.fromString(id));
            } catch (Exception e) {
                log.warn("Invalid credential ID format: {}", id);
            }
        }
        Map<UUID, Credential> credentialMap = new HashMap<>();
        if (!credentialUuids.isEmpty()) {
            credentialRepository.findAllById(credentialUuids).forEach(c -> credentialMap.put(c.getId(), c));
        }

        // Build placeholders using batch-loaded credentials
        for (var entry : nodesByCredentialId.entrySet()) {
            String credentialId = entry.getKey();
            for (Map<String, Object> node : entry.getValue()) {
                String nodeId = (String) node.get("id");
                Map<String, Object> data = (Map<String, Object>) node.get("data");

                String credentialName = "Unknown";
                String credentialType = "unknown";

                try {
                    Credential credential = credentialMap.get(UUID.fromString(credentialId));
                    if (credential != null) {
                        credentialName = credential.getType() + " Credential";
                        credentialType = credential.getType();
                    }
                } catch (Exception e) {
                    log.warn("Failed to load credential info for {}: {}", credentialId, e.getMessage());
                }

                CredentialPlaceholder placeholder = CredentialPlaceholder.builder()
                        .nodeId(nodeId)
                        .nodeName((String) data.get("label"))
                        .credentialType(credentialType)
                        .credentialName(credentialName)
                        .description("Required for " + data.get("label"))
                        .required(true)
                        .build();

                placeholders.add(placeholder);
            }
        }

        return placeholders;
    }

    /**
     * 遮罩 email
     */
    private String maskEmail(UUID userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                String email = userOpt.get().getEmail();
                int atIndex = email.indexOf('@');
                if (atIndex > 2) {
                    return email.substring(0, 2) + "***" + email.substring(atIndex);
                }
                return "***" + email.substring(atIndex);
            }
        } catch (Exception e) {
            log.warn("Failed to get user email: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * 計算 checksum
     */
    private String calculateChecksum(FlowExportPackage pkg) {
        try {
            // 只對 flow 和 dependencies 計算 checksum
            Map<String, Object> content = new HashMap<>();
            if (pkg.getFlow() != null) {
                content.put("flow", pkg.getFlow());
            }
            if (pkg.getDependencies() != null) {
                content.put("dependencies", pkg.getDependencies());
            }

            String json = objectMapper.writeValueAsString(content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                // Use String.format for consistent hex conversion (preserves leading zeros)
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }
}
