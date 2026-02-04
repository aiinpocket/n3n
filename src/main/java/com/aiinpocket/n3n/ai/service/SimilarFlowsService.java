package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.dto.SimilarFlow;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 類似流程服務
 * 根據用戶的描述推薦相似的現有流程
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimilarFlowsService {

    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;

    // 中文分詞相關的關鍵字模式
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "([a-zA-Z]+|[\\u4e00-\\u9fa5]{2,}|\\d+)",
        Pattern.UNICODE_CHARACTER_CLASS
    );

    // 常見的停用詞
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
        "一個", "上", "也", "很", "到", "說", "要", "去", "你", "會", "著",
        "沒有", "看", "好", "自己", "這", "the", "a", "an", "is", "are", "to",
        "and", "or", "for", "of", "in", "on", "with", "as", "at", "by"
    );

    /**
     * 搜尋類似流程
     *
     * @param userId 用戶 ID
     * @param query 查詢文字（自然語言描述）
     * @param limit 最大返回數量
     * @return 類似流程列表（按相似度排序）
     */
    public List<SimilarFlow> findSimilarFlows(UUID userId, String query, int limit) {
        // 提取查詢關鍵字
        Set<String> queryKeywords = extractKeywords(query);
        log.debug("Query keywords: {}", queryKeywords);

        if (queryKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        // 獲取用戶的所有流程（使用分頁，最多取100個）
        PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Flow> flowPage = flowRepository.findByCreatedByAndIsDeletedFalse(userId, pageRequest);
        List<Flow> userFlows = flowPage.getContent();

        if (userFlows.isEmpty()) {
            return Collections.emptyList();
        }

        // 批次獲取所有流程的最新版本
        List<UUID> flowIds = userFlows.stream().map(Flow::getId).toList();
        List<FlowVersion> versions = flowVersionRepository.findByFlowIdInOrderByFlowIdAscCreatedAtDesc(flowIds);

        // 建立 flowId -> 最新版本的映射（取每個 flowId 的第一個版本，因為是按 createdAt DESC 排序）
        Map<UUID, FlowVersion> latestVersionMap = new LinkedHashMap<>();
        for (FlowVersion version : versions) {
            latestVersionMap.putIfAbsent(version.getFlowId(), version);
        }

        // 計算每個流程的相似度並排序
        List<SimilarFlow> results = userFlows.stream()
            .map(flow -> {
                FlowVersion latestVersion = latestVersionMap.get(flow.getId());
                return calculateSimilarity(flow, latestVersion, queryKeywords);
            })
            .filter(sf -> sf.getSimilarity() > 0.1)  // 過濾相似度太低的
            .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
            .limit(limit)
            .collect(Collectors.toList());

        log.info("Found {} similar flows for query: {}", results.size(), truncate(query, 50));

        return results;
    }

    /**
     * 計算流程與查詢的相似度
     */
    @SuppressWarnings("unchecked")
    private SimilarFlow calculateSimilarity(Flow flow, FlowVersion version, Set<String> queryKeywords) {
        // 提取流程的關鍵字（名稱 + 描述 + 節點類型）
        Set<String> flowKeywords = new HashSet<>();
        flowKeywords.addAll(extractKeywords(flow.getName()));

        if (flow.getDescription() != null) {
            flowKeywords.addAll(extractKeywords(flow.getDescription()));
        }

        // 從流程定義中提取節點類型
        List<String> nodeTypes = new ArrayList<>();
        int nodeCount = 0;

        if (version != null && version.getDefinition() != null) {
            Map<String, Object> definition = version.getDefinition();
            Object nodesObj = definition.get("nodes");
            if (nodesObj instanceof List<?> nodes) {
                nodeCount = nodes.size();
                for (Object node : nodes) {
                    if (node instanceof Map<?, ?> nodeMap) {
                        Object type = nodeMap.get("type");
                        if (type != null) {
                            String typeStr = type.toString();
                            nodeTypes.add(typeStr);
                            flowKeywords.add(typeStr.toLowerCase());
                        }
                        // 也從節點標籤提取關鍵字
                        Object data = nodeMap.get("data");
                        if (data instanceof Map<?, ?> dataMap) {
                            Object label = dataMap.get("label");
                            if (label != null) {
                                flowKeywords.addAll(extractKeywords(label.toString()));
                            }
                        }
                    }
                }
            }
        }

        // 計算關鍵字重疊
        Set<String> intersection = new HashSet<>(queryKeywords);
        intersection.retainAll(flowKeywords);

        // 計算 Jaccard 相似度
        Set<String> union = new HashSet<>(queryKeywords);
        union.addAll(flowKeywords);

        double similarity = union.isEmpty() ? 0 : (double) intersection.size() / union.size();

        // 對匹配的關鍵字數量給予額外權重
        double matchBonus = Math.min(intersection.size() * 0.1, 0.3);
        similarity = Math.min(similarity + matchBonus, 1.0);

        return SimilarFlow.builder()
            .flowId(flow.getId())
            .name(flow.getName())
            .description(flow.getDescription())
            .similarity(Math.round(similarity * 100) / 100.0)  // 保留兩位小數
            .nodeCount(nodeCount)
            .nodeTypes(nodeTypes.stream().distinct().limit(5).toList())
            .createdAt(flow.getCreatedAt())
            .matchedKeywords(new ArrayList<>(intersection))
            .build();
    }

    /**
     * 從文字中提取關鍵字
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> keywords = new HashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(text.toLowerCase());

        while (matcher.find()) {
            String word = matcher.group(1);
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
