package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.credential.service.CredentialService;
import com.aiinpocket.n3n.execution.handler.CredentialResolver;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.aiinpocket.n3n.execution.expression.N3nExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 單節點試打（probe）：在編排階段用「真的執行一次」取代盲猜——
 * 以目前設定實際呼叫節點（真打 API、真跑程式碼），回傳實際輸出，
 * 讓使用者有理有據地把資料串到下一個節點。
 *
 * 特性：
 * - 臨時執行：不建立 execution / node_execution 紀錄、不進 Redis 狀態，
 *   跑完即丟，不佔用保留空間（產出檔案掛在虛擬 probeId 下，由 housekeeping 孤兒清理回收）。
 * - 上游資料：呼叫端可傳入 previousOutputs（例如上一個節點試打的實際輸出），
 *   {{...}} 表達式會以這份資料求值，逐節點把流程「打通」。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeProbeService {

    private static final long PROBE_TIMEOUT_SECONDS = 60;

    private final NodeHandlerRegistry handlerRegistry;
    private final N3nExpressionEvaluator expressionEvaluator;
    private final CredentialService credentialService;

    private final ExecutorService probeExecutor =
        Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 試打結果：output 為節點實際輸出；probeId 供前端關聯試打產生的檔案。
     */
    public record ProbeResult(boolean success, Map<String, Object> output,
                              String errorMessage, long durationMs, UUID probeId) {}

    public ProbeResult probe(UUID userId, String nodeType, String nodeId,
                             Map<String, Object> config, Map<String, Object> previousOutputs) {
        UUID probeId = UUID.randomUUID();
        Instant start = Instant.now();
        String handlerType = normalizeNodeType(nodeType);

        NodeHandler handler = handlerRegistry.hasHandler(handlerType)
            ? handlerRegistry.getHandler(handlerType)
            : handlerRegistry.getHandler("action");

        Map<String, Object> outputs = previousOutputs != null ? previousOutputs : Map.of();

        CredentialResolver credentialResolver = new CredentialResolver() {
            @Override
            public Map<String, Object> resolve(UUID credentialId, UUID uid) {
                return credentialService.getDecryptedData(credentialId, uid);
            }

            @Override
            public boolean canAccess(UUID credentialId, UUID uid) {
                try {
                    credentialService.getDecryptedData(credentialId, uid);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };

        NodeExecutionContext baseContext = NodeExecutionContext.builder()
            .executionId(probeId)
            .nodeId(nodeId != null ? nodeId : "probe")
            .nodeType(handlerType)
            .nodeConfig(new HashMap<>(config != null ? config : Map.of()))
            .inputData(new HashMap<>())
            .globalContext(new HashMap<>())
            .previousOutputs(new HashMap<>(outputs))
            .userId(userId)
            .expressionEvaluator(expressionEvaluator)
            .credentialResolver(credentialResolver)
            .build();

        try {
            Map<String, Object> evaluatedConfig =
                expressionEvaluator.evaluateConfig(baseContext.getNodeConfig(), baseContext);

            NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(probeId)
                .nodeId(baseContext.getNodeId())
                .nodeType(handlerType)
                .nodeConfig(evaluatedConfig)
                .inputData(baseContext.getInputData())
                .globalContext(baseContext.getGlobalContext())
                .previousOutputs(baseContext.getPreviousOutputs())
                .userId(userId)
                .expressionEvaluator(expressionEvaluator)
                .credentialResolver(credentialResolver)
                .build();

            CompletableFuture<NodeExecutionResult> future =
                CompletableFuture.supplyAsync(() -> handler.execute(context), probeExecutor);
            NodeExecutionResult result = future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            if (result.isSuccess()) {
                return new ProbeResult(true, result.getOutput() != null ? result.getOutput() : Map.of(),
                    null, durationMs, probeId);
            }
            return new ProbeResult(false, null,
                result.getErrorMessage() != null ? result.getErrorMessage() : "Node execution failed",
                durationMs, probeId);
        } catch (TimeoutException e) {
            return new ProbeResult(false, null,
                "Probe timed out after " + PROBE_TIMEOUT_SECONDS + "s", elapsed(start), probeId);
        } catch (Exception e) {
            log.warn("Node probe failed: type={} error={}", nodeType, e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "Probe failed";
            return new ProbeResult(false, null, message, elapsed(start), probeId);
        }
    }

    private long elapsed(Instant start) {
        return Instant.now().toEpochMilli() - start.toEpochMilli();
    }

    /** 與 ExecutionService.normalizeNodeType 一致的別名對應。 */
    static String normalizeNodeType(String nodeType) {
        if (nodeType == null || nodeType.isEmpty()) {
            return "action";
        }
        return switch (nodeType.toLowerCase()) {
            case "input", "start" -> "trigger";
            case "end" -> "output";
            case "if", "branch" -> "condition";
            case "switch" -> "switch";
            case "foreach", "iterate" -> "loop";
            case "http", "api", "request", "httprequest" -> "httpRequest";
            case "script", "js", "javascript" -> "code";
            case "cron", "schedule", "scheduletrigger" -> "scheduleTrigger";
            case "delay", "sleep" -> "wait";
            case "webhooktrigger", "webhook" -> "webhookTrigger";
            case "formtrigger" -> "formTrigger";
            case "approval", "waitforapproval" -> "approval";
            case "ssh", "sshcommand", "remotecommand" -> "ssh";
            default -> nodeType;
        };
    }
}
