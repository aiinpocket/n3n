package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.conversation.ConversationManager;
import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.entity.Conversation;
import com.aiinpocket.n3n.ai.service.AIAssistantService;
import com.aiinpocket.n3n.ai.service.RequirementClarificationService;
import com.aiinpocket.n3n.ai.service.SimilarFlowsService;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.service.AuthService;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-assistant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Assistant", description = "AI assistant chat and flow generation")
public class AIAssistantController {

    private final AIAssistantService aiAssistantService;
    private final RequirementClarificationService requirementClarificationService;
    private final AuthService authService;
    private final SimilarFlowsService similarFlowsService;
    private final FlowShareService flowShareService;
    private final ConversationManager conversationManager;
    private final com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;
    private final com.aiinpocket.n3n.ai.service.GenerationProbeService generationProbeService;

    /**
     * AI 對話串流 API
     * POST /api/ai-assistant/chat/stream
     *
     * 使用 Server-Sent Events (SSE) 串流回應
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamChunk>> chatStream(
            @Valid @RequestBody ChatStreamRequest request,
            Principal principal) {
        log.info("Chat stream request: message={}",
            request.getMessage() != null ?
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())) + "..." : "null");

        UUID userId = requireUserId(principal);
        // Rate limit: 20 AI chat requests per minute per user
        ipRateLimiter.checkAllowed("ai-chat", userId.toString(), 20, 60);
        return aiAssistantService.chatStream(request, userId)
            .map(chunk -> ServerSentEvent.<ChatStreamChunk>builder()
                .data(chunk)
                .build());
    }

    /**
     * AI 對話 API (非串流)
     * POST /api/ai-assistant/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatStreamRequest request,
            Principal principal) {
        log.info("Chat request: message={}",
            request.getMessage() != null ?
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())) + "..." : "null");

        UUID userId = requireUserId(principal);
        // Rate limit: shares counter with chat/stream
        ipRateLimiter.checkAllowed("ai-chat", userId.toString(), 20, 60);
        ChatResponse response = aiAssistantService.chat(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Analyze flow before publishing - returns optimization suggestions
     * POST /api/ai-assistant/analyze-for-publish
     */
    @PostMapping("/analyze-for-publish")
    public ResponseEntity<PublishAnalysisResponse> analyzeForPublish(
            @Valid @RequestBody AnalyzeForPublishRequest request,
            Principal principal) {
        log.info("Analyzing flow for publish: flowId={}, version={}",
            request.getFlowId(), request.getVersion());

        UUID userId = requireUserId(principal);
        ipRateLimiter.checkAllowed("ai-analyze", userId.toString(), 10, 60);
        PublishAnalysisResponse response = aiAssistantService.analyzeForPublish(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Apply selected optimization suggestions
     * POST /api/ai-assistant/apply-suggestions
     */
    @PostMapping("/apply-suggestions")
    public ResponseEntity<ApplySuggestionsResponse> applySuggestions(
            @Valid @RequestBody ApplySuggestionsRequest request,
            Principal principal) {
        log.info("Applying {} suggestions to flow {}",
            request.getSuggestionIds().size(), request.getFlowId());

        UUID userId = requireUserId(principal);
        ipRateLimiter.checkAllowed("ai-apply", userId.toString(), 10, 60);
        if (request.getFlowId() != null) {
            UUID flowId = UUID.fromString(request.getFlowId());
            if (!flowShareService.hasEditAccess(flowId, userId)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        }

        ApplySuggestionsResponse response = aiAssistantService.applySuggestions(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all node categories with counts
     * GET /api/ai-assistant/node-categories
     */
    @GetMapping("/node-categories")
    public ResponseEntity<List<NodeCategoryInfo>> getNodeCategories(Principal principal) {
        UUID userId = getUserId(principal);
        List<NodeCategoryInfo> categories = aiAssistantService.getNodeCategories(userId);
        return ResponseEntity.ok(categories);
    }

    /**
     * Get installed/available nodes, optionally filtered by category
     * GET /api/ai-assistant/installed-nodes?category=messaging
     */
    @GetMapping("/installed-nodes")
    public ResponseEntity<List<InstalledNodeInfo>> getInstalledNodes(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 100) String category,
            Principal principal) {
        UUID userId = getUserId(principal);
        List<InstalledNodeInfo> nodes = aiAssistantService.getInstalledNodes(userId, category);
        return ResponseEntity.ok(nodes);
    }

    /**
     * Get AI-powered node recommendations based on current flow context
     * POST /api/ai-assistant/recommend-nodes
     */
    @PostMapping("/recommend-nodes")
    public ResponseEntity<NodeRecommendationResponse> recommendNodes(
            @Valid @RequestBody NodeRecommendationRequest request,
            Principal principal) {
        log.info("Recommending nodes, searchQuery={}, category={}",
            request.getSearchQuery(), request.getCategory());

        UUID userId = requireUserId(principal);
        ipRateLimiter.checkAllowed("ai-recommend", userId.toString(), 15, 60);
        NodeRecommendationResponse response = aiAssistantService.recommendNodes(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate a flow from natural language description
     * POST /api/ai-assistant/generate-flow
     */
    @PostMapping("/generate-flow")
    public ResponseEntity<GenerateFlowResponse> generateFlow(
            @Valid @RequestBody GenerateFlowRequest request,
            Principal principal) {
        log.info("Generating flow from natural language: {}",
            request.getUserInput() != null ? request.getUserInput().substring(0, Math.min(50, request.getUserInput().length())) + "..." : "null");

        UUID userId = requireUserId(principal);
        // Rate limit: 5 flow generation requests per minute per user
        ipRateLimiter.checkAllowed("ai-generate-flow", userId.toString(), 5, 60);
        GenerateFlowResponse response = aiAssistantService.generateFlow(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate a flow from natural language description with SSE streaming
     * POST /api/ai-assistant/generate-flow/stream
     *
     * Provides real-time progress updates during flow generation:
     * - thinking: AI 思考中
     * - progress: 進度更新 (0-100%)
     * - understanding: AI 理解的需求
     * - node_added: 新增節點 (可即時渲染)
     * - edge_added: 新增連線
     * - missing_nodes: 缺失的節點 (可安裝)
     * - done: 完成
     * - error: 錯誤
     */
    @PostMapping(value = "/generate-flow/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<FlowGenerationChunk>> generateFlowStream(
            @Valid @RequestBody GenerateFlowRequest request,
            Principal principal) {
        log.info("Starting flow generation stream: {}",
            request.getUserInput() != null ? request.getUserInput().substring(0, Math.min(50, request.getUserInput().length())) + "..." : "null");

        UUID userId = requireUserId(principal);
        // Rate limit: shares counter with generate-flow
        ipRateLimiter.checkAllowed("ai-generate-flow", userId.toString(), 5, 60);
        return aiAssistantService.generateFlowStream(request, userId)
            .map(chunk -> ServerSentEvent.<FlowGenerationChunk>builder()
                .data(chunk)
                .build());
    }

    /**
     * 回覆背景驗證的 node_input_required 詢問
     * POST /api/ai-assistant/generate-flow/probe-input
     * body: {sessionId, nodeId, skip, config}
     * skip=true 表示「先跳過這段，之後提供」；否則帶入補充設定後系統會真的執行一次
     */
    @PostMapping("/generate-flow/probe-input")
    public ResponseEntity<Map<String, Object>> submitProbeInput(
            @RequestBody Map<String, Object> body,
            Principal principal) {
        UUID userId = requireUserId(principal);
        ipRateLimiter.checkAllowed("ai-probe-input", userId.toString(), 60, 60);

        String sessionId = body.get("sessionId") instanceof String s ? s : null;
        String nodeId = body.get("nodeId") instanceof String s ? s : null;
        if (sessionId == null || sessionId.isBlank() || nodeId == null || nodeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("accepted", false, "error", "sessionId and nodeId are required"));
        }
        boolean skip = Boolean.TRUE.equals(body.get("skip"));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = body.get("config") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();

        boolean accepted = generationProbeService.submitInput(sessionId, nodeId, userId, skip, config);
        return ResponseEntity.ok(Map.of("accepted", accepted));
    }

    /**
     * Get similar flows based on natural language description
     * GET /api/ai-assistant/similar-flows?query=xxx&limit=5
     */
    @GetMapping("/similar-flows")
    public ResponseEntity<List<SimilarFlow>> getSimilarFlows(
            @RequestParam @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String query,
            @RequestParam(defaultValue = "5") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20) int limit,
            Principal principal) {
        log.info("Finding similar flows for query: {}",
            query != null ? query.substring(0, Math.min(50, query.length())) + "..." : "null");

        UUID userId = getUserId(principal);
        if (userId == null) {
            return ResponseEntity.ok(List.of());
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<SimilarFlow> flows = similarFlowsService.findSimilarFlows(userId, query, safeLimit);
        return ResponseEntity.ok(flows);
    }

    /**
     * Requirement clarification - multi-turn conversation to clarify flow requirements
     * POST /api/ai-assistant/clarify-requirements
     *
     * Helps users define their flow requirements through guided conversation.
     * AI asks clarifying questions until requirements are complete.
     */
    @PostMapping("/clarify-requirements")
    public ResponseEntity<RequirementClarificationResponse> clarifyRequirements(
            @Valid @RequestBody RequirementClarificationRequest request,
            Principal principal) {
        log.info("Clarifying requirements: message={}",
            request.getMessage() != null ?
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())) + "..." : "null");

        UUID userId = requireUserId(principal);
        ipRateLimiter.checkAllowed("ai-clarify", userId.toString(), 15, 60);
        RequirementClarificationResponse response = requirementClarificationService.clarify(request, userId);
        return ResponseEntity.ok(response);
    }

    // ==================== Conversation Management ====================

    /**
     * List user's conversations
     * GET /api/ai-assistant/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummary>> listConversations(
            @RequestParam(required = false) @Size(max = 100) String flowId,
            Principal principal) {
        UUID userId = getUserId(principal);
        if (userId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Conversation> conversations;
        if (flowId != null && !flowId.isBlank()) {
            conversations = conversationManager.getFlowConversations(userId, UUID.fromString(flowId));
        } else {
            conversations = conversationManager.getUserConversations(userId);
        }

        List<ConversationSummary> summaries = conversations.stream()
            .map(c -> ConversationSummary.builder()
                .id(c.getId())
                .title(c.getTitle())
                .flowId(c.getFlowId())
                .messageCount(c.getMessageCount())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null)
                .build())
            .toList();

        return ResponseEntity.ok(summaries);
    }

    /**
     * Get conversation history
     * GET /api/ai-assistant/conversations/{conversationId}
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @PathVariable UUID conversationId,
            Principal principal) {
        UUID userId = requireUserId(principal);
        return conversationManager.getConversation(conversationId)
            .filter(c -> userId.equals(c.getUserId()))
            .map(c -> {
                Map<String, Object> result = Map.of(
                    "id", c.getId(),
                    "title", c.getTitle() != null ? c.getTitle() : "",
                    "flowId", c.getFlowId() != null ? c.getFlowId().toString() : "",
                    "messages", c.getMessages() != null ? c.getMessages() : List.of(),
                    "summary", c.getSummary() != null ? c.getSummary() : "",
                    "messageCount", c.getMessageCount()
                );
                return ResponseEntity.ok(result);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete a conversation
     * DELETE /api/ai-assistant/conversations/{conversationId}
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID conversationId,
            Principal principal) {
        UUID userId = getUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        try {
            conversationManager.deleteConversation(conversationId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            UserResponse user = authService.getCurrentUser(principal.getName());
            return user.getId();
        } catch (Exception e) {
            log.debug("Could not resolve user from principal");
            return null;
        }
    }

    private UUID requireUserId(Principal principal) {
        UUID userId = getUserId(principal);
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return userId;
    }
}
