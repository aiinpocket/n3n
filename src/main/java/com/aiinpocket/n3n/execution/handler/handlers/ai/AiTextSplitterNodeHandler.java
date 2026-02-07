package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Text Splitter node handler.
 *
 * Splits text into chunks suitable for RAG pipelines, embedding generation,
 * or any operation that requires processing text in smaller segments.
 *
 * Operations:
 * - characterSplit: Split by character count with overlap
 * - sentenceSplit: Split on sentence boundaries
 * - paragraphSplit: Split on paragraph boundaries (double newline)
 * - recursiveSplit: Recursively split using multiple separators until chunk size is met
 *
 * Config:
 * - text: Input text to split
 * - chunkSize: Maximum size of each chunk (default: 1000)
 * - chunkOverlap: Number of overlapping characters between chunks (default: 200)
 * - separator: Custom separator for splitting (optional)
 * - operation: Splitting strategy to use
 *
 * Output:
 * - chunks: Array of text chunks
 * - totalChunks: Number of chunks produced
 * - metadata: Statistics about the splitting operation
 */
@Component
@Slf4j
public class AiTextSplitterNodeHandler extends AbstractNodeHandler {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;
    private static final int MIN_CHUNK_SIZE = 10;
    private static final int MAX_CHUNK_SIZE = 100_000;

