package com.aiinpocket.n3n.ai.chain.impl;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainContext;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Chain
 *
 * 基礎的 LLM 呼叫 Chain，使用提示詞模板生成回應。
 * 類似 LangChain 的 LLMChain。
 */
@Slf4j
public class LLMChain implements Chain {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    @Getter
    private final String name;
    private final AiService aiService;
    private final String promptTemplate;
    private final String model;
    private final String[] inputKeys;
    private final String outputKey;

    @Builder
    public LLMChain(String name, AiService aiService, String promptTemplate,
                    String model, String[] inputKeys, String outputKey) {
        this.name = name != null ? name : "llm_chain";
        this.aiService = aiService;
        this.promptTemplate = promptTemplate;
        this.model = model;
        this.inputKeys = inputKeys != null ? inputKeys : new String[]{"input"};
        this.outputKey = outputKey != null ? outputKey : "output";
    }

    @Override
    public ChainResult run(Map<String, Object> inputs) {
        ChainContext context = ChainContext.of(inputs);
        invoke(context);
        return ChainResult.fromContext(context);
    }

    @Override
    public ChainContext invoke(ChainContext context) {
        try {
            // 填充提示詞模板
            String prompt = formatPrompt(context.getInputs());
            log.debug("LLMChain {} prompt: {}", name, prompt);

            // 呼叫 LLM
            String response;
            if (model != null) {
                response = aiService.generateText(prompt, model);
            } else {
                response = aiService.generateText(prompt);
            }

            // 設定輸出
            context.setOutput(outputKey, response);
            context.addStep(name, context.getInputs(), Map.of(outputKey, response));

            log.debug("LLMChain {} completed", name);
            return context;

        } catch (Exception e) {
            log.error("LLMChain {} failed", name, e);
            context.setError("LLM Chain failed: " + e.getMessage());
            return context;
        }
    }

    @Override
    public String[] getInputKeys() {
        return inputKeys;
    }

    @Override
    public String[] getOutputKeys() {
        return new String[]{outputKey};
    }

    /**
     * 格式化提示詞
     */
    private String formatPrompt(Map<String, Object> inputs) {
        if (promptTemplate == null) {
            Object input = inputs.get("input");
            return input != null ? input.toString() : "";
        }

        String result = promptTemplate;
        Matcher matcher = VARIABLE_PATTERN.matcher(promptTemplate);

        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = inputs.get(varName);
            if (value != null) {
                result = result.replace("{" + varName + "}", value.toString());
            }
        }

        return result;
    }

    /**
     * 建立簡單的 LLM Chain
     */
    public static LLMChain simple(AiService aiService, String promptTemplate) {
        return LLMChain.builder()
                .aiService(aiService)
                .promptTemplate(promptTemplate)
                .build();
    }
}
