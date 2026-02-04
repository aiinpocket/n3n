package com.aiinpocket.n3n.ai.agent;

import reactor.core.publisher.Flux;
import java.util.List;

/**
 * Agent 核心介面
 * 每個 Agent 負責特定領域的任務
 */
public interface Agent {

    /**
     * Agent 唯一識別碼
     */
    String getId();

    /**
     * Agent 顯示名稱
     */
    String getName();

    /**
     * Agent 描述（用於 Supervisor 決策）
     */
    String getDescription();

    /**
     * 此 Agent 可處理的意圖類型
     */
    List<String> getCapabilities();

    /**
     * 取得此 Agent 可用的工具
     */
    List<AgentTool> getTools();

    /**
     * 執行任務（非串流）
     */
    AgentResult execute(AgentContext context);

    /**
     * 執行任務（串流）
     */
    Flux<AgentStreamChunk> executeStream(AgentContext context);
}
