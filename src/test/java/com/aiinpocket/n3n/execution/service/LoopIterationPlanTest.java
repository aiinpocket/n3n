package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.flow.service.DagParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * loop 逐項迭代的計畫判定與項目抽取（純邏輯，不觸發實際執行）。
 */
@ExtendWith(MockitoExtension.class)
class LoopIterationPlanTest {

    @org.mockito.Mock private com.aiinpocket.n3n.execution.repository.ExecutionRepository executionRepository;
    @org.mockito.Mock private com.aiinpocket.n3n.execution.repository.NodeExecutionRepository nodeExecutionRepository;
    @org.mockito.Mock private com.aiinpocket.n3n.flow.repository.FlowRepository flowRepository;
    @org.mockito.Mock private com.aiinpocket.n3n.flow.repository.FlowVersionRepository flowVersionRepository;
    @org.mockito.Mock private DagParser dagParser;
    @org.mockito.Mock private StateManager stateManager;
    @org.mockito.Mock private ExecutionNotificationService notificationService;
    @org.mockito.Mock private com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry handlerRegistry;
    @org.mockito.Mock private com.aiinpocket.n3n.execution.expression.N3nExpressionEvaluator expressionEvaluator;
    @org.mockito.Mock private com.aiinpocket.n3n.credential.service.CredentialService credentialService;
    @org.mockito.Mock private com.aiinpocket.n3n.activity.service.ActivityService activityService;
    @org.mockito.Mock private com.aiinpocket.n3n.flow.service.FlowShareService flowShareService;

    @InjectMocks
    private ExecutionService executionService;

    private static Map<String, Object> node(String id, String type) {
        return Map.of("id", id, "type", type, "position", Map.of("x", 0, "y", 0), "data", Map.of());
    }

    private static DagParser.FlowEdge edge(String source, String target) {
        DagParser.FlowEdge e = new DagParser.FlowEdge();
        e.setSource(source);
        e.setTarget(target);
        return e;
    }

    private static Map<String, Object> definition(List<Map<String, Object>> nodes) {
        return Map.of("nodes", nodes, "edges", List.of());
    }

    @Test
    @DisplayName("線性 body 至 aggregate 收集點 → 產生迭代計畫")
    void linearBodyWithAggregateCollector_plansIteration() {
        Map<String, Object> def = definition(List.of(
            node("1", "trigger"), node("2", "setFields"), node("3", "loop"),
            node("4", "httpRequest"), node("5", "httpRequest"), node("6", "aiChat"),
            node("7", "aggregate"), node("8", "aiChat")));
        List<DagParser.FlowEdge> edges = List.of(
            edge("1", "2"), edge("2", "3"), edge("3", "4"),
            edge("4", "5"), edge("5", "6"), edge("6", "7"), edge("7", "8"));

        Object plan = ReflectionTestUtils.invokeMethod(executionService, "computeLoopPlan", "3", def, edges);

        assertThat(plan).isNotNull();
        assertThat((List<String>) ReflectionTestUtils.invokeMethod(plan, "bodyIds"))
            .containsExactly("4", "5", "6");
        assertThat((String) ReflectionTestUtils.invokeMethod(plan, "collectorId")).isEqualTo("7");
    }

    @Test
    @DisplayName("body 中有分支 → 不迭代（回傳 null）")
    void branchingBody_returnsNull() {
        Map<String, Object> def = definition(List.of(
            node("3", "loop"), node("4", "httpRequest"), node("5", "httpRequest"), node("7", "merge")));
        List<DagParser.FlowEdge> edges = List.of(
            edge("3", "4"), edge("3", "5"), edge("4", "7"), edge("5", "7"));

        Object plan = ReflectionTestUtils.invokeMethod(executionService, "computeLoopPlan", "3", def, edges);

        assertThat(plan).isNull();
    }

    @Test
    @DisplayName("沒有 merge/aggregate 收集點 → 不迭代")
    void noCollector_returnsNull() {
        Map<String, Object> def = definition(List.of(
            node("3", "loop"), node("4", "httpRequest"), node("5", "saveArtifact")));
        List<DagParser.FlowEdge> edges = List.of(edge("3", "4"), edge("4", "5"));

        Object plan = ReflectionTestUtils.invokeMethod(executionService, "computeLoopPlan", "3", def, edges);

        assertThat(plan).isNull();
    }

    @Test
    @DisplayName("extractLoopItems：優先取 items，否則攤平 batches")
    void extractLoopItems_prefersItemsThenBatches() {
        List<Object> fromItems = ReflectionTestUtils.invokeMethod(
            executionService, "extractLoopItems",
            Map.of("items", List.of("ONDS", "VST")));
        assertThat(fromItems).containsExactly("ONDS", "VST");

        List<Object> fromBatches = ReflectionTestUtils.invokeMethod(
            executionService, "extractLoopItems",
            Map.of("batches", List.of(
                Map.of("items", List.of("A")),
                Map.of("items", List.of("B")))));
        assertThat(fromBatches).containsExactly("A", "B");
    }
}