    // Sentence boundary pattern: period/question/exclamation followed by space or end
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "(?<=[.!?])\\s+(?=[A-Z\u4e00-\u9fff])|(?<=[.!?])$"
    );

    // Paragraph boundary: two or more newlines
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n");

    @Override
    public String getType() {
        return "aiTextSplitter";
    }

    @Override
    public String getDisplayName() {
        return "AI Text Splitter";
    }

    @Override
    public String getDescription() {
        return "Split text into chunks for RAG pipelines, embeddings, or batch processing.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "split-cells";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String text = getStringConfig(context, "text", "");
        String operation = getStringConfig(context, "operation", "characterSplit");
        int chunkSize = getIntConfig(context, "chunkSize", DEFAULT_CHUNK_SIZE);
        int chunkOverlap = getIntConfig(context, "chunkOverlap", DEFAULT_CHUNK_OVERLAP);
        String separator = getStringConfig(context, "separator", "");

        // Get text from input data if not configured
        if (text.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("text");
            if (data == null) data = context.getInputData().get("input");
            if (data == null) data = context.getInputData().get("data");
            if (data != null) text = data.toString();
        }

        if (text.isEmpty()) {
            return NodeExecutionResult.failure("Input text is required");
        }

        // Validate parameters
        chunkSize = Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSize));
        chunkOverlap = Math.max(0, Math.min(chunkSize - 1, chunkOverlap));

        try {
            List<String> chunks = switch (operation) {
                case "characterSplit" -> characterSplit(text, chunkSize, chunkOverlap, separator);
                case "sentenceSplit" -> sentenceSplit(text, chunkSize, chunkOverlap);
                case "paragraphSplit" -> paragraphSplit(text, chunkSize, chunkOverlap);
                case "recursiveSplit" -> recursiveSplit(text, chunkSize, chunkOverlap);
                default -> null;
            };

            if (chunks == null) {
                return NodeExecutionResult.failure("Unknown operation: " + operation);
            }

            // Build metadata
            Map<String, Object> metadata = buildMetadata(text, chunks, chunkSize, chunkOverlap, operation);

            Map<String, Object> output = new HashMap<>();
            output.put("chunks", chunks);
            output.put("totalChunks", chunks.size());
            output.put("metadata", metadata);

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .build();

        } catch (Exception e) {
            log.error("Text splitting failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Text splitting failed: " + e.getMessage());
        }
    }

    /**
     * Split text by character count with overlap.
     * If a custom separator is provided, tries to split on that separator
     * while respecting the chunk size.
     */
    private List<String> characterSplit(String text, int chunkSize, int chunkOverlap,
                                         String separator) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();

        if (!separator.isEmpty()) {
            // Split on separator first, then merge into chunks
            String[] segments = text.split(Pattern.quote(separator));
            chunks = mergeSegments(segments, chunkSize, chunkOverlap, separator);
        } else {
            // Simple character-based splitting with overlap
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                String chunk = text.substring(start, end).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                start = end - chunkOverlap;
                if (start >= text.length()) break;
                // Prevent infinite loop
                if (end == text.length()) break;
            }
        }

        return chunks;
    }

    /**
     * Split text on sentence boundaries, merging sentences into chunks
     * that respect the maximum chunk size.
     */
    private List<String> sentenceSplit(String text, int chunkSize, int chunkOverlap) {
        // Split into sentences
        List<String> sentences = splitBySentences(text);

        if (sentences.size() <= 1) {
            // If only one sentence and it's too long, fall back to character split
            if (text.length() > chunkSize) {
                return characterSplit(text, chunkSize, chunkOverlap, "");
            }
            return List.of(text);
        }

        return mergeSegments(
            sentences.toArray(new String[0]),
            chunkSize,
            chunkOverlap,
            " "
        );
    }

    /**
     * Split text on paragraph boundaries (double newlines), merging paragraphs
     * into chunks that respect the maximum chunk size.
     */
    private List<String> paragraphSplit(String text, int chunkSize, int chunkOverlap) {
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);

        if (paragraphs.length <= 1) {
            // Fall back to sentence splitting
            return sentenceSplit(text, chunkSize, chunkOverlap);
        }

        return mergeSegments(paragraphs, chunkSize, chunkOverlap, "\n\n");
    }

    /**
     * Recursively split text using progressively finer separators
     * until all chunks meet the size requirement.
     *
     * Separator hierarchy: paragraph -> newline -> sentence -> space -> character
     */
    private List<String> recursiveSplit(String text, int chunkSize, int chunkOverlap) {
        String[] separators = {"\n\n", "\n", ". ", " ", ""};
        return recursiveSplitInternal(text, chunkSize, chunkOverlap, separators, 0);
    }

    private List<String> recursiveSplitInternal(String text, int chunkSize, int chunkOverlap,
                                                 String[] separators, int level) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        if (level >= separators.length) {
            // At finest level (empty separator = character), do simple character split
            return characterSplit(text, chunkSize, chunkOverlap, "");
        }

        String separator = separators[level];
        String[] parts;

        if (separator.isEmpty()) {
            return characterSplit(text, chunkSize, chunkOverlap, "");
        } else {
            parts = text.split(Pattern.quote(separator));
        }

        if (parts.length <= 1) {
            // Separator not found, try next level
            return recursiveSplitInternal(text, chunkSize, chunkOverlap, separators, level + 1);
        }

        // Merge small parts and recursively split large ones
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            if (current.isEmpty()) {
                current.append(trimmed);
            } else if (current.length() + separator.length() + trimmed.length() <= chunkSize) {
                current.append(separator).append(trimmed);
            } else {
                // Current chunk is full
                String chunkText = current.toString().trim();
                if (!chunkText.isEmpty()) {
                    if (chunkText.length() > chunkSize) {
                        // Still too large, recurse with finer separator
                        result.addAll(recursiveSplitInternal(
                            chunkText, chunkSize, chunkOverlap, separators, level + 1));
                    } else {
                        result.add(chunkText);
                    }
                }

                // Handle overlap
                if (chunkOverlap > 0 && !result.isEmpty()) {
                    String lastChunk = result.get(result.size() - 1);
                    int overlapStart = Math.max(0, lastChunk.length() - chunkOverlap);
                    String overlap = lastChunk.substring(overlapStart);
                    current = new StringBuilder(overlap);
                    if (current.length() + separator.length() + trimmed.length() <= chunkSize) {
                        current.append(separator).append(trimmed);
                    } else {
                        current = new StringBuilder(trimmed);
                    }
                } else {
                    current = new StringBuilder(trimmed);
                }
            }
        }

        // Add remaining content
        if (!current.isEmpty()) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.length() > chunkSize) {
                    result.addAll(recursiveSplitInternal(
                        remaining, chunkSize, chunkOverlap, separators, level + 1));
                } else {
                    result.add(remaining);
                }
            }
        }

        return result;
    }

    /**
     * Split text into sentences using regex-based boundary detection.
     */
    private List<String> splitBySentences(String text) {
        List<String> sentences = new ArrayList<>();
        // Use a simpler approach: split on sentence-ending punctuation followed by space
        String[] parts = text.split("(?<=[.!?])\\s+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * Merge text segments into chunks that respect the maximum chunk size,
     * with overlap between consecutive chunks.
     */
    private List<String> mergeSegments(String[] segments, int chunkSize, int chunkOverlap,
                                        String joinSeparator) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;

            if (current.isEmpty()) {
                current.append(trimmed);
            } else if (current.length() + joinSeparator.length() + trimmed.length() <= chunkSize) {
                current.append(joinSeparator).append(trimmed);
            } else {
                // Save current chunk
                chunks.add(current.toString().trim());

                // Start new chunk with overlap from previous
                if (chunkOverlap > 0) {
                    String prev = current.toString();
                    int overlapStart = Math.max(0, prev.length() - chunkOverlap);
                    String overlap = prev.substring(overlapStart).trim();
                    current = new StringBuilder(overlap);
                    if (!current.isEmpty()) {
                        current.append(joinSeparator);
                    }
                    current.append(trimmed);
                } else {
                    current = new StringBuilder(trimmed);
                }
            }
        }

        // Add final chunk
        if (!current.isEmpty()) {
            String finalChunk = current.toString().trim();
            if (!finalChunk.isEmpty()) {
                chunks.add(finalChunk);
            }
        }

        return chunks;
    }

    /**
     * Build metadata about the splitting operation.
     */
    private Map<String, Object> buildMetadata(String originalText, List<String> chunks,
                                               int chunkSize, int chunkOverlap, String operation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalLength", originalText.length());
        metadata.put("chunkSize", chunkSize);
        metadata.put("chunkOverlap", chunkOverlap);
        metadata.put("operation", operation);
        metadata.put("totalChunks", chunks.size());

        if (!chunks.isEmpty()) {
            int minLen = chunks.stream().mapToInt(String::length).min().orElse(0);
            int maxLen = chunks.stream().mapToInt(String::length).max().orElse(0);
            double avgLen = chunks.stream().mapToInt(String::length).average().orElse(0.0);

            metadata.put("minChunkLength", minLen);
            metadata.put("maxChunkLength", maxLen);
            metadata.put("avgChunkLength", Math.round(avgLen * 100.0) / 100.0);
        }

        return metadata;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Split Strategy",
                    "enum", List.of("characterSplit", "sentenceSplit", "paragraphSplit", "recursiveSplit"),
                    "default", "recursiveSplit",
                    "description", "Strategy for splitting text into chunks"
                ),
                "text", Map.of(
                    "type", "string",
                    "title", "Text",
                    "description", "Input text to split into chunks"
                ),
                "chunkSize", Map.of(
                    "type", "integer",
                    "title", "Chunk Size",
                    "description", "Maximum number of characters per chunk",
                    "default", DEFAULT_CHUNK_SIZE
                ),
                "chunkOverlap", Map.of(
                    "type", "integer",
                    "title", "Chunk Overlap",
                    "description", "Number of overlapping characters between consecutive chunks",
                    "default", DEFAULT_CHUNK_OVERLAP
                ),
                "separator", Map.of(
                    "type", "string",
                    "title", "Custom Separator",
                    "description", "Custom separator for character split mode (optional)"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "text", "type", "string", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "chunks", "type", "array"),
                Map.of("name", "totalChunks", "type", "number"),
                Map.of("name", "metadata", "type", "object")
            )
        );
    }
}
