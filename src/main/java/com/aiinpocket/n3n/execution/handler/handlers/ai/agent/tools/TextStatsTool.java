package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Text statistics tool
 * Analyzes various statistical information about text
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
                Text statistics tool that analyzes various statistical information about text.

                Statistics include:
                - Character count (with/without spaces)
                - Word count
                - Sentence count
                - Paragraph count
                - Line count
                - Most frequent words
                - Average sentence length
                - Estimated reading time

                Parameters:
                - text: Text to analyze
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to analyze"
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
                    return ToolResult.failure("Text cannot be empty");
                }

                // Security: limit input size
                if (text.length() > 1_000_000) {
                    return ToolResult.failure("Text too long, maximum limit is 1MB");
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
                sb.append("Text statistics result:\n\n");
                sb.append("=== Basic Statistics ===\n");
                sb.append(String.format("- Characters (with spaces): %,d\n", charCount));
                sb.append(String.format("- Characters (without spaces): %,d\n", charCountNoSpaces));
                sb.append(String.format("- Words: %,d\n", wordCount));
                sb.append(String.format("- Sentences: %,d\n", sentenceCount));
                sb.append(String.format("- Paragraphs: %,d\n", paragraphCount));
                sb.append(String.format("- Lines: %,d\n", lineCount));

                sb.append("\n=== Advanced Statistics ===\n");
                sb.append(String.format("- Average sentence length: %.1f words\n", avgSentenceLength));
                sb.append(String.format("- Estimated reading time: %d minutes\n", readingTimeMinutes));
                sb.append(String.format("- Estimated speaking time: %d minutes\n", speakingTimeMinutes));

                sb.append("\n=== Most Frequent Words (Top 10) ===\n");
                for (int i = 0; i < topWords.size(); i++) {
                    Map.Entry<String, Integer> entry = topWords.get(i);
                    sb.append(String.format("%d. \"%s\" - %d occurrences\n", i + 1, entry.getKey(), entry.getValue()));
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
                return ToolResult.failure("Text statistics analysis failed");
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
