package com.aiinpocket.n3n.execution.handler.handlers.action;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 記錄節點：把訊息與上游資料寫進執行紀錄，資料原樣往下傳。
 *
 * 給「健康檢查每天記錄一筆」「同步完成留個紀錄」這類流程用，
 * 也是官方範本大量使用的類型。不做任何轉換，永遠成功。
 */
@Component
@Slf4j
public class LoggerNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "logger";
    }

    @Override
    public String getDisplayName() {
        return "Logger";
    }

    @Override
    public String getDescription() {
        return "Write a message (and the incoming data) into the execution log, then pass the data through unchanged. "
                + "把訊息與資料記進執行紀錄，資料原樣往下傳。";
    }

    @Override
    public String getCategory() {
        return "Tools";
    }

    @Override
    public String getIcon() {
        return "file-text";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String message = getStringConfig(context, "message", "");
        String level = getStringConfig(context, "level", "info").toLowerCase();

        Map<String, Object> input = context.getInputData() != null ? context.getInputData() : Map.of();

        switch (level) {
            case "warn" -> log.warn("[flow-logger][{}] {}", context.getNodeId(), message);
            case "error" -> log.error("[flow-logger][{}] {}", context.getNodeId(), message);
            case "debug" -> log.debug("[flow-logger][{}] {}", context.getNodeId(), message);
            default -> log.info("[flow-logger][{}] {}", context.getNodeId(), message);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("message", message);
        output.put("level", level);
        output.put("loggedAt", Instant.now().toString());
        // 資料原樣傳遞，讓 logger 可以插在任何兩個節點之間而不改變流程行為
        output.putAll(input);
        return NodeExecutionResult.success(output);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("message", Map.of(
                "type", "string",
                "title", "Message",
                "description", "Text to record; supports {{expressions}}. 要記錄的訊息，支援 {{表達式}}"
        ));
        properties.put("level", Map.of(
                "type", "string",
                "title", "Level",
                "enum", List.of("info", "warn", "error", "debug"),
                "default", "info"
        ));
        return Map.of("type", "object", "properties", properties);
    }
}
