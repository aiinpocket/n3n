package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.dto.NodeExecutionResponse;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 執行分析上下文組裝器：
 * 把一次執行（測試草稿或正式/批次執行）的結果整理成 AI 可讀的文字區塊，
 * 供 AI 助手分析失敗原因並以口語化方式回報使用者。
 * 所有查詢都經過 ExecutionService 的使用者存取檢查。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionAnalysisContextBuilder {

    private static final int MAX_OUTPUT_CHARS = 600;
    // 流程定義要完整給模型，它才能輸出可套用的 flow-fix（截斷會讓修正殘缺）
    private static final int MAX_DEFINITION_CHARS = 20000;

    private final ExecutionService executionService;
    private final FlowVersionRepository flowVersionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 組出執行上下文區塊；查無資料或無權限時擲出例外由呼叫端處理。
     */
    public String build(UUID executionId, UUID userId) {
        ExecutionResponse execution = executionService.getExecution(executionId, userId);
        List<NodeExecutionResponse> nodes = executionService.getNodeExecutions(executionId, userId);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 本次執行紀錄（供你分析，使用者看不到這個區塊）===\n");
        sb.append("流程：").append(execution.getFlowName())
          .append("（版本 ").append(execution.getFlowVersion()).append("）\n");
        sb.append("整體狀態：").append(execution.getStatus());
        if (execution.getDurationMs() != null) {
            sb.append("，耗時 ").append(execution.getDurationMs()).append(" ms");
        }
        sb.append("\n觸發方式：").append(execution.getTriggerType()).append("\n");

        appendTriggerInput(sb, execution);
        appendNodeResults(sb, executionId, userId, nodes);
        appendFlowDefinition(sb, execution.getFlowVersionId());

        sb.append("=== 執行紀錄結束 ===\n");
        return sb.toString();
    }

    private void appendTriggerInput(StringBuilder sb, ExecutionResponse execution) {
        if (execution.getTriggerInput() != null && !execution.getTriggerInput().isEmpty()) {
            sb.append("觸發輸入：").append(toJson(execution.getTriggerInput(), MAX_OUTPUT_CHARS)).append("\n");
        }
    }

    private void appendNodeResults(StringBuilder sb, UUID executionId, UUID userId,
                                   List<NodeExecutionResponse> nodes) {
        sb.append("\n各節點結果：\n");
        for (NodeExecutionResponse node : nodes) {
            sb.append("- 節點 ").append(node.getNodeId())
              .append("（類型 ").append(node.getComponentName()).append("）：")
              .append(node.getStatus());
            if (node.getDurationMs() != null) {
                sb.append("，").append(node.getDurationMs()).append(" ms");
            }
            if (node.getErrorMessage() != null && !node.getErrorMessage().isBlank()) {
                sb.append("\n  錯誤訊息：").append(node.getErrorMessage());
                if (node.getErrorStack() != null && !node.getErrorStack().isBlank()) {
                    sb.append("（例外類別：").append(node.getErrorStack()).append("）");
                }
            }
            appendNodeOutput(sb, executionId, userId, node);
            sb.append("\n");
        }
    }

    /** 節點輸出存在 Redis（保留 24 小時），逾期或查不到時安靜略過。 */
    private void appendNodeOutput(StringBuilder sb, UUID executionId, UUID userId,
                                  NodeExecutionResponse node) {
        try {
            Map<String, Object> data = executionService.getNodeData(executionId, node.getNodeId(), userId);
            Object output = data.get("output");
            if (output instanceof Map<?, ?> map && !map.isEmpty()) {
                sb.append("\n  輸出（截斷）：").append(toJson(output, MAX_OUTPUT_CHARS));
            }
        } catch (Exception e) {
            log.debug("No node output available for {}/{}", executionId, node.getNodeId());
        }
    }

    /** 附上流程定義（含各節點 config），AI 才能對照設定找出問題並提出修改。 */
    private void appendFlowDefinition(StringBuilder sb, UUID flowVersionId) {
        flowVersionRepository.findById(flowVersionId).ifPresent(version ->
            sb.append("\n流程定義（截斷）：")
              .append(toJson(definitionOf(version), MAX_DEFINITION_CHARS))
              .append("\n"));
    }

    private Object definitionOf(FlowVersion version) {
        return version.getDefinition();
    }

    private String toJson(Object value, int maxChars) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > maxChars ? json.substring(0, maxChars) + "...(截斷)" : json;
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
