package com.aiinpocket.n3n.ai.chain;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chain 執行上下文
 *
 * 在 Chain 之間傳遞資料和狀態。
 */
@Data
@Builder
public class ChainContext {

    /**
     * 執行 ID
     */
    private String executionId;

    /**
     * 對話 ID（用於對話 Chain）
     */
    private String conversationId;

    /**
     * 輸入變數
     */
    @Builder.Default
    private Map<String, Object> inputs = new HashMap<>();

    /**
     * 中間結果
     */
    @Builder.Default
    private Map<String, Object> intermediates = new HashMap<>();

    /**
     * 最終輸出
     */
    @Builder.Default
    private Map<String, Object> outputs = new HashMap<>();

    /**
     * 執行歷史
     */
    @Builder.Default
    private List<ChainStep> steps = new ArrayList<>();

    /**
     * 元資料
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 開始時間
     */
    @Builder.Default
    private Instant startTime = Instant.now();

    /**
     * 是否有錯誤
     */
    private boolean hasError;

    /**
     * 錯誤訊息
     */
    private String errorMessage;

    /**
     * 從輸入建立上下文
     */
    public static ChainContext of(Map<String, Object> inputs) {
        return ChainContext.builder()
                .inputs(new HashMap<>(inputs))
                .build();
    }

    /**
     * 從單一輸入建立上下文
     */
    public static ChainContext of(String input) {
        return of(Map.of("input", input));
    }

    /**
     * 設定輸入變數
     */
    public ChainContext setInput(String key, Object value) {
        inputs.put(key, value);
        return this;
    }

    /**
     * 取得輸入變數
     */
    @SuppressWarnings("unchecked")
    public <T> T getInput(String key) {
        return (T) inputs.get(key);
    }

    /**
     * 設定中間結果
     */
    public ChainContext setIntermediate(String key, Object value) {
        intermediates.put(key, value);
        return this;
    }

    /**
     * 取得中間結果
     */
    @SuppressWarnings("unchecked")
    public <T> T getIntermediate(String key) {
        return (T) intermediates.get(key);
    }

    /**
     * 設定輸出
     */
    public ChainContext setOutput(String key, Object value) {
        outputs.put(key, value);
        return this;
    }

    /**
     * 取得輸出
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String key) {
        return (T) outputs.get(key);
    }

    /**
     * 取得主要輸出（output 鍵）
     */
    public String getOutputText() {
        Object output = outputs.get("output");
        return output != null ? output.toString() : null;
    }

    /**
     * 新增步驟記錄
     */
    public void addStep(String chainName, Map<String, Object> input, Map<String, Object> output) {
        steps.add(ChainStep.builder()
                .chainName(chainName)
                .input(input)
                .output(output)
                .timestamp(Instant.now())
                .build());
    }

    /**
     * 設定錯誤
     */
    public ChainContext setError(String message) {
        this.hasError = true;
        this.errorMessage = message;
        return this;
    }

    /**
     * 執行步驟記錄
     */
    @Data
    @Builder
    public static class ChainStep {
        private String chainName;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private Instant timestamp;
        private long durationMs;
    }
}
