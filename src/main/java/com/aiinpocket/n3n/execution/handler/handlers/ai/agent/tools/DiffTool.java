package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 文字差異比較工具
 * 比較兩段文字的差異
 */
@Component
@Slf4j
public class DiffTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "diff";
    }

    @Override
    public String getName() {
        return "Text Diff";
    }

    @Override
    public String getDescription() {
        return """
                文字差異比較工具，比較兩段文字的差異。

                操作類型：
                - line: 逐行比較（預設）
                - word: 逐字比較
                - char: 逐字元比較

                參數：
                - text1: 原始文字
                - text2: 比較文字
                - mode: 比較模式（line/word/char）
                - contextLines: 上下文行數（預設 3）

                輸出格式：
                - 開頭的 - 表示刪除
                - 開頭的 + 表示新增
                - 無符號表示不變
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text1", Map.of(
                                "type", "string",
                                "description", "原始文字"
                        ),
                        "text2", Map.of(
                                "type", "string",
                                "description", "比較文字"
                        ),
                        "mode", Map.of(
                                "type", "string",
                                "enum", List.of("line", "word", "char"),
                                "description", "比較模式",
                                "default", "line"
                        ),
                        "contextLines", Map.of(
                                "type", "integer",
                                "description", "上下文行數",
                                "default", 3
                        )
                ),
                "required", List.of("text1", "text2")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text1 = (String) parameters.get("text1");
                String text2 = (String) parameters.get("text2");
                String mode = (String) parameters.getOrDefault("mode", "line");
                int contextLines = parameters.containsKey("contextLines")
                        ? ((Number) parameters.get("contextLines")).intValue()
                        : 3;

                if (text1 == null) text1 = "";
                if (text2 == null) text2 = "";

                // Security: limit input size
                if (text1.length() > 500_000 || text2.length() > 500_000) {
                    return ToolResult.failure("文字過長，最大限制 500KB");
                }

                List<DiffEntry> diff = switch (mode) {
                    case "word" -> diffByWord(text1, text2);
                    case "char" -> diffByChar(text1, text2);
                    default -> diffByLine(text1, text2);
                };

                // Count statistics
                int additions = 0;
                int deletions = 0;
                int unchanged = 0;
                for (DiffEntry entry : diff) {
                    switch (entry.type) {
                        case ADD -> additions++;
                        case DELETE -> deletions++;
                        case EQUAL -> unchanged++;
                    }
                }

                // Format output
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("差異比較結果（%s 模式）：\n", mode));
                sb.append(String.format("- 新增: %d\n", additions));
                sb.append(String.format("- 刪除: %d\n", deletions));
                sb.append(String.format("- 不變: %d\n\n", unchanged));

                if (additions == 0 && deletions == 0) {
                    sb.append("兩段文字完全相同");
                } else {
                    sb.append("差異內容：\n");
                    int outputCount = 0;
                    for (DiffEntry entry : diff) {
                        if (outputCount >= 200) {
                            sb.append("...(省略剩餘差異)\n");
                            break;
                        }
                        switch (entry.type) {
                            case ADD -> sb.append("+ ").append(entry.text).append("\n");
                            case DELETE -> sb.append("- ").append(entry.text).append("\n");
                            case EQUAL -> {
                                if (contextLines > 0 || diff.size() < 50) {
                                    sb.append("  ").append(entry.text).append("\n");
                                }
                            }
                        }
                        outputCount++;
                    }
                }

                return ToolResult.success(sb.toString(), Map.of(
                        "additions", additions,
                        "deletions", deletions,
                        "unchanged", unchanged,
                        "mode", mode,
                        "identical", additions == 0 && deletions == 0
                ));

            } catch (Exception e) {
                log.error("Diff failed", e);
                return ToolResult.failure("差異比較失敗: " + e.getMessage());
            }
        });
    }

    private List<DiffEntry> diffByLine(String text1, String text2) {
        String[] lines1 = text1.split("\n", -1);
        String[] lines2 = text2.split("\n", -1);
        return computeDiff(Arrays.asList(lines1), Arrays.asList(lines2));
    }

    private List<DiffEntry> diffByWord(String text1, String text2) {
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");
        return computeDiff(Arrays.asList(words1), Arrays.asList(words2));
    }

    private List<DiffEntry> diffByChar(String text1, String text2) {
        List<String> chars1 = new ArrayList<>();
        List<String> chars2 = new ArrayList<>();
        for (char c : text1.toCharArray()) chars1.add(String.valueOf(c));
        for (char c : text2.toCharArray()) chars2.add(String.valueOf(c));
        return computeDiff(chars1, chars2);
    }

    /**
     * Simple LCS-based diff algorithm
     */
    private List<DiffEntry> computeDiff(List<String> list1, List<String> list2) {
        int m = list1.size();
        int n = list2.size();

        // Security: limit complexity
        if ((long) m * n > 10_000_000L) {
            // Fall back to simple line-by-line comparison
            return simpleDiff(list1, list2);
        }

        // LCS dynamic programming
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (list1.get(i - 1).equals(list2.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack to build diff
        List<DiffEntry> result = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && list1.get(i - 1).equals(list2.get(j - 1))) {
                result.add(0, new DiffEntry(DiffType.EQUAL, list1.get(i - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, new DiffEntry(DiffType.ADD, list2.get(j - 1)));
                j--;
            } else {
                result.add(0, new DiffEntry(DiffType.DELETE, list1.get(i - 1)));
                i--;
            }
        }

        return result;
    }

    private List<DiffEntry> simpleDiff(List<String> list1, List<String> list2) {
        List<DiffEntry> result = new ArrayList<>();
        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);

        for (String s : list1) {
            if (set2.contains(s)) {
                result.add(new DiffEntry(DiffType.EQUAL, s));
            } else {
                result.add(new DiffEntry(DiffType.DELETE, s));
            }
        }
        for (String s : list2) {
            if (!set1.contains(s)) {
                result.add(new DiffEntry(DiffType.ADD, s));
            }
        }
        return result;
    }

    private enum DiffType {
        ADD, DELETE, EQUAL
    }

    private record DiffEntry(DiffType type, String text) {}

    @Override
    public String getCategory() {
        return "text";
    }
}
