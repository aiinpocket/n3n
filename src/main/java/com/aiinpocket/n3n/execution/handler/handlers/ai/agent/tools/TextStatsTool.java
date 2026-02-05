package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 文字統計工具
 * 分析文字的各種統計資訊
 */
@Component
@Slf4j
public class TextStatsTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "textStats";
    }

    @Override
    public String getName() {
        return "Text Statistics";
    }

    @Override
    public String getDescription() {
        return """
                文字統計工具，分析文字的各種統計資訊。

                統計項目：
                - 字元數（含/不含空格）
                - 字數
                - 句子數
                - 段落數
                - 行數
                - 最常見的詞彙
                - 平均句子長度
                - 閱讀時間估計

                參數：
                - text: 要分析的文字
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "要分析的文字"
                        )
                ),
                "required", List.of("text")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");

                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("文字不能為空");
                }

                // Security: limit input size
                if (text.length() > 1_000_000) {
                    return ToolResult.failure("文字過長，最大限制 1MB");
                }

                // Calculate statistics
                int charCount = text.length();
                int charCountNoSpaces = text.replaceAll("\\s", "").length();
                int wordCount = countWords(text);
                int sentenceCount = countSentences(text);
                int paragraphCount = countParagraphs(text);
                int lineCount = countLines(text);

                // Word frequency
                Map<String, Integer> wordFrequency = getWordFrequency(text);
                List<Map.Entry<String, Integer>> topWords = wordFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .collect(Collectors.toList());

                // Average sentence length
                double avgSentenceLength = sentenceCount > 0 ? (double) wordCount / sentenceCount : 0;

                // Reading time (assuming 200 words per minute)
                int readingTimeMinutes = (int) Math.ceil((double) wordCount / 200);

                // Speaking time (assuming 150 words per minute)
                int speakingTimeMinutes = (int) Math.ceil((double) wordCount / 150);

                StringBuilder sb = new StringBuilder();
                sb.append("文字統計結果：\n\n");
                sb.append("=== 基本統計 ===\n");
                sb.append(String.format("- 字元數（含空格）: %,d\n", charCount));
                sb.append(String.format("- 字元數（不含空格）: %,d\n", charCountNoSpaces));
                sb.append(String.format("- 字數: %,d\n", wordCount));
                sb.append(String.format("- 句子數: %,d\n", sentenceCount));
                sb.append(String.format("- 段落數: %,d\n", paragraphCount));
                sb.append(String.format("- 行數: %,d\n", lineCount));

                sb.append("\n=== 進階統計 ===\n");
                sb.append(String.format("- 平均句子長度: %.1f 字\n", avgSentenceLength));
                sb.append(String.format("- 預估閱讀時間: %d 分鐘\n", readingTimeMinutes));
                sb.append(String.format("- 預估朗讀時間: %d 分鐘\n", speakingTimeMinutes));

                sb.append("\n=== 最常見詞彙（前 10）===\n");
                for (int i = 0; i < topWords.size(); i++) {
                    Map.Entry<String, Integer> entry = topWords.get(i);
                    sb.append(String.format("%d. \"%s\" - %d 次\n", i + 1, entry.getKey(), entry.getValue()));
                }

                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("charCount", charCount);
                stats.put("charCountNoSpaces", charCountNoSpaces);
                stats.put("wordCount", wordCount);
                stats.put("sentenceCount", sentenceCount);
                stats.put("paragraphCount", paragraphCount);
                stats.put("lineCount", lineCount);
                stats.put("avgSentenceLength", avgSentenceLength);
                stats.put("readingTimeMinutes", readingTimeMinutes);
                stats.put("speakingTimeMinutes", speakingTimeMinutes);
                stats.put("topWords", topWords.stream()
                        .map(e -> Map.of("word", e.getKey(), "count", e.getValue()))
                        .collect(Collectors.toList()));

                return ToolResult.success(sb.toString(), stats);

            } catch (Exception e) {
                log.error("Text stats failed", e);
                return ToolResult.failure("文字統計失敗: " + e.getMessage());
            }
        });
    }

    private int countWords(String text) {
        if (text.isBlank()) return 0;

        // Handle both English and Chinese
        int count = 0;

        // Count English words
        String[] englishWords = text.split("[\\s\\p{Punct}]+");
        for (String word : englishWords) {
            if (!word.isEmpty() && word.matches(".*[a-zA-Z]+.*")) {
                count++;
            }
        }

        // Count CJK characters (each character is a "word")
        for (char c : text.toCharArray()) {
            if (Character.isIdeographic(c)) {
                count++;
            }
        }

        return count;
    }

    private int countSentences(String text) {
        if (text.isBlank()) return 0;

        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.getDefault());
        iterator.setText(text);

        int count = 0;
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countParagraphs(String text) {
        if (text.isBlank()) return 0;

        String[] paragraphs = text.split("\\n\\s*\\n");
        int count = 0;
        for (String p : paragraphs) {
            if (!p.trim().isEmpty()) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private int countLines(String text) {
        if (text.isEmpty()) return 0;

        int count = 1;
        for (char c : text.toCharArray()) {
            if (c == '\n') count++;
        }
        return count;
    }

    private Map<String, Integer> getWordFrequency(String text) {
        Map<String, Integer> frequency = new HashMap<>();

        // English words
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (word.length() >= 2 && word.matches("[a-zA-Z]+")) {
                // Skip common stop words
                if (!isStopWord(word)) {
                    frequency.merge(word, 1, Integer::sum);
                }
            }
        }

        // CJK words (2-character combinations are common in Chinese)
        String cjkOnly = text.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i < cjkOnly.length() - 1; i++) {
            String word = cjkOnly.substring(i, i + 2);
            frequency.merge(word, 1, Integer::sum);
        }

        return frequency;
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
                "be", "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "must", "this", "that", "these", "those", "it",
                "its", "they", "them", "their", "he", "she", "him", "her", "his", "we",
                "us", "our", "you", "your", "i", "me", "my"
        );
        return stopWords.contains(word);
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
