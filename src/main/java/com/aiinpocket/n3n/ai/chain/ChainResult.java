package com.aiinpocket.n3n.ai.chain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Chain 執行結果
 */
@Data
@Builder
public class ChainResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 主要輸出
     */
    private String output;

    /**
     * 所有輸出
     */
    private Map<String, Object> outputs;

    /**
     * 錯誤訊息
     */
    private String error;

    /**
     * 執行上下文
     */
    private ChainContext context;

    /**
     * 建立成功結果
     */
    public static ChainResult success(String output) {
        return ChainResult.builder()
                .success(true)
                .output(output)
                .outputs(Map.of("output", output))
                .build();
    }

    /**
     * 建立成功結果（帶多個輸出）
     */
    public static ChainResult success(Map<String, Object> outputs) {
        Object mainOutput = outputs.get("output");
        return ChainResult.builder()
                .success(true)
                .output(mainOutput != null ? mainOutput.toString() : null)
                .outputs(outputs)
                .build();
    }

    /**
     * 建立失敗結果
     */
    public static ChainResult failure(String error) {
        return ChainResult.builder()
                .success(false)
                .error(error)
                .build();
    }

    /**
     * 從上下文建立結果
     */
    public static ChainResult fromContext(ChainContext context) {
        if (context.isHasError()) {
            return ChainResult.builder()
                    .success(false)
                    .error(context.getErrorMessage())
                    .context(context)
                    .build();
        }

        return ChainResult.builder()
                .success(true)
                .output(context.getOutputText())
                .outputs(context.getOutputs())
                .context(context)
                .build();
    }
}
