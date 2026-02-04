package com.aiinpocket.n3n.ai.agent.supervisor;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路由引擎
 * 決定任務應交給哪個子代理處理
 */
@Slf4j
@Component
public class RouterEngine {

    /**
     * 根據意圖路由到適當的代理
     */
    public String route(Intent intent, AgentContext context) {
        // 避免重複訪問同一代理太多次
        if (context.getVisitedAgents() != null &&
            context.getVisitedAgents().size() >= 3) {
            log.debug("Too many iterations, routing to responder");
            return "responder";
        }

        String targetAgent = switch (intent.getType()) {
            // Discovery 相關
            case SEARCH_NODE, GET_DOCUMENTATION, FIND_EXAMPLES, SEARCH_SKILL
                -> "discovery";

            // Builder 相關
            case CREATE_FLOW, ADD_NODE, REMOVE_NODE, CONNECT_NODES,
                 CONFIGURE_NODE, MODIFY_FLOW, OPTIMIZE_FLOW
                -> "builder";

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
     * 處理複合意圖的路由
     * 優先順序：Discovery -> Builder -> Responder
     */
    private String determineCompoundRoute(Intent intent, AgentContext context) {
        var visited = context.getVisitedAgents();

        // 如果還沒訪問過 Discovery，先去搜尋
        if (visited == null || !visited.contains("discovery")) {
            // 檢查是否有 Discovery 相關的子意圖
            if (intent.getSubIntents() != null) {
                boolean hasDiscoveryIntent = intent.getSubIntents().stream()
                    .anyMatch(Intent::isDiscoveryIntent);
                if (hasDiscoveryIntent) {
                    return "discovery";
                }
            }
        }

        // 如果還沒訪問過 Builder，去建構
        if (visited == null || !visited.contains("builder")) {
            if (intent.getSubIntents() != null) {
                boolean hasBuilderIntent = intent.getSubIntents().stream()
                    .anyMatch(Intent::isBuilderIntent);
                if (hasBuilderIntent) {
                    return "builder";
                }
            }
            // 如果有 Discovery 結果，可能需要 Builder
            if (context.getFromMemory("discoveryResults", Object.class) != null) {
                return "builder";
            }
        }

        // 最後交給 Responder 整理回應
        return "responder";
    }

    /**
     * 根據上下文決定是否需要繼續協作
     */
    public boolean shouldContinue(AgentResult result, AgentContext context) {
        if (!context.canContinue()) {
            return false;
        }

        if (result.isRequiresFollowUp()) {
            return true;
        }

        // 如果 Builder 建立了流程但還沒有 Responder 總結
        if (context.getFlowDraft() != null &&
            context.getFlowDraft().hasContent() &&
            (context.getVisitedAgents() == null ||
             !context.getVisitedAgents().contains("responder"))) {
            return true;
        }

        return false;
    }
}
