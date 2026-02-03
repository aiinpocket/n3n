package com.aiinpocket.n3n.ai.controller;

import com.aiinpocket.n3n.ai.dto.*;
import com.aiinpocket.n3n.ai.service.AIAssistantService;
import com.aiinpocket.n3n.auth.dto.response.UserResponse;
import com.aiinpocket.n3n.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
