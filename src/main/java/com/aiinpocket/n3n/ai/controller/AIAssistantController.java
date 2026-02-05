package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.service.AIAssistantService;
import com.aiinpocket.n3n.ai.service.SimilarFlowsService;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-assistant")
@RequiredArgsConstructor
@Slf4j
public class AIAssistantController {

    private final AIAssistantService aiAssistantService;
    private final AuthService authService;
    private final SimilarFlowsService similarFlowsService;

    /**
     * AI 對話串流 API
     * POST /api/ai-assistant/chat/stream
     *
     * 使用 Server-Sent Events (SSE) 串流回應
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamChunk>> chatStream(
            @RequestBody ChatStreamRequest request,
            Principal principal) {
        log.info("Chat stream request: message={}",
            request.getMessage() != null ?
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())) + "..." : "null");

        UUID userId = getUserId(principal);
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
            @RequestBody ChatStreamRequest request,
            Principal principal) {
        log.info("Chat request: message={}",
            request.getMessage() != null ?
                request.getMessage().substring(0, Math.min(50, request.getMessage().length())) + "..." : "null");

        UUID userId = getUserId(principal);
        ChatResponse response = aiAssistantService.chat(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Analyze flow before publishing - returns optimization suggestions
     * POST /api/ai-assistant/analyze-for-publish
     */
    @PostMapping("/analyze-for-publish")
    public ResponseEntity<PublishAnalysisResponse> analyzeForPublish(
            @RequestBody AnalyzeForPublishRequest request,
            Principal principal) {
        log.info("Analyzing flow for publish: flowId={}, version={}",
            request.getFlowId(), request.getVersion());

        UUID userId = getUserId(principal);
        PublishAnalysisResponse response = aiAssistantService.analyzeForPublish(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Apply selected optimization suggestions
     * POST /api/ai-assistant/apply-suggestions
     */
    @PostMapping("/apply-suggestions")
    public ResponseEntity<ApplySuggestionsResponse> applySuggestions(
            @RequestBody ApplySuggestionsRequest request) {
        log.info("Applying {} suggestions to flow {}",
            request.getSuggestionIds().size(), request.getFlowId());

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
            @RequestParam(required = false) String category,
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
            @RequestBody NodeRecommendationRequest request,
            Principal principal) {
        log.info("Recommending nodes, searchQuery={}, category={}",
            request.getSearchQuery(), request.getCategory());

        UUID userId = getUserId(principal);
        NodeRecommendationResponse response = aiAssistantService.recommendNodes(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate a flow from natural language description
     * POST /api/ai-assistant/generate-flow
     */
    @PostMapping("/generate-flow")
    public ResponseEntity<GenerateFlowResponse> generateFlow(
            @RequestBody GenerateFlowRequest request,
            Principal principal) {
        log.info("Generating flow from natural language: {}",
            request.getUserInput() != null ? request.getUserInput().substring(0, Math.min(50, request.getUserInput().length())) + "..." : "null");

        UUID userId = getUserId(principal);
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
            @RequestBody GenerateFlowRequest request,
            Principal principal) {
        log.info("Starting flow generation stream: {}",
            request.getUserInput() != null ? request.getUserInput().substring(0, Math.min(50, request.getUserInput().length())) + "..." : "null");

        UUID userId = getUserId(principal);
        return aiAssistantService.generateFlowStream(request, userId)
            .map(chunk -> ServerSentEvent.<FlowGenerationChunk>builder()
                .data(chunk)
                .build());
    }

    /**
     * Get similar flows based on natural language description
     * GET /api/ai-assistant/similar-flows?query=xxx&limit=5
     */
    @GetMapping("/similar-flows")
    public ResponseEntity<List<SimilarFlow>> getSimilarFlows(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            Principal principal) {
        log.info("Finding similar flows for query: {}",
            query != null ? query.substring(0, Math.min(50, query.length())) + "..." : "null");

        UUID userId = getUserId(principal);
        if (userId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<SimilarFlow> flows = similarFlowsService.findSimilarFlows(userId, query, limit);
        return ResponseEntity.ok(flows);
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            UserResponse user = authService.getCurrentUser(principal.getName());
            return user.getId();
        } catch (Exception e) {
            log.debug("Could not get user ID from principal: {}", e.getMessage());
            return null;
        }
    }
}
