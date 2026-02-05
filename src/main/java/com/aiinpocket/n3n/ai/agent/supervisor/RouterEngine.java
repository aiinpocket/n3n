package com.aiinpocket.n3n.ai.agent.supervisor;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 路由引擎
 * 決定任務應交給哪個子代理處理
 * 支援多種協作模式和智能路由
 */
@Slf4j
@Component
public class RouterEngine {

    /**
     * 預定義的協作鏈
     * 定義常見工作流程的代理執行順序
     */
    private static final Map<String, List<String>> COLLABORATION_CHAINS = Map.of(
        // 建立流程：搜尋 -> 建構 -> 優化 -> 回應
        "create_flow", List.of("discovery", "builder", "optimizer", "responder"),
        // 優化流程：優化 -> 回應
        "optimize_flow", List.of("optimizer", "responder"),
        // 修改流程：建構 -> 優化 -> 回應
        "modify_flow", List.of("builder", "optimizer", "responder"),
        // 搜尋節點：搜尋 -> 回應
        "search_node", List.of("discovery", "responder"),
        // 複合任務：搜尋 -> 建構 -> 回應
        "compound", List.of("discovery", "builder", "responder")
    );

    /**
     * 根據意圖路由到適當的代理
     */
    public String route(Intent intent, AgentContext context) {
        // 檢查迭代限制
        if (context.isNearIterationLimit()) {
            log.debug("Near iteration limit, routing to responder for completion");
            return "responder";
        }

        // 避免重複訪問同一代理太多次
        if (context.getVisitedCount() >= 4) {
            log.debug("Too many agent visits, routing to responder");
            return "responder";
        }

        String targetAgent = switch (intent.getType()) {
            // Discovery 相關
            case SEARCH_NODE, GET_DOCUMENTATION, FIND_EXAMPLES, SEARCH_SKILL
                -> "discovery";

            // Builder 相關
            case CREATE_FLOW, ADD_NODE, REMOVE_NODE, CONNECT_NODES,
                 CONFIGURE_NODE, MODIFY_FLOW
                -> "builder";

            // Optimizer 相關（新增）
            case OPTIMIZE_FLOW -> routeOptimization(context);

            // Responder 相關
            case EXPLAIN, CLARIFY, CONFIRM, CHITCHAT
                -> "responder";

            // 複合意圖 - 依照訪問歷史決定
            case COMPOUND -> determineCompoundRoute(intent, context);

            // 未知意圖
            case UNKNOWN -> "responder";
        };

        log.debug("Routing intent {} to agent: {}", intent.getType(), targetAgent);
        return targetAgent;
    }

    /**
     * 路由優化請求
     * 如果流程剛建立，先交給 optimizer，否則交給 builder
     */
    private String routeOptimization(AgentContext context) {
        // 如果已經訪問過 optimizer，交給 responder 總結
        if (context.hasVisited("optimizer")) {
            return "responder";
        }

        // 如果有流程草稿，交給 optimizer
        if (context.getFlowDraft() != null && context.getFlowDraft().hasContent()) {
            return "optimizer";
        }

        // 否則先建構
        return "builder";
    }

    /**
     * 處理複合意圖的路由
     * 使用協作鏈決定下一個代理
     */
    private String determineCompoundRoute(Intent intent, AgentContext context) {
        // 取得適用的協作鏈
        List<String> chain = getApplicableChain(intent, context);

        // 找到鏈中下一個未訪問的代理
        for (String agent : chain) {
            if (!context.hasVisited(agent)) {
                // 特殊檢查：optimizer 需要有流程才能執行
                if ("optimizer".equals(agent)) {
                    if (context.getFlowDraft() == null || !context.getFlowDraft().hasContent()) {
                        continue; // 跳過 optimizer
                    }
                }
                return agent;
            }
        }

        // 如果鏈中所有代理都訪問過，交給 responder
        return "responder";
    }

    /**
     * 根據意圖取得適用的協作鏈
     */
    private List<String> getApplicableChain(Intent intent, AgentContext context) {
        if (intent.getSubIntents() != null && !intent.getSubIntents().isEmpty()) {
            // 分析子意圖來決定協作鏈
            boolean hasDiscovery = intent.getSubIntents().stream().anyMatch(Intent::isDiscoveryIntent);
            boolean hasBuilder = intent.getSubIntents().stream().anyMatch(Intent::isBuilderIntent);

            if (hasDiscovery && hasBuilder) {
                return COLLABORATION_CHAINS.get("create_flow");
            } else if (hasBuilder) {
                return COLLABORATION_CHAINS.get("modify_flow");
            } else if (hasDiscovery) {
                return COLLABORATION_CHAINS.get("search_node");
            }
        }

        // 預設使用 compound 鏈
        return COLLABORATION_CHAINS.getOrDefault("compound",
            List.of("discovery", "builder", "responder"));
    }

    /**
     * 根據上下文決定是否需要繼續協作
     */
    public boolean shouldContinue(AgentResult result, AgentContext context) {
        // 檢查基本條件
        if (!context.canContinue()) {
            log.debug("Cannot continue: iteration limit reached");
            return false;
        }

        // 如果結果要求後續處理
        if (result.isRequiresFollowUp()) {
            log.debug("Continue: result requires follow-up");
            return true;
        }

        // 如果有錯誤，停止協作
        if (!result.isSuccess() && result.getError() != null) {
            log.debug("Stop: result has error");
            return false;
        }

        // 如果 Builder 建立了流程但還沒有 Responder 總結
        if (context.getFlowDraft() != null &&
            context.getFlowDraft().hasContent() &&
            !context.hasVisited("responder")) {

            // 如果還沒優化，先優化
            if (!context.hasVisited("optimizer") && context.getFlowDraft().getNodeCount() > 2) {
                log.debug("Continue: flow needs optimization");
                return true;
            }

            log.debug("Continue: flow needs responder summary");
            return true;
        }

        // 如果有 Discovery 結果但還沒建構
        if (context.getFromMemory("discoveryResults", Object.class) != null &&
            !context.hasVisited("builder")) {
            log.debug("Continue: discovery results need builder");
            return true;
        }

        return false;
    }

    /**
     * 取得下一個建議的代理（用於協作鏈）
     */
    public String getNextAgent(AgentContext context) {
        WorkingFlowDraft draft = context.getFlowDraft();

        // 如果有流程且未優化
        if (draft != null && draft.hasContent() && !context.hasVisited("optimizer")) {
            return "optimizer";
        }

        // 如果有流程且未總結
        if (draft != null && draft.hasContent() && !context.hasVisited("responder")) {
            return "responder";
        }

        // 如果有 Discovery 結果且未建構
        if (context.getFromMemory("discoveryResults", Object.class) != null &&
            !context.hasVisited("builder")) {
            return "builder";
        }

        return "responder";
    }

    /**
     * 檢查是否應該提前終止協作
     * 用於檢測可能的無限循環
     */
    public boolean shouldTerminateEarly(AgentContext context) {
        // 如果同一個代理被訪問超過 2 次，可能有問題
        // 這裡使用 visitedCount 來大致判斷
        if (context.getIterationCount() > 5 && context.getVisitedCount() < 3) {
            log.warn("Possible loop detected: {} iterations but only {} unique agents",
                context.getIterationCount(), context.getVisitedCount());
            return true;
        }

        return false;
    }
}
