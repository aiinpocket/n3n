package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Responder Agent - 回應整理代理
 *
 * 職責：
 * 1. 整理最終回應格式
 * 2. 解釋流程內容
 * 3. 處理確認互動
 * 4. 回應閒聊
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponderAgent implements Agent {

    private final AgentRegistry agentRegistry;
    private final SimpleAIProviderRegistry providerRegistry;

    @PostConstruct
    public void init() {
        agentRegistry.register(this);
    }

    @Override
    public String getId() {
        return "responder";
    }

    @Override
    public String getName() {
        return "Responder Agent";
    }

    @Override
    public String getDescription() {
        return "回應整理代理，負責格式化回應、解釋流程、處理閒聊";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("explain", "clarify", "confirm", "chitchat", "format_response");
    }

    @Override
    public List<AgentTool> getTools() {
        return List.of(); // Responder 不需要工具
    }

    @Override
    public AgentResult execute(AgentContext context) {
        log.info("Responder Agent executing for intent: {}",
            context.getIntent() != null ? context.getIntent().getType() : "null");

        try {
            Intent intent = context.getIntent();
            if (intent == null) {
                return summarizeAndRespond(context);
            }

            return switch (intent.getType()) {
                case EXPLAIN -> explainFlow(context);
                case CLARIFY -> askClarification(context);
                case CONFIRM -> confirmAction(context);
                case CHITCHAT, UNKNOWN -> handleChitchat(context);
                default -> summarizeAndRespond(context);
            };

        } catch (Exception e) {
            log.error("Responder Agent execution failed", e);
            return AgentResult.error("回應失敗: " + e.getMessage());
        }
    }

    @Override
    public Flux<AgentStreamChunk> executeStream(AgentContext context) {
        return Flux.create(sink -> {
            try {
                AgentResult result = execute(context);

                if (result.isSuccess()) {
                    // 將回應內容分段輸出，模擬打字效果
                    String content = result.getContent();
                    if (content != null && !content.isEmpty()) {
                        // 按句子分段
                        String[] sentences = content.split("(?<=[。！？\n])");
                        for (String sentence : sentences) {
                            if (!sentence.isBlank()) {
                                sink.next(AgentStreamChunk.text(sentence));
                            }
                        }
                    }

                    if (result.getFlowDefinition() != null) {
                        sink.next(AgentStreamChunk.structured(Map.of(
                            "action", "update_flow",
                            "flowDefinition", result.getFlowDefinition()
                        )));
                    }

                    if (result.getPendingChanges() != null && !result.getPendingChanges().isEmpty()) {
                        sink.next(AgentStreamChunk.structured(Map.of(
                            "action", "pending_changes",
                            "changes", result.getPendingChanges()
                        )));
                    }
                } else {
                    sink.next(AgentStreamChunk.error(result.getError()));
                }

                sink.next(AgentStreamChunk.done());
                sink.complete();
            } catch (Exception e) {
                log.error("Responder stream failed", e);
                sink.next(AgentStreamChunk.error(e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 解釋流程
     */
    private AgentResult explainFlow(AgentContext context) {
        WorkingFlowDraft draft = context.getFlowDraft();

        // 如果沒有流程，嘗試從當前節點建立
        if ((draft == null || !draft.hasContent()) &&
            context.getCurrentNodes() != null && !context.getCurrentNodes().isEmpty()) {

            draft = new WorkingFlowDraft();
            Map<String, Object> definition = Map.of(
                "nodes", context.getCurrentNodes(),
                "edges", context.getCurrentEdges() != null ? context.getCurrentEdges() : List.of()
            );
            draft.initializeFromDefinition(definition);
            context.setFlowDraft(draft);
        }

        if (draft == null || !draft.hasContent()) {
            return AgentResult.success("目前沒有流程可以解釋。您想要建立一個新流程嗎？");
        }

        // 使用 AI 生成解釋（如果可用）
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(
            "responder", context.getUserId());

        if (provider.isAvailable()) {
            try {
                String flowDescription = describeFlow(draft);
                String prompt = String.format("""
                    以下是一個工作流程的結構:
                    %s

                    請用簡潔易懂的方式解釋這個流程做了什麼事。
                    包含：
                    1. 流程的觸發方式
                    2. 主要步驟說明
                    3. 最終輸出或結果
                    """, flowDescription);

                String response = provider.chat(prompt, EXPLAIN_SYSTEM_PROMPT, 1000, 0.5);
                return AgentResult.success(response);

            } catch (Exception e) {
                log.warn("AI explanation failed, using rule-based", e);
            }
        }

        // Fallback: 規則式解釋
        return ruleBasedExplanation(draft);
    }

    private AgentResult ruleBasedExplanation(WorkingFlowDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 流程說明\n\n");

        List<WorkingFlowDraft.Node> nodes = draft.getNodes();
        List<WorkingFlowDraft.Edge> edges = draft.getEdges();

        sb.append("此流程包含 **").append(nodes.size()).append("** 個節點：\n\n");

        // 找出起始節點（沒有入邊的節點）
        Set<String> hasIncoming = new HashSet<>();
        for (WorkingFlowDraft.Edge edge : edges) {
            hasIncoming.add(edge.target());
        }

        List<WorkingFlowDraft.Node> startNodes = nodes.stream()
            .filter(n -> !hasIncoming.contains(n.id()))
            .toList();

        if (!startNodes.isEmpty()) {
            sb.append("**起始點**: ");
            for (WorkingFlowDraft.Node node : startNodes) {
                sb.append(node.label()).append(" (`").append(node.type()).append("`) ");
            }
            sb.append("\n\n");
        }

        sb.append("**節點列表**:\n");
        int index = 1;
        for (WorkingFlowDraft.Node node : nodes) {
            sb.append(index++).append(". **").append(node.label())
                .append("** - ").append(getNodeTypeDescription(node.type())).append("\n");
        }

        sb.append("\n**連接關係**:\n");
        for (WorkingFlowDraft.Edge edge : edges) {
            String sourceName = findNodeLabel(nodes, edge.source());
            String targetName = findNodeLabel(nodes, edge.target());
            sb.append("- ").append(sourceName).append(" → ").append(targetName).append("\n");
        }

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .flowDefinition(draft.toDefinition())
            .build();
    }

    /**
     * 詢問澄清
     */
    private AgentResult askClarification(AgentContext context) {
        String understanding = context.getIntent() != null ?
            context.getIntent().getUnderstanding() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("我需要更多資訊來協助您。\n\n");

        if (understanding != null && !understanding.isBlank()) {
            sb.append("我理解您想要: ").append(understanding).append("\n\n");
        }

        sb.append("請問您可以提供更多細節嗎？例如：\n");
        sb.append("- 流程的觸發方式（定時、Webhook、手動）\n");
        sb.append("- 需要處理什麼資料\n");
        sb.append("- 最終要執行什麼動作\n");

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .requiresFollowUp(true)
            .build();
    }

    /**
     * 確認動作
     */
    private AgentResult confirmAction(AgentContext context) {
        // 檢查是否有待確認的變更
        List<AgentResult.PendingChange> pendingChanges = context.getFromMemory(
            "pendingChanges", List.class);

        if (pendingChanges == null || pendingChanges.isEmpty()) {
            return AgentResult.success("目前沒有待確認的變更。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 待確認變更\n\n");
        sb.append("以下變更將被套用：\n\n");

        for (AgentResult.PendingChange change : pendingChanges) {
            sb.append("- **").append(change.getType()).append("**: ")
                .append(change.getDescription()).append("\n");
        }

        sb.append("\n請確認是否要套用這些變更？");

        return AgentResult.builder()
            .success(true)
            .content(sb.toString())
            .pendingChanges(pendingChanges)
            .build();
    }

    /**
     * 處理閒聊
     */
    private AgentResult handleChitchat(AgentContext context) {
        String userInput = context.getUserInput();

        // 常見問候
        if (containsAny(userInput.toLowerCase(), "你好", "hi", "hello", "哈囉")) {
            return AgentResult.success(
                "你好！我是 N3N 流程助手。我可以幫您：\n" +
                "- 建立新的工作流程\n" +
                "- 搜尋可用的節點\n" +
                "- 解釋現有流程\n" +
                "- 優化流程設計\n\n" +
                "請問有什麼我可以協助的？"
            );
        }

        if (containsAny(userInput.toLowerCase(), "謝謝", "感謝", "thank")) {
            return AgentResult.success("不客氣！如果還有其他需要，隨時告訴我。");
        }

        if (containsAny(userInput.toLowerCase(), "幫助", "help", "怎麼用")) {
            return AgentResult.success(
                "## 使用說明\n\n" +
                "我可以幫助您建立和管理工作流程。您可以：\n\n" +
                "**建立流程**:\n" +
                "- 「建立一個每天發送報表的流程」\n" +
                "- 「幫我做一個監控網站的自動化」\n\n" +
                "**搜尋節點**:\n" +
                "- 「有什麼節點可以發送郵件？」\n" +
                "- 「資料庫相關的節點有哪些？」\n\n" +
                "**修改流程**:\n" +
                "- 「加一個錯誤處理節點」\n" +
                "- 「把 HTTP 請求節點的 URL 改成...」\n\n" +
                "**其他**:\n" +
                "- 「解釋這個流程」\n" +
                "- 「優化這個流程」"
            );
        }

        // 使用 AI 回應
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(
            "responder", context.getUserId());

        if (provider.isAvailable()) {
            try {
                String response = provider.chat(
                    userInput,
                    CHITCHAT_SYSTEM_PROMPT,
                    500,
                    0.7
                );
                return AgentResult.success(response);
            } catch (Exception e) {
                log.warn("AI chitchat failed", e);
            }
        }

        return AgentResult.success(
            "我可能沒有完全理解您的意思。\n" +
            "如果您想建立或修改流程，請告訴我具體的需求。\n" +
            "或者您可以說「幫助」來查看使用說明。"
        );
    }

    /**
     * 總結並回應
     */
    private AgentResult summarizeAndRespond(AgentContext context) {
        // 收集工作記憶中的結果
        Map<String, Object> validationResult = context.getFromMemory("validationResult", Map.class);
        List<Map<String, Object>> searchResults = context.getFromMemory("searchResults", List.class);
        List<Map<String, Object>> discoveryResults = context.getFromMemory("discoveryResults", List.class);

        WorkingFlowDraft draft = context.getFlowDraft();

        StringBuilder sb = new StringBuilder();

        // 如果有流程，總結流程狀態
        if (draft != null && draft.hasContent()) {
            sb.append("## 流程狀態\n\n");
            sb.append("目前流程包含 **").append(draft.getNodeCount()).append("** 個節點");
            if (draft.getEdgeCount() > 0) {
                sb.append("和 **").append(draft.getEdgeCount()).append("** 個連接");
            }
            sb.append("。\n\n");

            // 加入驗證結果
            if (validationResult != null) {
                Boolean valid = (Boolean) validationResult.get("valid");
                if (valid != null && valid) {
                    sb.append("✅ 流程驗證通過\n");
                } else {
                    sb.append("⚠️ 流程有待處理的問題\n");
                    @SuppressWarnings("unchecked")
                    List<String> errors = (List<String>) validationResult.get("errors");
                    if (errors != null) {
                        for (String error : errors) {
                            sb.append("- ").append(error).append("\n");
                        }
                    }
                }
            }

            return AgentResult.builder()
                .success(true)
                .content(sb.toString())
                .flowDefinition(draft.toDefinition())
                .build();
        }

        // 如果沒有流程，根據上下文回應
        sb.append("請問您想要做什麼？我可以幫您：\n\n");
        sb.append("- 建立新的工作流程\n");
        sb.append("- 搜尋可用的節點\n");
        sb.append("- 取得節點使用說明\n");

        return AgentResult.success(sb.toString());
    }

    private String describeFlow(WorkingFlowDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("節點:\n");
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            sb.append("- ").append(node.id()).append(": ")
                .append(node.label()).append(" (").append(node.type()).append(")\n");
        }
        sb.append("\n連接:\n");
        for (WorkingFlowDraft.Edge edge : draft.getEdges()) {
            sb.append("- ").append(edge.source()).append(" -> ").append(edge.target()).append("\n");
        }
        return sb.toString();
    }

    private String findNodeLabel(List<WorkingFlowDraft.Node> nodes, String nodeId) {
        return nodes.stream()
            .filter(n -> n.id().equals(nodeId))
            .map(WorkingFlowDraft.Node::label)
            .findFirst()
            .orElse(nodeId);
    }

    private String getNodeTypeDescription(String type) {
        return switch (type) {
            case "trigger", "manualTrigger" -> "手動觸發流程";
            case "scheduleTrigger" -> "排程觸發器";
            case "webhookTrigger" -> "Webhook 觸發器";
            case "httpRequest" -> "發送 HTTP 請求";
            case "sendEmail" -> "發送電子郵件";
            case "database", "postgres", "mysql" -> "資料庫操作";
            case "code" -> "執行程式碼";
            case "condition" -> "條件判斷";
            case "loop" -> "迴圈處理";
            case "slack" -> "發送 Slack 訊息";
            case "telegram" -> "發送 Telegram 訊息";
            case "action" -> "執行動作";
            default -> type + " 節點";
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static final String EXPLAIN_SYSTEM_PROMPT = """
        你是一個流程解釋專家。用簡潔、易懂的繁體中文解釋工作流程。
        說明時要：
        1. 先總結流程的目的
        2. 解釋每個步驟做什麼
        3. 說明資料如何流動
        """;

    private static final String CHITCHAT_SYSTEM_PROMPT = """
        你是 N3N 流程助手，一個友善的自動化流程建構助手。
        用繁體中文回應，保持簡潔友善。
        如果使用者的問題與流程建構無關，友善地引導他們使用流程功能。
        """;
}
