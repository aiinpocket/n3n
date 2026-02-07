package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AiTextSplitterNodeHandlerTest {

    private AiTextSplitterNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiTextSplitterNodeHandler();
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType() {
            assertThat(handler.getType()).isEqualTo("aiTextSplitter");
        }

        @Test
        void getDisplayName() {
            assertThat(handler.getDisplayName()).contains("Text Splitter");
        }

        @Test
        void getCategory() {
            assertThat(handler.getCategory()).isEqualTo("AI");
        }

        @Test
        void getConfigSchema() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition() {
            assertThat(handler.getInterfaceDefinition())
                    .containsKey("inputs")
                    .containsKey("outputs");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_isNotBlank() {
            assertThat(handler.getIcon()).isNotBlank();
        }

        @Test
        void getConfigSchema_containsOperationProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("operation");
            assertThat(properties).containsKey("text");
            assertThat(properties).containsKey("chunkSize");
            assertThat(properties).containsKey("chunkOverlap");
            assertThat(properties).containsKey("separator");
        }
    }

    // ==================== Character Split ====================

    @Nested
    @DisplayName("Character Split")
    class CharacterSplit {

        @Test
        void splitsLongTextIntoMultipleChunks() {
            String longText = "A".repeat(2500);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", longText,
                    "chunkSize", 1000,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
            assertThat(result.getOutput().get("totalChunks")).isEqualTo(chunks.size());
        }

        @Test
        void respectsChunkSizeLimit() {
            String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(4); // 104 chars

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
            for (String chunk : chunks) {
                assertThat(chunk.length()).isLessThanOrEqualTo(30);
            }
        }

        @Test
        void smallTextReturnsOneChunk() {
            String text = "Short text.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 1000,
                    "chunkOverlap", 200
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo("Short text.");
        }

        @Test
        void withOverlapProducesOverlappingChunks() {
            String text = "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH IIII JJJJ KKKK LLLL MMMM NNNN OOOO PPPP";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 25,
                    "chunkOverlap", 10
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(2);
        }

        @Test
        void withCustomSeparator_splitsOnSeparator() {
            String text = "Part1|||Part2|||Part3|||Part4|||Part5|||Part6|||Part7|||Part8|||Part9|||Part10";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 0,
                    "separator", "|||"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void veryLongText_splitsCorrectly() {
            String longText = "Word ".repeat(5000); // ~25000 chars

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", longText,
                    "chunkSize", 500,
                    "chunkOverlap", 50
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(10);
        }

        @Test
        void unicodeText_splitsCorrectly() {
            String text = "\u4F60\u597D\u4E16\u754C\u3002".repeat(50); // Chinese characters

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 5
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
            for (String chunk : chunks) {
                assertThat(chunk).isNotEmpty();
            }
        }
    }

    // ==================== Sentence Split ====================

    @Nested
    @DisplayName("Sentence Split")
    class SentenceSplit {

        @Test
        void splitsOnSentenceBoundaries() {
            String text = "First sentence. Second sentence. Third sentence. Fourth sentence. Fifth sentence.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "sentenceSplit",
                    "text", text,
                    "chunkSize", 40,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void singleSentence_returnsOneChunk() {
            String text = "This is a single sentence.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "sentenceSplit",
                    "text", text,
                    "chunkSize", 1000,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSize(1);
        }

        @Test
        void handlesQuestionMarksAndExclamations() {
            String text = "Is this a question? Yes it is! Another sentence here. Final one!";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "sentenceSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }
    }

    // ==================== Paragraph Split ====================

    @Nested
    @DisplayName("Paragraph Split")
    class ParagraphSplit {

        @Test
        void splitsOnParagraphBoundaries() {
            String text = "First paragraph content here.\n\nSecond paragraph content here.\n\nThird paragraph content here.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "paragraphSplit",
                    "text", text,
                    "chunkSize", 50,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void multipleParagraphBreaks_handledCorrectly() {
            String text = "Para 1 content.\n\nPara 2 content.\n\nPara 3 content.\n\nPara 4 content.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "paragraphSplit",
                    "text", text,
                    "chunkSize", 1000,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).isNotEmpty();
        }

        @Test
        void noParagraphBoundaries_fallsBackToSentenceSplit() {
            String text = "Sentence one. Sentence two. Sentence three. Sentence four. Sentence five.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "paragraphSplit",
                    "text", text,
                    "chunkSize", 40,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }
    }

    // ==================== Recursive Split ====================

    @Nested
    @DisplayName("Recursive Split")
    class RecursiveSplit {

        @Test
        void handlesNestedSplitting() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append("Paragraph ").append(i).append(" sentence one. Sentence two. Sentence three.");
                if (i < 4) sb.append("\n\n");
            }
            String text = sb.toString();

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "recursiveSplit",
                    "text", text,
                    "chunkSize", 60,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void smallTextWithRecursiveSplit_returnsOneChunk() {
            String text = "A short text.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "recursiveSplit",
                    "text", text,
                    "chunkSize", 1000,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSize(1);
        }
    }

    // ==================== Output Metadata ====================

    @Nested
    @DisplayName("Output Metadata")
    class OutputMetadata {

        @Test
        void metadataContainsAllExpectedFields() {
            String text = "First chunk content. Second chunk content. Third chunk content.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 25,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("metadata");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("metadata");
            assertThat(metadata).containsKey("originalLength");
            assertThat(metadata).containsKey("chunkSize");
            assertThat(metadata).containsKey("chunkOverlap");
            assertThat(metadata).containsKey("operation");
            assertThat(metadata).containsKey("totalChunks");
            assertThat(metadata).containsKey("minChunkLength");
            assertThat(metadata).containsKey("maxChunkLength");
            assertThat(metadata).containsKey("avgChunkLength");
        }

        @Test
        void metadataHasCorrectOriginalLength() {
            String text = "A".repeat(100);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("metadata");
            assertThat(((Number) metadata.get("originalLength")).intValue()).isEqualTo(100);
        }

        @Test
        void metadataMinLessThanOrEqualToMax() {
            String text = "A".repeat(100);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("metadata");
            double avgLen = ((Number) metadata.get("avgChunkLength")).doubleValue();
            assertThat(avgLen).isGreaterThan(0);
            int minLen = ((Number) metadata.get("minChunkLength")).intValue();
            int maxLen = ((Number) metadata.get("maxChunkLength")).intValue();
            assertThat(minLen).isLessThanOrEqualTo(maxLen);
        }

        @Test
        void totalChunksMatchesChunkListSize() {
            String text = "A".repeat(200);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 50,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            int totalChunks = ((Number) result.getOutput().get("totalChunks")).intValue();
            assertThat(totalChunks).isEqualTo(chunks.size());
        }
    }

    // ==================== Edge Cases & Validation ====================

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesAndValidation {

        @Test
        void emptyText_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "characterSplit");

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input text is required");
        }

        @Test
        void nullText_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "characterSplit");
            config.put("text", null);
            config.put("chunkSize", 100);

            NodeExecutionContext context = buildContext(config);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Input text is required");
        }

        @Test
        void unknownOperation_returnsFailure() {
            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "tokenSplit",
                    "text", "Some text to split"
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }

        @Test
        void zeroChunkSize_clampedToMinimum() {
            String text = "Some text that needs splitting.";

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 0,
                    "chunkOverlap", 0
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).isNotEmpty();
        }

        @Test
        void negativeOverlap_clampedToZero() {
            String text = "A".repeat(100);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 30,
                    "chunkOverlap", -10
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void largeOverlap_clampedToChunkSizeMinusOne() {
            String text = "A".repeat(200);

            NodeExecutionContext context = buildContext(Map.of(
                    "operation", "characterSplit",
                    "text", text,
                    "chunkSize", 50,
                    "chunkOverlap", 100
            ));

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("metadata");
            int chunkOverlap = ((Number) metadata.get("chunkOverlap")).intValue();
            assertThat(chunkOverlap).isLessThan(50);
        }

        @Test
        void textFromInputData_splitsCorrectly() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "characterSplit");
            config.put("chunkSize", 20);
            config.put("chunkOverlap", 0);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("text", "This is text from input data field for splitting.");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("splitter-1")
                    .nodeType("aiTextSplitter")
                    .nodeConfig(new HashMap<>(config))
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void textFromInputInputField_splitsCorrectly() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "characterSplit");
            config.put("chunkSize", 20);
            config.put("chunkOverlap", 0);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("input", "This is text from the input field for splitting test.");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("splitter-1")
                    .nodeType("aiTextSplitter")
                    .nodeConfig(new HashMap<>(config))
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        void textFromInputDataField_splitsCorrectly() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "characterSplit");
            config.put("chunkSize", 20);
            config.put("chunkOverlap", 0);

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("data", "This is text from the data field for splitting test.");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("splitter-1")
                    .nodeType("aiTextSplitter")
                    .nodeConfig(new HashMap<>(config))
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .flowId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<String> chunks = (List<String>) result.getOutput().get("chunks");
            assertThat(chunks).hasSizeGreaterThan(1);
        }
    }

    // ==================== Helper Methods ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("splitter-1")
                .nodeType("aiTextSplitter")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
