package com.aiinpocket.n3n.ai.chain.impl;

import com.aiinpocket.n3n.ai.chain.Chain;
import com.aiinpocket.n3n.ai.chain.ChainContext;
import com.aiinpocket.n3n.ai.chain.ChainResult;
import com.aiinpocket.n3n.ai.memory.MemoryManager;
import com.aiinpocket.n3n.ai.memory.MemoryMessage;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 對話 Chain
 *
 * 整合 Memory 系統的對話 Chain，支援多輪對話上下文。
 * 類似 LangChain 的 ConversationChain。
 */
@Slf4j
public class ConversationChain implements Chain {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一個有幫助的 AI 助手。請用繁體中文回答使用者的問題。
            請根據對話歷史提供連貫且有幫助的回答。
            """;

    @Getter
    private final String name;
    private final AiService aiService;
    private final MemoryManager memoryManager;
    private final String systemPrompt;
    private final String model;

    @Builder
    public ConversationChain(String name, AiService aiService, MemoryManager memoryManager,
                              String systemPrompt, String model) {
        this.name = name != null ? name : "conversation_chain";
        this.aiService = aiService;
        this.memoryManager = memoryManager;
        this.systemPrompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.model = model;
    }

    @Override
    public ChainResult run(Map<String, Object> inputs) {
        ChainContext context = ChainContext.of(inputs);

        // 取得或建立對話 ID
        String conversationId = (String) inputs.get("conversation_id");
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }
        context.setConversationId(conversationId);

        invoke(context);
        return ChainResult.fromContext(context);
    }

    @Override
    public ChainContext invoke(ChainContext context) {
        try {
            String conversationId = context.getConversationId();
            if (conversationId == null) {
                conversationId = UUID.randomUUID().toString();
                context.setConversationId(conversationId);
            }

            String userInput = context.getInput("input");
            if (userInput == null || userInput.isBlank()) {
                context.setError("No input provided");
                return context;
            }

            // 1. 新增使用者訊息到記憶
            memoryManager.addUserMessage(conversationId, userInput);

            // 2. 取得對話上下文
            List<MemoryMessage> contextMessages = memoryManager.getContextMessages(conversationId, userInput);

            // 3. 建構完整 prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("System: ").append(systemPrompt).append("\n\n");

            // 新增對話歷史
            if (!contextMessages.isEmpty()) {
                promptBuilder.append("對話歷史：\n");
                promptBuilder.append(memoryManager.formatMessagesAsPrompt(contextMessages));
                promptBuilder.append("\n\n");
            }

            promptBuilder.append("User: ").append(userInput).append("\n\nAssistant:");

            String prompt = promptBuilder.toString();
            log.debug("ConversationChain {} prompt length: {} chars", name, prompt.length());

            // 4. 呼叫 LLM
            String response;
            if (model != null) {
                response = aiService.generateText(prompt, model);
            } else {
                response = aiService.generateText(prompt);
            }

            // 5. 新增 AI 回應到記憶
            memoryManager.addAssistantMessage(conversationId, response);

            // 6. 設定輸出
            context.setOutput("output", response);
            context.setOutput("conversation_id", conversationId);
            context.addStep(name, Map.of("input", userInput), Map.of("output", response));

            log.debug("ConversationChain {} completed for conversation {}", name, conversationId);
            return context;

        } catch (Exception e) {
            log.error("ConversationChain {} failed", name, e);
            context.setError("Conversation Chain failed: " + e.getMessage());
            return context;
        }
    }

    @Override
    public String[] getInputKeys() {
        return new String[]{"input", "conversation_id"};
    }

    @Override
    public String[] getOutputKeys() {
        return new String[]{"output", "conversation_id"};
    }

    /**
     * 清除對話記憶
     */
    public void clearMemory(String conversationId) {
        memoryManager.clearMemory(conversationId);
    }

    /**
     * 取得對話摘要
     */
    public String getSummary(String conversationId) {
        return memoryManager.getSummary(conversationId);
    }
}
