package com.aiinpocket.n3n.ai.rag.chain;

import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.retriever.Retriever;
import com.aiinpocket.n3n.ai.service.AiService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 問答 Chain
 *
 * 結合檢索和生成，實現基於知識庫的問答。
 * 類似 LangChain 的 RetrievalQA。
 */
@Slf4j
public class RetrievalQAChain {

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            基於以下上下文資訊回答問題。如果上下文中沒有相關資訊，請誠實說明你不知道。

            上下文：
            %s

            問題：%s

            請用繁體中文回答：
            """;

    private final Retriever retriever;
    private final AiService aiService;
    private final String promptTemplate;
    private final int retrieveK;
    private final boolean returnSourceDocuments;

    @Builder
    public RetrievalQAChain(Retriever retriever, AiService aiService,
                            String promptTemplate, int retrieveK,
                            boolean returnSourceDocuments) {
        this.retriever = retriever;
        this.aiService = aiService;
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.retrieveK = retrieveK > 0 ? retrieveK : 4;
        this.returnSourceDocuments = returnSourceDocuments;
    }

    /**
     * 執行 RAG 問答
     *
     * @param question 問題
     * @return 答案
     */
    public String run(String question) {
        return query(question).getAnswer();
    }

    /**
     * 執行 RAG 問答（帶完整結果）
     *
     * @param question 問題
     * @return 問答結果
     */
    public QAResult query(String question) {
        log.info("Processing RAG query: {}", question);

        // 1. 檢索相關文檔
        List<Document> relevantDocs = retriever.getRelevantDocuments(question, retrieveK);
        log.debug("Retrieved {} relevant documents", relevantDocs.size());

        if (relevantDocs.isEmpty()) {
            return QAResult.builder()
                    .question(question)
                    .answer("抱歉，我在知識庫中找不到相關資訊來回答這個問題。")
                    .sourceDocuments(List.of())
                    .build();
        }

        // 2. 組合上下文
        String context = relevantDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 建構 prompt
        String prompt = String.format(promptTemplate, context, question);

        // 4. 呼叫 LLM 生成答案
        String answer = aiService.generateText(prompt);
        log.debug("Generated answer: {} chars", answer.length());

        return QAResult.builder()
                .question(question)
                .answer(answer)
                .sourceDocuments(returnSourceDocuments ? relevantDocs : List.of())
                .build();
    }

    /**
     * 問答結果
     */
    @Data
    @Builder
    public static class QAResult {
        private String question;
        private String answer;
        private List<Document> sourceDocuments;

        /**
         * 取得來源摘要
         */
        public List<String> getSourceSummaries() {
            return sourceDocuments.stream()
                    .map(doc -> {
                        String source = doc.getSource();
                        String preview = doc.getContent();
                        if (preview != null && preview.length() > 100) {
                            preview = preview.substring(0, 100) + "...";
                        }
                        return source != null ? source + ": " + preview : preview;
                    })
                    .toList();
        }
    }

    /**
     * 建立簡單的 RAG Chain
     */
    public static RetrievalQAChain simple(Retriever retriever, AiService aiService) {
        return RetrievalQAChain.builder()
                .retriever(retriever)
                .aiService(aiService)
                .returnSourceDocuments(true)
                .build();
    }
}
