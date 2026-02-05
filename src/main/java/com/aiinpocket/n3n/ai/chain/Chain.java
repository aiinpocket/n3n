package com.aiinpocket.n3n.ai.chain;

import java.util.Map;

/**
 * Chain 介面
 *
 * 定義可組合的處理單元，類似 LangChain 的 Chain。
 * Chain 可以串聯執行，形成複雜的處理流程。
 */
public interface Chain {

    /**
     * 執行 Chain
     *
     * @param inputs 輸入參數
     * @return 執行結果
     */
    ChainResult run(Map<String, Object> inputs);

    /**
     * 執行 Chain（單一輸入）
     *
     * @param input 輸入文字
     * @return 輸出文字
     */
    default String run(String input) {
        ChainResult result = run(Map.of("input", input));
        return result.getOutput();
    }

    /**
     * 執行 Chain 並返回上下文
     *
     * @param context 執行上下文
     * @return 更新後的上下文
     */
    ChainContext invoke(ChainContext context);

    /**
     * 取得 Chain 名稱
     */
    String getName();

    /**
     * 取得輸入變數名稱
     */
    String[] getInputKeys();

    /**
     * 取得輸出變數名稱
     */
    String[] getOutputKeys();
}
