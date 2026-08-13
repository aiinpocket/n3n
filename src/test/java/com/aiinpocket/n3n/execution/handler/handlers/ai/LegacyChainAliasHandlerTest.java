package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.service.AiService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyChainAliasHandlerTest {

    @Mock
    private AiService aiService;

    @Mock
    private MemoryStore memoryStore;

    private LegacyChainAliasHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LegacyChainAliasHandler(aiService, memoryStore);
    }

    private NodeExecutionContext context(Map<String, Object> config, Map<String, Object> input) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .nodeType("aiChain")
                .nodeConfig(config)
                .inputData(input)
                .build();
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiChain() {
            assertThat(handler.getType()).isEqualTo("aiChain");
        }

        @Test
        void getDisplayName_returnsAIChain() {
            assertThat(handler.getDisplayName()).isEqualTo("AI Chain");
        }

        @Test
        void getCategory_returnsAI() {
            assertThat(handler.getCategory()).isEqualTo("AI");
        }

        @Test
        void getDescription_marksNodeAsDeprecatedAlias() {
            assertThat(handler.getDescription()).contains("aiPipeline");
            assertThat(handler.getDescription()).contains("相容舊流程");
        }
    }

    // ==================== Config Schema ====================

    @Nested
    @DisplayName("Config Schema")
    class ConfigSchemaTests {

        @Test
        void getConfigSchema_keepsLegacyProperties() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKeys("chainType", "promptTemplate", "model", "timeout");
        }

        @Test
        void getInterfaceDefinition_keepsLegacyPorts() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKeys("inputs", "outputs");
        }
    }

    // ==================== LLM Chain ====================

    @Nested
    @DisplayName("LLM Chain")
    class LlmChainTests {

        @Test
        void llmChain_formatsTemplateAndReturnsOutput() {
            when(aiService.generateText("Translate: hello")).thenReturn("你好");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "llm");
            config.put("promptTemplate", "Translate: {input}");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "hello")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "你好");
        }

        @Test
        void llmChain_withModel_passesModelToAiService() {
            when(aiService.generateText("hi", "gpt-4o")).thenReturn("hello");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "llm");
            config.put("promptTemplate", "{input}");
            config.put("model", "gpt-4o");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "hi")));

            assertThat(result.isSuccess()).isTrue();
            verify(aiService).generateText("hi", "gpt-4o");
        }

        @Test
        void defaultChainType_isLlm() {
            when(aiService.generateText(anyString())).thenReturn("ok");

            NodeExecutionResult result = handler.execute(
                    context(new HashMap<>(), Map.of("input", "test")));

            assertThat(result.isSuccess()).isTrue();
            verify(aiService).generateText("test");
        }
    }

    // ==================== Conversation Chain ====================

    @Nested
    @DisplayName("Conversation Chain")
    class ConversationChainTests {

        @Test
        void conversationChain_usesMemoryAndReturnsConversationId() {
            when(memoryStore.getHistory(anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(
                            MemoryStore.MemoryEntry.user("earlier question"),
                            MemoryStore.MemoryEntry.assistant("earlier answer"))));
            when(memoryStore.store(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(aiService.generateText(anyString())).thenReturn("the answer");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "conversation");
            config.put("conversationId", "conv-42");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "my question")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "the answer");
            assertThat(result.getOutput()).containsEntry("conversation_id", "conv-42");
            verify(memoryStore).getHistory(eq("legacy-chain:conv-42"), anyInt());
            verify(memoryStore, times(2)).store(eq("legacy-chain:conv-42"), any());
        }

        @Test
        void conversationChain_includesHistoryInPrompt() {
            when(memoryStore.getHistory(anyString(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(
                            MemoryStore.MemoryEntry.user("q1"))));
            when(memoryStore.store(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(aiService.generateText(anyString())).thenAnswer(inv -> {
                String prompt = inv.getArgument(0);
                assertThat(prompt).contains("Conversation History:");
                assertThat(prompt).contains("User: q1");
                assertThat(prompt).contains("User: q2");
                return "a2";
            });

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "conversation");
            config.put("conversationId", "conv-h");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "q2")));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void conversationChain_withoutInput_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "conversation");

            NodeExecutionResult result = handler.execute(context(config, Map.of()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("No input provided");
            verify(memoryStore, never()).store(anyString(), any());
        }
    }

    // ==================== Sequential Chain ====================

    @Nested
    @DisplayName("Sequential Chain")
    class SequentialChainTests {

        @Test
        void sequentialChain_pipesOutputBetweenSteps() {
            when(aiService.generateText("step1: start")).thenReturn("mid");
            when(aiService.generateText("step2: mid")).thenReturn("end");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "sequential");
            config.put("steps", List.of(
                    Map.of("promptTemplate", "step1: {input}"),
                    Map.of("promptTemplate", "step2: {output}")));

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "start")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "end");
        }

        @Test
        void sequentialChain_withoutSteps_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "sequential");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x")));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("steps");
        }

        @Test
        void sequentialChain_returnIntermediates_includesIntermediates() {
            when(aiService.generateText(anyString())).thenReturn("r");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "sequential");
            config.put("returnIntermediates", true);
            config.put("steps", List.of(Map.of("promptTemplate", "{input}")));

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("intermediates");
        }
    }

    // ==================== Router Chain ====================

    @Nested
    @DisplayName("Router Chain")
    class RouterChainTests {

        @Test
        void routerChain_routesByRouteKeyInInput() {
            when(aiService.generateText("math: 1+1")).thenReturn("2");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "router");
            config.put("routes", Map.of(
                    "math", Map.of("promptTemplate", "math: {input}"),
                    "other", Map.of("promptTemplate", "other: {input}")));

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "1+1", "route", "math")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "2");
        }

        @Test
        void routerChain_withAiRouting_selectsRouteFromResponse() {
            when(aiService.generateText("choose for: hi from math, chat")).thenReturn("chat");
            when(aiService.generateText("chat: hi")).thenReturn("hello there");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "router");
            config.put("routingPrompt", "choose for: {input} from {routes}");
            Map<String, Map<String, Object>> routes = new LinkedHashMap<>();
            routes.put("math", Map.of("promptTemplate", "math: {input}"));
            routes.put("chat", Map.of("promptTemplate", "chat: {input}"));
            config.put("routes", routes);

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "hi")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "hello there");
        }

        @Test
        void routerChain_unknownRoute_fallsBackToDefaultRoute() {
            when(aiService.generateText("fallback: x")).thenReturn("fb");

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "router");
            config.put("defaultRoute", "fb");
            config.put("routes", Map.of(
                    "fb", Map.of("promptTemplate", "fallback: {input}")));

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x", "route", "nonexistent")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("output", "fb");
        }

        @Test
        void routerChain_withoutRoutes_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "router");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x")));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("routes");
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        void unknownChainType_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "banana");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x")));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown chain type");
        }

        @Test
        void aiFailure_returnsSanitizedError() {
            when(aiService.generateText(anyString()))
                    .thenThrow(new RuntimeException("provider exploded"));

            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "llm");

            NodeExecutionResult result = handler.execute(
                    context(config, Map.of("input", "x")));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Chain execution failed");
        }
    }
}
