package com.aiinpocket.n3n.ai.chain.impl;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainContext;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 順序執行 Chain
 *
 * 依序執行多個 Chain，前一個 Chain 的輸出作為下一個的輸入。
 * 類似 LangChain 的 SequentialChain。
 */
@Slf4j
public class SequentialChain implements Chain {

    @Getter
    private final String name;
    private final List<Chain> chains;
    private final String[] inputKeys;
    private final String[] outputKeys;
    private final boolean returnIntermediates;

    @Builder
    public SequentialChain(String name, List<Chain> chains, String[] inputKeys,
                           String[] outputKeys, boolean returnIntermediates) {
        this.name = name != null ? name : "sequential_chain";
        this.chains = chains != null ? new ArrayList<>(chains) : new ArrayList<>();
        this.inputKeys = inputKeys;
        this.outputKeys = outputKeys;
        this.returnIntermediates = returnIntermediates;
    }

    @Override
    public ChainResult run(Map<String, Object> inputs) {
        ChainContext context = ChainContext.of(inputs);
        invoke(context);
        return ChainResult.fromContext(context);
    }

    @Override
    public ChainContext invoke(ChainContext context) {
        if (chains.isEmpty()) {
            context.setError("No chains to execute");
            return context;
        }

        log.debug("SequentialChain {} starting with {} chains", name, chains.size());

        // 合併輸入到中間結果
        context.getIntermediates().putAll(context.getInputs());

        for (int i = 0; i < chains.size(); i++) {
            Chain chain = chains.get(i);
            log.debug("Executing chain {}/{}: {}", i + 1, chains.size(), chain.getName());

            // 建立該 Chain 的輸入
            Map<String, Object> chainInputs = new HashMap<>(context.getIntermediates());

            // 執行 Chain
            ChainContext chainContext = ChainContext.of(chainInputs);
            chainContext.setConversationId(context.getConversationId());
            chainContext.setExecutionId(context.getExecutionId());

            chain.invoke(chainContext);

            if (chainContext.isHasError()) {
                context.setError("Chain " + chain.getName() + " failed: " + chainContext.getErrorMessage());
                return context;
            }

            // 合併輸出到中間結果
            context.getIntermediates().putAll(chainContext.getOutputs());

            // 記錄步驟
            context.addStep(chain.getName(), chainInputs, chainContext.getOutputs());
        }

        // 設定最終輸出
        if (outputKeys != null && outputKeys.length > 0) {
            for (String key : outputKeys) {
                Object value = context.getIntermediates().get(key);
                if (value != null) {
                    context.setOutput(key, value);
                }
            }
        } else {
            // 使用最後一個 Chain 的輸出
            Chain lastChain = chains.get(chains.size() - 1);
            for (String key : lastChain.getOutputKeys()) {
                Object value = context.getIntermediates().get(key);
                if (value != null) {
                    context.setOutput(key, value);
                }
            }
        }

        // 如果需要返回中間結果
        if (returnIntermediates) {
            context.setOutput("intermediates", context.getIntermediates());
        }

        log.debug("SequentialChain {} completed", name);
        return context;
    }

    @Override
    public String[] getInputKeys() {
        if (inputKeys != null) {
            return inputKeys;
        }
        // 使用第一個 Chain 的輸入
        if (!chains.isEmpty()) {
            return chains.get(0).getInputKeys();
        }
        return new String[]{"input"};
    }

    @Override
    public String[] getOutputKeys() {
        if (outputKeys != null) {
            return outputKeys;
        }
        // 使用最後一個 Chain 的輸出
        if (!chains.isEmpty()) {
            return chains.get(chains.size() - 1).getOutputKeys();
        }
        return new String[]{"output"};
    }

    /**
     * 新增 Chain
     */
    public SequentialChain addChain(Chain chain) {
        chains.add(chain);
        return this;
    }

    /**
     * 取得 Chain 列表
     */
    public List<Chain> getChains() {
        return Collections.unmodifiableList(chains);
    }

    /**
     * 建立簡單的順序 Chain
     */
    public static SequentialChain of(Chain... chains) {
        return SequentialChain.builder()
                .chains(Arrays.asList(chains))
                .build();
    }
}
