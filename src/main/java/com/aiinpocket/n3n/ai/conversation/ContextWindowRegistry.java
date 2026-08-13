package com.aiinpocket.n3n.ai.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 各模型的 context window 大小登錄表。
 * 以模型名稱前綴比對，查不到時回傳預設值。
 * 數值以官方公開規格為準（誠實數值，不灌水）。
 */
@Component
@Slf4j
public class ContextWindowRegistry {

    /** Claude 4 系列標準 window */
    private static final int CLAUDE_4_WINDOW = 200_000;
    /** Claude 4 系列 1M beta window（需 beta header）*/
    private static final int CLAUDE_4_1M_WINDOW = 1_000_000;
    /** Gemini 2.5 Pro / Flash */
    private static final int GEMINI_25_WINDOW = 1_048_576;
    /** GPT-4o */
    private static final int GPT_4O_WINDOW = 128_000;
    /** GPT-4.1 */
    private static final int GPT_41_WINDOW = 1_047_576;

    /** 是否啟用 Claude 1M context beta（需要 API beta header 才真的生效） */
    @Value("${n3n.ai.context.claude-1m:false}")
    private boolean claude1mEnabled;

    /** 未知模型的預設 window */
    @Value("${n3n.ai.context.default-window:200000}")
    private int defaultWindow;

    /**
     * 依模型名稱查詢 context window（token 數）。
     * 模型名稱為 null、空白或無法辨識時回傳預設值。
     */
    public int windowFor(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return defaultWindow;
        }

        String model = modelName.trim().toLowerCase();
        for (Map.Entry<String, Integer> entry : buildPatternTable().entrySet()) {
            if (model.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return defaultWindow;
    }

    /**
     * 前綴 → window 對照表（依序比對，較特定的前綴放前面）。
     */
    private Map<String, Integer> buildPatternTable() {
        int claudeWindow = claude1mEnabled ? CLAUDE_4_1M_WINDOW : CLAUDE_4_WINDOW;

        Map<String, Integer> table = new LinkedHashMap<>();
        table.put("claude-sonnet-4-5", claudeWindow);
        table.put("claude-opus-4", claudeWindow);
        table.put("claude-sonnet-4", claudeWindow);
        table.put("claude-haiku-4", CLAUDE_4_WINDOW);
        table.put("claude", CLAUDE_4_WINDOW);
        table.put("gemini-2.5-pro", GEMINI_25_WINDOW);
        table.put("gemini-2.5-flash", GEMINI_25_WINDOW);
        table.put("gpt-4o", GPT_4O_WINDOW);
        table.put("gpt-4.1", GPT_41_WINDOW);
        return table;
    }
}
