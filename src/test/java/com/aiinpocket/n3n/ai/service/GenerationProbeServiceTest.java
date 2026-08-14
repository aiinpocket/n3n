package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.execution.service.NodeProbeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationProbeServiceTest {

    @Mock
    private NodeProbeService nodeProbeService;

    @Mock
    private NodeHandlerRegistry handlerRegistry;

    @Mock
    private AssistantAiClient aiClient;

    private GenerationProbeService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GenerationProbeService(nodeProbeService, handlerRegistry, aiClient, new ObjectMapper());
        lenient().when(aiClient.chat(anyString(), anyString(), anyInt(), anyDouble(), any()))
            .thenThrow(new RuntimeException("no ai in test"));
    }

    private Map<String, Object> node(String id, String type) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("label", id);
        n.put("config", new HashMap<String, Object>());
        return n;
    }

    @Test
    void safeNodeProbeSuccessIsVerified() {
        when(nodeProbeService.probe(eq(userId), eq("code"), eq("n1"), any(), any(), anyLong()))
            .thenReturn(new NodeProbeService.ProbeResult(true, Map.of("ok", 1), null, 5, UUID.randomUUID()));

        List<GenerationProbeService.NodeVerification> results = service.verifyFlow(
            userId, "s1", List.of(node("n1", "code")), List.of(), v -> {}, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("verified");
    }

    @Test
    void sideEffectNodeWithoutInteractionIsSkippedWithMockOutput() {
        // onInputRequired 為 null（非互動情境）→ 視為跳過，不執行有副作用節點
        List<GenerationProbeService.NodeVerification> results = service.verifyFlow(
            userId, "s2", List.of(node("n1", "sendEmail")), List.of(), v -> {}, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("skipped");
    }

    @Test
    void sideEffectNodeRunsForRealAfterUserProvides() throws Exception {
        when(nodeProbeService.probe(eq(userId), eq("sendEmail"), eq("n1"), any(), any(), anyLong()))
            .thenReturn(new NodeProbeService.ProbeResult(true, Map.of("sent", true), null, 8, UUID.randomUUID()));

        AtomicReference<GenerationProbeService.InputRequest> asked = new AtomicReference<>();
        CompletableFuture<List<GenerationProbeService.NodeVerification>> run =
            CompletableFuture.supplyAsync(() -> service.verifyFlow(
                userId, "s3", List.of(node("n1", "sendEmail")), List.of(),
                v -> {}, asked::set, () -> {}));

        // 等待詢問出現後模擬使用者提供設定
        long deadline = System.currentTimeMillis() + 5000;
        while (asked.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(asked.get()).isNotNull();
        assertThat(asked.get().sideEffect()).isTrue();

        boolean accepted = service.submitInput("s3", "n1", userId, false, Map.of("to", "a@b.c"));
        assertThat(accepted).isTrue();

        List<GenerationProbeService.NodeVerification> results = run.get(10, TimeUnit.SECONDS);
        assertThat(results.get(0).status()).isEqualTo("verified");
        assertThat(results.get(0).repairedConfig()).containsEntry("to", "a@b.c");
    }

    @Test
    void userSkipContinuesDownstreamWithMockData() throws Exception {
        // n1 被使用者跳過後，n2 仍會被試打（下游不斷鏈）
        when(nodeProbeService.probe(eq(userId), eq("code"), eq("n2"), any(), any(), anyLong()))
            .thenReturn(new NodeProbeService.ProbeResult(true, Map.of("ok", 1), null, 3, UUID.randomUUID()));

        AtomicReference<GenerationProbeService.InputRequest> asked = new AtomicReference<>();
        CompletableFuture<List<GenerationProbeService.NodeVerification>> run =
            CompletableFuture.supplyAsync(() -> service.verifyFlow(
                userId, "s4", List.of(node("n1", "sendEmail"), node("n2", "code")),
                List.of(Map.of("source", "n1", "target", "n2")),
                v -> {}, asked::set, () -> {}));

        long deadline = System.currentTimeMillis() + 5000;
        while (asked.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        boolean accepted = service.submitInput("s4", "n1", userId, true, null);
        assertThat(accepted).isTrue();

        List<GenerationProbeService.NodeVerification> results = run.get(10, TimeUnit.SECONDS);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("skipped");
        assertThat(results.get(1).status()).isEqualTo("verified");
    }

    @Test
    void cancelSessionWakesWaitingVerificationThread() throws Exception {
        AtomicReference<GenerationProbeService.InputRequest> asked = new AtomicReference<>();
        CompletableFuture<List<GenerationProbeService.NodeVerification>> run =
            CompletableFuture.supplyAsync(() -> service.verifyFlow(
                userId, "s5", List.of(node("n1", "sendEmail")), List.of(),
                v -> {}, asked::set, () -> {}));

        long deadline = System.currentTimeMillis() + 5000;
        while (asked.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(asked.get()).isNotNull();

        // 模擬 SSE 串流結束：cancelSession 必須立刻喚醒等待中的驗證緒
        service.cancelSession("s5");
        List<GenerationProbeService.NodeVerification> results = run.get(5, TimeUnit.SECONDS);
        assertThat(results.get(0).status()).isEqualTo("skipped");
    }

    @Test
    void submitInputRejectsWrongUser() throws Exception {
        AtomicReference<GenerationProbeService.InputRequest> asked = new AtomicReference<>();
        CompletableFuture<List<GenerationProbeService.NodeVerification>> run =
            CompletableFuture.supplyAsync(() -> service.verifyFlow(
                userId, "s6", List.of(node("n1", "sendEmail")), List.of(),
                v -> {}, asked::set, () -> {}));

        long deadline = System.currentTimeMillis() + 5000;
        while (asked.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(service.submitInput("s6", "n1", UUID.randomUUID(), true, null)).isFalse();

        service.cancelSession("s6");
        run.get(5, TimeUnit.SECONDS);
    }

    @Test
    void hasSideEffectClassification() {
        assertThat(GenerationProbeService.hasSideEffect("sendEmail", Map.of())).isTrue();
        assertThat(GenerationProbeService.hasSideEffect("code", Map.of())).isFalse();
        assertThat(GenerationProbeService.hasSideEffect("httpRequest", Map.of("method", "GET"))).isFalse();
        assertThat(GenerationProbeService.hasSideEffect("httpRequest", Map.of("method", "POST"))).isTrue();
    }
}
