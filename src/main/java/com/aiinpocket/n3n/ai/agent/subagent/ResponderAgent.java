package com.aiinpocket.n3n.ai.agent.subagent;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.ai.provider.AssistantAiClient;
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
    private final AssistantAiClient aiClient;

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
        return "Responder agent for formatting responses, explaining flows, and handling chitchat";
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
            return AgentResult.error("Response failed");
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
                sink.next(AgentStreamChunk.error("Response failed"));
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
            return AgentResult.success("No flow available to explain. Would you like to create a new one?");
        }

        if (aiClient.isAvailable(context.getUserId())) {
            try {
                String flowDescription = describeFlow(draft);
                String prompt = String.format("""
                    Here is a workflow structure:
                    %s

                    Explain what this flow does in a clear, concise way.
                    Respond in the same language the user used.
                    Include:
                    1. How the flow is triggered
                    2. Main steps description
                    3. Final output or result
                    """, flowDescription);

                String response = aiClient.chat(prompt, EXPLAIN_SYSTEM_PROMPT, 1000, 0.5, context.getUserId());
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
        sb.append("## Flow Description\n\n");

        List<WorkingFlowDraft.Node> nodes = draft.getNodes();
        List<WorkingFlowDraft.Edge> edges = draft.getEdges();

        sb.append("This flow contains **").append(nodes.size()).append("** nodes:\n\n");

        Set<String> hasIncoming = new HashSet<>();
        for (WorkingFlowDraft.Edge edge : edges) {
            hasIncoming.add(edge.target());
        }

        List<WorkingFlowDraft.Node> startNodes = nodes.stream()
            .filter(n -> !hasIncoming.contains(n.id()))
            .toList();

        if (!startNodes.isEmpty()) {
            sb.append("**Start**: ");
            for (WorkingFlowDraft.Node node : startNodes) {
                sb.append(node.label()).append(" (`").append(node.type()).append("`) ");
            }
            sb.append("\n\n");
        }

        sb.append("**Nodes**:\n");
        int index = 1;
        for (WorkingFlowDraft.Node node : nodes) {
            sb.append(index++).append(". **").append(node.label())
                .append("** - ").append(getNodeTypeDescription(node.type())).append("\n");
        }

        sb.append("\n**Connections**:\n");
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
        sb.append("I need more information to help you.\n\n");

        if (understanding != null && !understanding.isBlank()) {
            sb.append("I understand you want to: ").append(understanding).append("\n\n");
        }

        sb.append("Could you provide more details? For example:\n");
        sb.append("- How the flow should be triggered (schedule, webhook, manual)\n");
        sb.append("- What data needs to be processed\n");
        sb.append("- What actions should be performed at the end\n");

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
            return AgentResult.success("No pending changes to confirm.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Pending Changes\n\n");
        sb.append("The following changes will be applied:\n\n");

        for (AgentResult.PendingChange change : pendingChanges) {
            sb.append("- **").append(change.getType()).append("**: ")
                .append(change.getDescription()).append("\n");
        }

        sb.append("\nWould you like to apply these changes?");

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

        if (containsAny(userInput.toLowerCase(), "你好", "hi", "hello", "哈囉", "こんにちは")) {
            return AgentResult.success(
                "Hello! I'm the N3N Flow Assistant. I can help you:\n" +
                "- Create new workflows\n" +
                "- Search for available nodes\n" +
                "- Explain existing flows\n" +
                "- Optimize flow design\n\n" +
                "What would you like to do?"
            );
        }

        if (containsAny(userInput.toLowerCase(), "謝謝", "感謝", "thank", "ありがとう")) {
            return AgentResult.success("You're welcome! Let me know if you need anything else.");
        }

        if (containsAny(userInput.toLowerCase(), "幫助", "help", "怎麼用", "ヘルプ")) {
            return AgentResult.success(
                "## Usage Guide\n\n" +
                "I can help you build and manage workflows. Try:\n\n" +
                "**Create flows**:\n" +
                "- \"Create a daily report flow\"\n" +
                "- \"Build a website monitoring automation\"\n\n" +
                "**Search nodes**:\n" +
                "- \"What nodes can send emails?\"\n" +
                "- \"Show me database nodes\"\n\n" +
                "**Modify flows**:\n" +
                "- \"Add an error handler node\"\n" +
                "- \"Change the HTTP node URL to...\"\n\n" +
                "**Other**:\n" +
                "- \"Explain this flow\"\n" +
                "- \"Optimize this flow\""
            );
        }

        if (aiClient.isAvailable(context.getUserId())) {
            try {
                String response = aiClient.chat(
                    userInput,
                    CHITCHAT_SYSTEM_PROMPT,
                    500,
                    0.7,
                    context.getUserId()
                );
                return AgentResult.success(response);
            } catch (Exception e) {
                log.warn("AI chitchat failed", e);
            }
        }

        return AgentResult.success(
            "I may not have fully understood your request.\n" +
            "If you'd like to create or modify a flow, please tell me your specific needs.\n" +
            "You can also type \"help\" to see usage instructions."
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

        if (draft != null && draft.hasContent()) {
            sb.append("## Flow Status\n\n");
            sb.append("Current flow has **").append(draft.getNodeCount()).append("** nodes");
            if (draft.getEdgeCount() > 0) {
                sb.append(" and **").append(draft.getEdgeCount()).append("** connections");
            }
            sb.append(".\n\n");

            if (validationResult != null) {
                Boolean valid = (Boolean) validationResult.get("valid");
                if (valid != null && valid) {
                    sb.append("Flow validation passed\n");
                } else {
                    sb.append("Flow has issues that need attention\n");
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

        sb.append("What would you like to do? I can help you:\n\n");
        sb.append("- Create a new workflow\n");
        sb.append("- Search for available nodes\n");
        sb.append("- Get node documentation\n");

        return AgentResult.success(sb.toString());
    }

    private String describeFlow(WorkingFlowDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nodes:\n");
        for (WorkingFlowDraft.Node node : draft.getNodes()) {
            sb.append("- ").append(node.id()).append(": ")
                .append(node.label()).append(" (").append(node.type()).append(")\n");
        }
        sb.append("\nConnections:\n");
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
            case "trigger", "manualTrigger" -> "Manual trigger";
            case "scheduleTrigger" -> "Schedule trigger";
            case "webhookTrigger" -> "Webhook trigger";
            case "httpRequest" -> "HTTP request";
            case "sendEmail" -> "Send email";
            case "database", "postgres", "mysql" -> "Database operation";
            case "code" -> "Execute code";
            case "condition" -> "Condition check";
            case "loop" -> "Loop processing";
            case "slack" -> "Slack message";
            case "telegram" -> "Telegram message";
            case "action" -> "Execute action";
            default -> type + " node";
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static final String EXPLAIN_SYSTEM_PROMPT = """
        You are a workflow explanation expert. Explain workflows in a clear, concise manner.
        Respond in the same language the user used (Chinese, English, or Japanese).
        When explaining:
        1. First summarize the flow's purpose
        2. Explain what each step does
        3. Describe how data flows through the pipeline
        """;

    private static final String CHITCHAT_SYSTEM_PROMPT = """
        You are the N3N Flow Assistant, a friendly workflow automation assistant.
        Respond in the same language the user used (Chinese, English, or Japanese).
        Keep responses concise and friendly.
        If the user's question is unrelated to workflow building, gently guide them toward flow features.
        """;
}
