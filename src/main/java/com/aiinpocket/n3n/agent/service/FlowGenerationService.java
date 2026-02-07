package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.context.ComponentContextBuilder;
import com.aiinpocket.n3n.agent.layout.FlowLayoutEngine;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程生成服務
 *
 * 根據 AI 推薦的元件組合生成完整的流程定義
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowGenerationService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final ComponentContextBuilder contextBuilder;
    private final FlowLayoutEngine layoutEngine;
    private final ObjectMapper objectMapper;

    /**
     * 從 AI 結構化輸出生成流程
     *
     * @param userId 使用者 ID
     * @param aiOutput AI 生成的結構化資料
     * @param flowName 流程名稱
     * @return 生成的流程
     */
    @Transactional
    public GeneratedFlow generateFlow(UUID userId, Map<String, Object> aiOutput, String flowName) {
        // 解析 AI 輸出
        @SuppressWarnings("unchecked")
        Map<String, Object> flowDefinition =
            (Map<String, Object>) aiOutput.get("flowDefinition");

        if (flowDefinition == null) {
            // 如果沒有完整的 flowDefinition，嘗試從元件推薦建構
            flowDefinition = buildFlowFromRecommendations(aiOutput);
        }

        // 確保有節點和邊
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes =
            (List<Map<String, Object>>) flowDefinition.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges =
            (List<Map<String, Object>>) flowDefinition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Flow definition must contain at least one node");
        }

        // 自動排版
        FlowLayoutEngine.LayoutResult layout = layoutEngine.layout(nodes, edges);

        // 建立流程
        Flow flow = Flow.builder()
            .name(flowName != null ? flowName : "AI 生成的流程")
            .description(extractDescription(aiOutput))
            .createdBy(userId)
            .build();

        flow = flowRepository.save(flow);

        // 建立版本
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("nodes", layout.nodes());
        definition.put("edges", layout.edges());
        definition.put("viewport", Map.of("x", 0, "y", 0, "zoom", 1));

        FlowVersion version = FlowVersion.builder()
            .flowId(flow.getId())
            .version("1.0.0")
            .definition(definition)
            .status("draft")
            .createdBy(userId)
            .build();

        version = flowVersionRepository.save(version);

        log.info("Generated flow {} with {} nodes", flow.getId(), nodes.size());

        return new GeneratedFlow(
            flow.getId(),
            flow.getName(),
            version.getId(),
            version.getVersion(),
            definition,
            extractUnderstanding(aiOutput)
        );
    }

    /**
     * 從元件推薦建構基本流程定義
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFlowFromRecommendations(Map<String, Object> aiOutput) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 取得推薦的現有元件
        List<Map<String, Object>> existingComponents =
            (List<Map<String, Object>>) aiOutput.get("existingComponents");

        if (existingComponents != null) {
            for (int i = 0; i < existingComponents.size(); i++) {
                Map<String, Object> comp = existingComponents.get(i);
                String nodeId = "node_" + (i + 1);

                // 查找元件詳細資訊
                String componentName = (String) comp.get("name");
                Map<String, Object> componentInfo = findComponentByName(componentName);

                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", nodeId);
                node.put("type", "component");
                node.put("data", Map.of(
                    "componentName", componentName,
                    "componentId", componentInfo != null ? componentInfo.get("id") : null,
                    "label", comp.getOrDefault("purpose", componentName),
                    "config", Map.of()
                ));
                node.put("position", Map.of("x", 0, "y", 0)); // 會被 layout 覆蓋

                nodes.add(node);

                // 建立連接（線性連接）
                if (i > 0) {
                    String prevNodeId = "node_" + i;
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("id", "edge_" + i);
                    edge.put("source", prevNodeId);
                    edge.put("target", nodeId);
                    edges.add(edge);
                }
            }
        }

        Map<String, Object> flowDef = new LinkedHashMap<>();
        flowDef.put("nodes", nodes);
        flowDef.put("edges", edges);
        return flowDef;
    }

    /**
     * 根據名稱查找元件
     */
    private Map<String, Object> findComponentByName(String name) {
        if (name == null) return null;

        List<Map<String, Object>> results = contextBuilder.searchComponents(name);
        return results.stream()
            .filter(c -> name.equalsIgnoreCase((String) c.get("name")))
            .findFirst()
            .orElse(null);
    }

    /**
     * 從 AI 輸出中提取描述
     */
    private String extractDescription(Map<String, Object> aiOutput) {
        String understanding = (String) aiOutput.get("understanding");
        if (understanding != null) {
            return understanding;
        }
        return "由 AI 助手生成的流程";
    }

    /**
     * 從 AI 輸出中提取需求理解
     */
    private String extractUnderstanding(Map<String, Object> aiOutput) {
        return (String) aiOutput.get("understanding");
    }

    /**
     * 驗證流程定義的結構
     *
     * @param definition 流程定義
     * @return 驗證結果
     */
    public ValidationResult validateFlowDefinition(Map<String, Object> definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes =
            (List<Map<String, Object>>) definition.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges =
            (List<Map<String, Object>>) definition.get("edges");

        // 檢查節點
        if (nodes == null || nodes.isEmpty()) {
            errors.add("流程必須包含至少一個節點");
        } else {
            Set<String> nodeIds = new HashSet<>();
            for (Map<String, Object> node : nodes) {
                String id = (String) node.get("id");
                if (id == null || id.isEmpty()) {
                    errors.add("節點缺少 ID");
                } else if (nodeIds.contains(id)) {
                    errors.add("節點 ID 重複: " + id);
                } else {
                    nodeIds.add(id);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) node.get("data");
                if (data == null) {
                    warnings.add("節點 " + id + " 缺少 data 屬性");
                }
            }

            // 檢查邊
            if (edges != null) {
                for (Map<String, Object> edge : edges) {
                    String source = (String) edge.get("source");
                    String target = (String) edge.get("target");

                    if (source == null || target == null) {
                        errors.add("邊缺少 source 或 target");
                    } else {
                        if (!nodeIds.contains(source)) {
                            errors.add("邊的 source 節點不存在: " + source);
                        }
                        if (!nodeIds.contains(target)) {
                            errors.add("邊的 target 節點不存在: " + target);
                        }
                    }
                }
            }

            // 檢查孤立節點
            if (edges != null && !edges.isEmpty()) {
                Set<String> connectedNodes = new HashSet<>();
                for (Map<String, Object> edge : edges) {
                    connectedNodes.add((String) edge.get("source"));
                    connectedNodes.add((String) edge.get("target"));
                }
                for (String nodeId : nodeIds) {
                    if (!connectedNodes.contains(nodeId) && nodeIds.size() > 1) {
                        warnings.add("節點 " + nodeId + " 未與其他節點連接");
                    }
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * 生成的流程
     */
    public record GeneratedFlow(
        UUID flowId,
        String flowName,
        UUID versionId,
        String version,
        Map<String, Object> definition,
        String understanding
    ) {}

    /**
     * 驗證結果
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}
}
