package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.component.repository.ComponentRepository;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.flow.dto.FlowResponse;
import com.aiinpocket.n3n.flow.dto.export.ComponentDependency;
import com.aiinpocket.n3n.flow.dto.export.CredentialPlaceholder;
import com.aiinpocket.n3n.flow.dto.export.FlowExportPackage;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportPreviewResponse;
import com.aiinpocket.n3n.flow.dto.import_.FlowImportRequest;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowImport;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowImportRepository;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flow 匯入服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowImportService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowImportRepository importRepository;
    private final CredentialRepository credentialRepository;
    private final ComponentRepository componentRepository;
    private final DagParser dagParser;
    private final ObjectMapper objectMapper;

    /**
     * 預覽匯入
     */
    @Transactional(readOnly = true)
    public FlowImportPreviewResponse previewImport(FlowExportPackage pkg, UUID userId) {
        // 驗證 checksum
        validateChecksum(pkg);

        FlowExportPackage.FlowData flowData = pkg.getFlow();
        Map<String, Object> definition = flowData.getDefinition();

        // 分析元件狀態
        List<FlowImportPreviewResponse.ComponentStatus> componentStatuses =
                analyzeComponents(pkg.getDependencies().getComponents());

        // 分析憑證需求
        List<FlowImportPreviewResponse.CredentialRequirement> credentialRequirements =
                analyzeCredentials(pkg.getDependencies().getCredentialPlaceholders(), userId);

        // 檢查阻擋因素
        List<String> blockers = new ArrayList<>();

        // 檢查是否有無法安裝的元件
        boolean hasUnresolvable = componentStatuses.stream()
                .anyMatch(c -> !c.isInstalled() && !c.isCanAutoInstall());
        if (hasUnresolvable) {
            blockers.add("有些必要元件無法自動安裝");
        }

        // 驗證 DAG
        DagParser.ParseResult parseResult = dagParser.parse(definition);
        if (!parseResult.isValid()) {
            blockers.addAll(parseResult.getErrors());
        }

        // 計算節點和邊數量
        int nodeCount = getNodeCount(definition);
        int edgeCount = getEdgeCount(definition);

        return FlowImportPreviewResponse.builder()
                .flowName(flowData.getName())
                .description(flowData.getDescription())
                .nodeCount(nodeCount)
                .edgeCount(edgeCount)
                .componentStatuses(componentStatuses)
                .credentialRequirements(credentialRequirements)
                .canImport(blockers.isEmpty())
                .blockers(blockers)
                .build();
    }

    /**
     * 執行匯入
     */
    @Transactional
    public FlowResponse importFlow(FlowImportRequest request, UUID userId) {
        FlowExportPackage pkg = request.getPackageData();

        // 驗證 checksum
        validateChecksum(pkg);

        // 決定流程名稱
        String flowName = request.getNewFlowName() != null
                ? request.getNewFlowName()
                : pkg.getFlow().getName() + " (Imported)";

        // 檢查名稱是否重複
        if (flowRepository.existsByNameAndIsDeletedFalse(flowName)) {
            flowName = flowName + " - " + System.currentTimeMillis();
        }

        // 建立流程
        Flow flow = Flow.builder()
                .name(flowName)
                .description(pkg.getFlow().getDescription())
                .createdBy(userId)
                .build();
        flow = flowRepository.save(flow);

        // 處理憑證映射
        Map<String, Object> definition = remapCredentials(
                pkg.getFlow().getDefinition(),
                request.getCredentialMappings()
        );

        // 建立版本
        FlowVersion version = FlowVersion.builder()
                .flowId(flow.getId())
                .version("1.0.0")
                .definition(definition)
                .settings(pkg.getFlow().getSettings() != null ? pkg.getFlow().getSettings() : Map.of())
                .status("draft")
                .createdBy(userId)
                .build();
        flowVersionRepository.save(version);

        // 記錄匯入
        FlowImport importRecord = FlowImport.builder()
                .flowId(flow.getId())
                .packageVersion(pkg.getVersion())
                .packageChecksum(pkg.getChecksum())
                .originalFlowName(pkg.getFlow().getName())
                .importedBy(userId)
                .credentialMappings(request.getCredentialMappings() != null
                        ? request.getCredentialMappings().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().toString()
                        ))
                        : Map.of())
                .status(FlowImport.STATUS_RESOLVED)
                .build();
        importRepository.save(importRecord);

        log.info("Flow imported: flowId={}, name={}, by={}", flow.getId(), flowName, userId);

        return FlowResponse.from(flow, "1.0.0", null);
    }

    /**
     * 驗證 checksum
     */
    private void validateChecksum(FlowExportPackage pkg) {
        if (pkg.getChecksum() == null) {
            throw new IllegalArgumentException("Export package is missing checksum");
        }

        try {
            Map<String, Object> content = Map.of(
                    "flow", pkg.getFlow(),
                    "dependencies", pkg.getDependencies()
            );

            String json = objectMapper.writeValueAsString(content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                // Use String.format for consistent hex conversion (preserves leading zeros)
                hexString.append(String.format("%02x", b));
            }

            if (!hexString.toString().equals(pkg.getChecksum())) {
                throw new IllegalArgumentException("Export package checksum verification failed, file may have been modified");
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Checksum verification failed", e);
        }
    }

    /**
     * 分析元件狀態
     */
    private List<FlowImportPreviewResponse.ComponentStatus> analyzeComponents(
            List<ComponentDependency> components) {

        if (components == null) {
            return List.of();
        }

        return components.stream()
                .map(comp -> {
                    boolean installed = componentRepository.existsByNameAndIsDeletedFalse(comp.getName());
                    return FlowImportPreviewResponse.ComponentStatus.builder()
                            .name(comp.getName())
                            .version(comp.getVersion())
                            .image(comp.getImage())
                            .installed(installed)
                            .versionMatch(installed)
                            .canAutoInstall(comp.getImage() != null && !comp.getImage().isEmpty())
                            .build();
                })
                .toList();
    }

    /**
     * 分析憑證需求
     */
    private List<FlowImportPreviewResponse.CredentialRequirement> analyzeCredentials(
            List<CredentialPlaceholder> placeholders, UUID userId) {

        if (placeholders == null) {
            return List.of();
        }

        return placeholders.stream()
                .map(ph -> {
                    // 查找相容的憑證
                    List<Credential> compatibleCreds = credentialRepository
                            .findByOwnerIdAndType(userId, ph.getCredentialType());

                    return FlowImportPreviewResponse.CredentialRequirement.builder()
                            .nodeId(ph.getNodeId())
                            .nodeName(ph.getNodeName())
                            .credentialType(ph.getCredentialType())
                            .originalCredentialName(ph.getCredentialName())
                            .compatibleCredentials(compatibleCreds.stream()
                                    .map(c -> FlowImportPreviewResponse.CompatibleCredential.builder()
                                            .id(c.getId().toString())
                                            .name(c.getName())
                                            .type(c.getType())
                                            .build())
                                    .toList())
                            .build();
                })
                .toList();
    }

    /**
     * 重新映射憑證
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> remapCredentials(
            Map<String, Object> definition,
            Map<String, UUID> credentialMappings) {

        if (credentialMappings == null || credentialMappings.isEmpty()) {
            // 移除所有憑證引用
            return removeCredentialReferences(definition);
        }

        // 深拷貝
        Map<String, Object> newDefinition = new HashMap<>(definition);

        Object nodesObj = definition.get("nodes");
        if (nodesObj instanceof List) {
            List<Map<String, Object>> nodes = new ArrayList<>();

            for (Map<String, Object> node : (List<Map<String, Object>>) nodesObj) {
                Map<String, Object> newNode = new HashMap<>(node);
                Map<String, Object> data = new HashMap<>((Map<String, Object>) node.get("data"));

                String nodeId = (String) node.get("id");
                if (credentialMappings.containsKey(nodeId)) {
                    data.put("credentialId", credentialMappings.get(nodeId).toString());
                } else if (data.containsKey("credentialId")) {
                    data.remove("credentialId");
                }

                newNode.put("data", data);
                nodes.add(newNode);
            }

            newDefinition.put("nodes", nodes);
        }

        return newDefinition;
    }

    /**
     * 移除所有憑證引用
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> removeCredentialReferences(Map<String, Object> definition) {
        Map<String, Object> newDefinition = new HashMap<>(definition);

        Object nodesObj = definition.get("nodes");
        if (nodesObj instanceof List) {
            List<Map<String, Object>> nodes = new ArrayList<>();

            for (Map<String, Object> node : (List<Map<String, Object>>) nodesObj) {
                Map<String, Object> newNode = new HashMap<>(node);
                Map<String, Object> data = new HashMap<>((Map<String, Object>) node.get("data"));
                data.remove("credentialId");
                newNode.put("data", data);
                nodes.add(newNode);
            }

            newDefinition.put("nodes", nodes);
        }

        return newDefinition;
    }

    @SuppressWarnings("unchecked")
    private int getNodeCount(Map<String, Object> definition) {
        Object nodes = definition.get("nodes");
        return nodes instanceof List ? ((List<?>) nodes).size() : 0;
    }

    @SuppressWarnings("unchecked")
    private int getEdgeCount(Map<String, Object> definition) {
        Object edges = definition.get("edges");
        return edges instanceof List ? ((List<?>) edges).size() : 0;
    }
}
