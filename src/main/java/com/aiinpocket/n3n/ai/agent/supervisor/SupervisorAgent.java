package com.aiinpocket.n3n.ai.agent.supervisor;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Supervisor Agent - 多代理協調器
 *
 * 職責：
 * 1. 分析使用者意圖
 * 2. 路由到適當的子代理
 * 3. 協調多代理協作
 * 4. 管理對話狀態
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final IntentAnalyzer intentAnalyzer;
    private final RouterEngine routerEngine;

    @PostConstruct
    public void init() {
        agentRegistry.register(this);
    }

    @Override
    public String getId() {
        return "supervisor";
    }

    @Override
    public String getName() {
        return "Supervisor Agent";
    }

    @Override
    public String getDescription() {
        return "協調多個子代理，分析意圖並路由任務";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("intent_analysis", "routing", "orchestration");
    }

    @Override
    public List<AgentTool> getTools() {
        return List.of(); // Supervisor 不直接使用工具
    }

    /**
     * 主要入口點 - 處理使用者訊息
     */
    @Override
    public AgentResult execute(AgentContext context) {
        log.info("Supervisor processing: {}",
            truncate(context.getUserInput(), 50));

        try {
            // 1. 分析意圖
            Intent intent = intentAnalyzer.analyze(context);
            context.setIntent(intent);
            log.debug("Analyzed intent: {} (confidence: {})",
                intent.getType(), intent.getConfidence());

            // 2. 初始化流程草稿（如果需要）
            if (context.getFlowDraft() == null && intent.isBuilderIntent()) {
                context.setFlowDraft(new WorkingFlowDraft());
                // 如果有現有流程，初始化草稿
                if (context.getCurrentNodes() != null && !context.getCurrentNodes().isEmpty()) {
                    Map<String, Object> existing = Map.of(
                        "nodes", context.getCurrentNodes(),
                        "edges", context.getCurrentEdges() != null ?
                            context.getCurrentEdges() : List.of()
                    );
                    context.getFlowDraft().initializeFromDefinition(existing);
                }
            }

            // 3. 路由到子代理
            String targetAgentId = routerEngine.route(intent, context);
            log.debug("Routing to agent: {}", targetAgentId);

            // 4. 執行子代理
            Agent targetAgent = agentRegistry.getAgent(targetAgentId);
            AgentResult subResult = targetAgent.execute(context);

            // 5. 標記已訪問
            context.markVisited(targetAgentId);
            context.incrementIteration();

            // 6. 檢查是否需要繼續協作
            if (routerEngine.shouldContinue(subResult, context)) {
                // 遞迴處理
                return execute(context);
            }

            // 7. 最終回應
            return finalize(subResult, context);

        } catch (Exception e) {
            log.error("Supervisor execution failed", e);
            return AgentResult.error("Processing failed: " + e.getMessage());
        }
    }

    /**
     * 串流執行
     */
    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                // 分析意圖
                sink.next(AgentStreamChunk.thinking("分析您的需求..."));
                Intent intent = intentAnalyzer.analyze(context);
                context.setIntent(intent);

                // 初始化流程草稿
                if (context.getFlowDraft() == null && intent.isBuilderIntent()) {
                    context.setFlowDraft(new WorkingFlowDraft());
                    if (context.getCurrentNodes() != null && !context.getCurrentNodes().isEmpty()) {
                        Map<String, Object> existing = Map.of(
                            "nodes", context.getCurrentNodes(),
                            "edges", context.getCurrentEdges() != null ?
                                context.getCurrentEdges() : List.of()
                        );
                        context.getFlowDraft().initializeFromDefinition(existing);
                    }
                }

                // 路由
                String targetAgentId = routerEngine.route(intent, context);
                Agent targetAgent = agentRegistry.getAgent(targetAgentId);

                sink.next(AgentStreamChunk.thinking(
                    "交給 " + targetAgent.getName() + " 處理..."));

                // 執行子代理串流
                targetAgent.executeStream(context)
                    .doOnNext(chunk -> {
                        sink.next(chunk);
                    })
                    .doOnComplete(() -> {
                        context.markVisited(targetAgentId);
                        context.incrementIteration();

                        // 如果有流程定義，發送結構化資料
                        if (context.getFlowDraft() != null &&
                            context.getFlowDraft().hasContent()) {
                            sink.next(AgentStreamChunk.structured(Map.of(
                                "action", "update_flow",
                                "flowDefinition", context.getFlowDraft().toDefinition()
                            )));
                        }

                        sink.next(AgentStreamChunk.done());
                        sink.complete();
                    })
                    .doOnError(sink::error)
                    .subscribe();

            } catch (Exception e) {
                log.error("Supervisor stream execution failed", e);
                sink.next(AgentStreamChunk.error(e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 最終化結果
     */
    private AgentResult finalize(AgentResult subResult, AgentContext context) {
        // 如果有流程草稿，整理最終輸出
        if (context.getFlowDraft() != null && context.getFlowDraft().hasContent()) {
            return AgentResult.builder()
                .success(true)
                .content(subResult.getContent())
                .flowDefinition(context.getFlowDraft().toDefinition())
                .recommendations(subResult.getRecommendations())
                .pendingChanges(subResult.getPendingChanges())
                .build();
        }
        return subResult;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
