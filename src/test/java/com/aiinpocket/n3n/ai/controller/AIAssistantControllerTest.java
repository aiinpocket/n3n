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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIAssistantControllerTest {

    @Mock
    private AIAssistantService aiAssistantService;

    @Mock
    private RequirementClarificationService requirementClarificationService;

    @Mock
    private AuthService authService;

    @Mock
    private SimilarFlowsService similarFlowsService;

    @Mock
    private FlowShareService flowShareService;

    @Mock
    private ConversationManager conversationManager;

    @Mock
    private com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    @Mock
    private com.aiinpocket.n3n.ai.service.GenerationProbeService generationProbeService;

    @InjectMocks
    private AIAssistantController controller;

    private UUID testUserId() {
        return UUID.randomUUID();
    }

    private Principal testPrincipal(String name) {
        return () -> name;
    }

    private void mockAuthService(UUID userId) {
        UserResponse userResponse = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .name("Test User")
                .roles(List.of("ROLE_USER"))
                .build();
        lenient().when(authService.getCurrentUser(userId.toString())).thenReturn(userResponse);
    }

    private void mockAuthServiceFailure(String principalName) {
        lenient().when(authService.getCurrentUser(principalName))
                .thenThrow(new RuntimeException("User not found"));
    }

    // ===== chat (POST /api/ai-assistant/chat) =====

    @Test
    void chat_success_returnsOk() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Help me build a flow");

        var response = ChatResponse.success(UUID.randomUUID(), "Sure, I can help with that!");
        when(aiAssistantService.chat(any(ChatStreamRequest.class), eq(userId))).thenReturn(response);

        var result = controller.chat(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getContent()).isEqualTo("Sure, I can help with that!");
        verify(aiAssistantService).chat(any(ChatStreamRequest.class), eq(userId));
    }

    @Test
    void chat_withConversationId_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversationId = UUID.randomUUID();
        var request = new ChatStreamRequest();
        request.setMessage("Continue our conversation");
        request.setConversationId(conversationId);

        var response = ChatResponse.success(conversationId, "Continuing...");
        when(aiAssistantService.chat(any(ChatStreamRequest.class), eq(userId))).thenReturn(response);

        var result = controller.chat(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getConversationId()).isEqualTo(conversationId);
    }

    @Test
    void chat_withFlowDefinition_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Add an email node");
        request.setFlowId("flow-123");

        var flowDef = new ChatStreamRequest.FlowDefinition();
        flowDef.setNodes(List.of(Map.of("id", "node1", "type", "trigger")));
        flowDef.setEdges(List.of());
        request.setFlowDefinition(flowDef);

        var response = ChatResponse.successWithFlow(
                UUID.randomUUID(),
                "Added email node",
                Map.of("nodes", List.of(), "edges", List.of()),
                List.of()
        );
        when(aiAssistantService.chat(any(ChatStreamRequest.class), eq(userId))).thenReturn(response);

        var result = controller.chat(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getFlowDefinition()).isNotNull();
    }

    @Test
    void chat_unauthenticated_throwsAccessDeniedException() {
        var request = new ChatStreamRequest();
        request.setMessage("Hello");

        assertThatThrownBy(() -> controller.chat(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void chat_authServiceFails_throwsAccessDeniedException() {
        var principalName = "bad-user";
        var principal = testPrincipal(principalName);
        mockAuthServiceFailure(principalName);

        var request = new ChatStreamRequest();
        request.setMessage("Hello");

        assertThatThrownBy(() -> controller.chat(request, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void chat_serviceReturnsError_returnsOkWithError() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Do something");

        var response = ChatResponse.error("AI service unavailable");
        when(aiAssistantService.chat(any(ChatStreamRequest.class), eq(userId))).thenReturn(response);

        var result = controller.chat(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("AI service unavailable");
    }

    @Test
    void chat_extractsUserIdFromPrincipal() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Test");

        var response = ChatResponse.success(UUID.randomUUID(), "OK");
        when(aiAssistantService.chat(any(ChatStreamRequest.class), eq(userId))).thenReturn(response);

        controller.chat(request, principal);

        verify(aiAssistantService).chat(any(ChatStreamRequest.class), eq(userId));
    }

    // ===== chatStream (POST /api/ai-assistant/chat/stream) =====

    @Test
    void chatStream_success_returnsFlux() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Stream a response");

        var chunk = ChatStreamChunk.text("Hello");
        when(aiAssistantService.chatStream(any(ChatStreamRequest.class), eq(userId)))
                .thenReturn(Flux.just(chunk));

        var result = controller.chatStream(request, principal);

        assertThat(result).isNotNull();
        var events = result.collectList().block();
        assertThat(events).isNotNull();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo(chunk);
        verify(aiAssistantService).chatStream(any(ChatStreamRequest.class), eq(userId));
    }

    @Test
    void chatStream_unauthenticated_throwsAccessDeniedException() {
        var request = new ChatStreamRequest();
        request.setMessage("Stream");

        assertThatThrownBy(() -> controller.chatStream(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void chatStream_emptyStream_returnsEmptyFlux() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Empty response");

        when(aiAssistantService.chatStream(any(ChatStreamRequest.class), eq(userId)))
                .thenReturn(Flux.empty());

        var result = controller.chatStream(request, principal);
        var events = result.collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isEmpty();
    }

    // ===== analyzeForPublish (POST /api/ai-assistant/analyze-for-publish) =====

    @Test
    void analyzeForPublish_success_returnsOk() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = AnalyzeForPublishRequest.builder()
                .definition(Map.of("nodes", List.of(), "edges", List.of()))
                .flowId("flow-id-1")
                .version("1.0")
                .build();

        var response = PublishAnalysisResponse.builder()
                .success(true)
                .summary(PublishAnalysisResponse.FlowSummary.builder()
                        .nodeCount(5)
                        .edgeCount(4)
                        .version("1.0")
                        .nodeTypes(List.of("trigger", "action"))
                        .hasUnconnectedNodes(false)
                        .hasCycles(false)
                        .build())
                .suggestions(List.of(
                        PublishAnalysisResponse.OptimizationSuggestion.builder()
                                .id("s1")
                                .type("parallel")
                                .title("Parallelize independent nodes")
                                .description("Nodes A and B can run in parallel")
                                .benefit("Reduce execution time by 40%")
                                .priority(1)
                                .affectedNodes(List.of("nodeA", "nodeB"))
                                .build()
                ))
                .analysisTimeMs(150L)
                .build();
        when(aiAssistantService.analyzeForPublish(any(AnalyzeForPublishRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.analyzeForPublish(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getSummary().getNodeCount()).isEqualTo(5);
        assertThat(result.getBody().getSuggestions()).hasSize(1);
        assertThat(result.getBody().getAnalysisTimeMs()).isEqualTo(150L);
    }

    @Test
    void analyzeForPublish_unauthenticated_throwsAccessDeniedException() {
        var request = AnalyzeForPublishRequest.builder()
                .definition(Map.of())
                .build();

        assertThatThrownBy(() -> controller.analyzeForPublish(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void analyzeForPublish_serviceReturnsError_returnsOkWithError() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = AnalyzeForPublishRequest.builder()
                .definition(Map.of("nodes", List.of()))
                .build();

        var response = PublishAnalysisResponse.error("Analysis failed");
        when(aiAssistantService.analyzeForPublish(any(AnalyzeForPublishRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.analyzeForPublish(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("Analysis failed");
    }

    @Test
    void analyzeForPublish_disabled_returnsEmptySuggestions() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = AnalyzeForPublishRequest.builder()
                .definition(Map.of("nodes", List.of()))
                .build();

        var response = PublishAnalysisResponse.disabled();
        when(aiAssistantService.analyzeForPublish(any(AnalyzeForPublishRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.analyzeForPublish(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getSuggestions()).isEmpty();
    }

    @Test
    void analyzeForPublish_extractsUserIdFromPrincipal() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = AnalyzeForPublishRequest.builder()
                .definition(Map.of("nodes", List.of()))
                .build();

        var response = PublishAnalysisResponse.disabled();
        when(aiAssistantService.analyzeForPublish(any(AnalyzeForPublishRequest.class), eq(userId)))
                .thenReturn(response);

        controller.analyzeForPublish(request, principal);

        verify(aiAssistantService).analyzeForPublish(any(AnalyzeForPublishRequest.class), eq(userId));
    }

    // ===== applySuggestions (POST /api/ai-assistant/apply-suggestions) =====

    @Test
    void applySuggestions_success_returnsOk() {
        var userId = testUserId();
        var flowId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = ApplySuggestionsRequest.builder()
                .flowId(flowId.toString())
                .suggestionIds(List.of("s1", "s2"))
                .definition(Map.of("nodes", List.of()))
                .build();

        when(flowShareService.hasEditAccess(eq(flowId), eq(userId))).thenReturn(true);

        var response = ApplySuggestionsResponse.success(
                2, List.of("s1", "s2"), Map.of("nodes", List.of(), "edges", List.of()));
        when(aiAssistantService.applySuggestions(any(ApplySuggestionsRequest.class))).thenReturn(response);

        var result = controller.applySuggestions(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getAppliedCount()).isEqualTo(2);
        assertThat(result.getBody().getAppliedSuggestions()).containsExactly("s1", "s2");
    }

    @Test
    void applySuggestions_noEditAccess_returnsForbidden() {
        var userId = testUserId();
        var flowId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = ApplySuggestionsRequest.builder()
                .flowId(flowId.toString())
                .suggestionIds(List.of("s1"))
                .build();

        when(flowShareService.hasEditAccess(eq(flowId), eq(userId))).thenReturn(false);

        var result = controller.applySuggestions(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNull();
        verify(aiAssistantService, never()).applySuggestions(any());
    }

    @Test
    void applySuggestions_nullFlowId_skipsPermissionCheck() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = ApplySuggestionsRequest.builder()
                .flowId(null)
                .suggestionIds(List.of("s1"))
                .build();

        var response = ApplySuggestionsResponse.success(1, List.of("s1"), Map.of());
        when(aiAssistantService.applySuggestions(any(ApplySuggestionsRequest.class))).thenReturn(response);

        var result = controller.applySuggestions(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(flowShareService, never()).hasEditAccess(any(), any());
    }

    @Test
    void applySuggestions_unauthenticated_throwsAccessDeniedException() {
        var request = ApplySuggestionsRequest.builder()
                .flowId(UUID.randomUUID().toString())
                .suggestionIds(List.of("s1"))
                .build();

        assertThatThrownBy(() -> controller.applySuggestions(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void applySuggestions_serviceReturnsError_returnsOkWithError() {
        var userId = testUserId();
        var flowId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = ApplySuggestionsRequest.builder()
                .flowId(flowId.toString())
                .suggestionIds(List.of("s1"))
                .build();

        when(flowShareService.hasEditAccess(eq(flowId), eq(userId))).thenReturn(true);

        var response = ApplySuggestionsResponse.error("Failed to apply suggestions");
        when(aiAssistantService.applySuggestions(any(ApplySuggestionsRequest.class))).thenReturn(response);

        var result = controller.applySuggestions(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("Failed to apply suggestions");
    }

    @Test
    void applySuggestions_extractsUserIdFromPrincipal() {
        var userId = testUserId();
        var flowId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = ApplySuggestionsRequest.builder()
                .flowId(flowId.toString())
                .suggestionIds(List.of("s1"))
                .build();

        when(flowShareService.hasEditAccess(eq(flowId), eq(userId))).thenReturn(true);
        var response = ApplySuggestionsResponse.success(1, List.of("s1"), Map.of());
        when(aiAssistantService.applySuggestions(any())).thenReturn(response);

        controller.applySuggestions(request, principal);

        verify(flowShareService).hasEditAccess(eq(flowId), eq(userId));
    }

    // ===== getNodeCategories (GET /api/ai-assistant/node-categories) =====

    @Test
    void getNodeCategories_success_returnsCategories() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var categories = List.of(
                NodeCategoryInfo.of("trigger", "Triggers", "play", 5, 10),
                NodeCategoryInfo.of("action", "Actions", "bolt", 12, 20),
                NodeCategoryInfo.of("messaging", "Messaging", "mail", 3, 8)
        );
        when(aiAssistantService.getNodeCategories(eq(userId))).thenReturn(categories);

        var result = controller.getNodeCategories(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(3);
        assertThat(result.getBody().get(0).getId()).isEqualTo("trigger");
        assertThat(result.getBody().get(1).getDisplayName()).isEqualTo("Actions");
        assertThat(result.getBody().get(2).getInstalledCount()).isEqualTo(3);
    }

    @Test
    void getNodeCategories_empty_returnsEmptyList() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(aiAssistantService.getNodeCategories(eq(userId))).thenReturn(List.of());

        var result = controller.getNodeCategories(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getNodeCategories_nullPrincipal_returnsOkWithNullUserId() {
        // getUserId returns null when principal is null, but service still receives null
        when(aiAssistantService.getNodeCategories(isNull())).thenReturn(List.of());

        var result = controller.getNodeCategories(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiAssistantService).getNodeCategories(isNull());
    }

    @Test
    void getNodeCategories_extractsUserIdFromPrincipal() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(aiAssistantService.getNodeCategories(eq(userId))).thenReturn(List.of());

        controller.getNodeCategories(principal);

        verify(aiAssistantService).getNodeCategories(eq(userId));
    }

    // ===== getInstalledNodes (GET /api/ai-assistant/installed-nodes) =====

    @Test
    void getInstalledNodes_noCategory_returnsAllNodes() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var nodes = List.of(
                InstalledNodeInfo.builder()
                        .nodeType("http_request")
                        .displayName("HTTP Request")
                        .description("Make HTTP requests")
                        .category("network")
                        .source("builtin")
                        .build(),
                InstalledNodeInfo.builder()
                        .nodeType("email_send")
                        .displayName("Send Email")
                        .description("Send email notifications")
                        .category("messaging")
                        .source("builtin")
                        .build()
        );
        when(aiAssistantService.getInstalledNodes(eq(userId), isNull())).thenReturn(nodes);

        var result = controller.getInstalledNodes(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getNodeType()).isEqualTo("http_request");
    }

    @Test
    void getInstalledNodes_withCategory_filtersNodes() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var nodes = List.of(
                InstalledNodeInfo.builder()
                        .nodeType("email_send")
                        .displayName("Send Email")
                        .category("messaging")
                        .source("builtin")
                        .build()
        );
        when(aiAssistantService.getInstalledNodes(eq(userId), eq("messaging"))).thenReturn(nodes);

        var result = controller.getInstalledNodes("messaging", principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getCategory()).isEqualTo("messaging");
    }

    @Test
    void getInstalledNodes_empty_returnsEmptyList() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(aiAssistantService.getInstalledNodes(eq(userId), eq("nonexistent"))).thenReturn(List.of());

        var result = controller.getInstalledNodes("nonexistent", principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getInstalledNodes_pluginSource_includesPluginId() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var nodes = List.of(
                InstalledNodeInfo.builder()
                        .nodeType("custom_plugin_node")
                        .displayName("Custom Node")
                        .category("custom")
                        .source("plugin")
                        .pluginId("plugin-123")
                        .build()
        );
        when(aiAssistantService.getInstalledNodes(eq(userId), isNull())).thenReturn(nodes);

        var result = controller.getInstalledNodes(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get(0).getSource()).isEqualTo("plugin");
        assertThat(result.getBody().get(0).getPluginId()).isEqualTo("plugin-123");
    }

    // ===== recommendNodes (POST /api/ai-assistant/recommend-nodes) =====

    @Test
    void recommendNodes_success_returnsRecommendations() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new NodeRecommendationRequest();
        request.setSearchQuery("send email notification");
        request.setCategory("messaging");

        var response = NodeRecommendationResponse.success(
                List.of(NodeCategoryInfo.of("messaging", "Messaging", "mail", 3, 5)),
                List.of(InstalledNodeInfo.builder()
                        .nodeType("email_send")
                        .displayName("Send Email")
                        .category("messaging")
                        .source("builtin")
                        .build()),
                List.of(NodeRecommendation.builder()
                        .nodeType("email_send")
                        .displayName("Send Email")
                        .matchReason("Matches your query for email notification")
                        .rating(0.95)
                        .build()),
                List.of()
        );
        when(aiAssistantService.recommendNodes(any(NodeRecommendationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.recommendNodes(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().isAiAvailable()).isTrue();
        assertThat(result.getBody().getAiRecommendations()).hasSize(1);
    }

    @Test
    void recommendNodes_aiUnavailable_returnsSuccessWithoutAi() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new NodeRecommendationRequest();
        request.setSearchQuery("test");

        var response = NodeRecommendationResponse.aiUnavailable(
                List.of(NodeCategoryInfo.of("trigger", "Triggers", "play", 5, 10)),
                List.of()
        );
        when(aiAssistantService.recommendNodes(any(NodeRecommendationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.recommendNodes(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().isAiAvailable()).isFalse();
        assertThat(result.getBody().getAiRecommendations()).isEmpty();
    }

    @Test
    void recommendNodes_unauthenticated_throwsAccessDeniedException() {
        var request = new NodeRecommendationRequest();
        request.setSearchQuery("test");

        assertThatThrownBy(() -> controller.recommendNodes(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void recommendNodes_error_returnsOkWithError() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new NodeRecommendationRequest();
        request.setSearchQuery("test");

        var response = NodeRecommendationResponse.error("Recommendation failed");
        when(aiAssistantService.recommendNodes(any(NodeRecommendationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.recommendNodes(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("Recommendation failed");
    }

    @Test
    void recommendNodes_withCurrentFlow_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new NodeRecommendationRequest();
        request.setSearchQuery("next step");
        request.setCurrentFlow(Map.of("nodes", List.of(Map.of("type", "trigger"))));

        var response = NodeRecommendationResponse.success(List.of(), List.of(), List.of(), List.of());
        when(aiAssistantService.recommendNodes(any(NodeRecommendationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.recommendNodes(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiAssistantService).recommendNodes(any(NodeRecommendationRequest.class), eq(userId));
    }

    // ===== generateFlow (POST /api/ai-assistant/generate-flow) =====

    @Test
    void generateFlow_success_returnsOk() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Create a flow that sends an email every 5 minutes");

        var response = GenerateFlowResponse.success(
                "Creating a scheduled email flow",
                Map.of("nodes", List.of(
                        Map.of("type", "cron_trigger"),
                        Map.of("type", "email_send")
                ), "edges", List.of()),
                List.of("cron_trigger", "email_send"),
                List.of()
        );
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().isAiAvailable()).isTrue();
        assertThat(result.getBody().getUnderstanding()).isEqualTo("Creating a scheduled email flow");
        assertThat(result.getBody().getFlowDefinition()).isNotNull();
        assertThat(result.getBody().getRequiredNodes()).containsExactly("cron_trigger", "email_send");
        assertThat(result.getBody().getMissingNodes()).isEmpty();
    }

    @Test
    void generateFlow_withMissingNodes_returnsMissingList() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Create a Slack bot");

        var response = GenerateFlowResponse.success(
                "Creating a Slack bot flow",
                Map.of("nodes", List.of(), "edges", List.of()),
                List.of("webhook_trigger", "slack_send"),
                List.of("slack_send")
        );
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMissingNodes()).containsExactly("slack_send");
    }

    @Test
    void generateFlow_aiUnavailable_returnsSuccessWithFlag() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        var response = GenerateFlowResponse.aiUnavailable();
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isAiAvailable()).isFalse();
    }

    @Test
    void generateFlow_unauthenticated_throwsAccessDeniedException() {
        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        assertThatThrownBy(() -> controller.generateFlow(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void generateFlow_error_returnsOkWithError() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        var response = GenerateFlowResponse.error("Generation failed");
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("Generation failed");
    }

    @Test
    void generateFlow_withLanguage_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");
        request.setLanguage("en");

        var response = GenerateFlowResponse.success("Building", Map.of(), List.of(), List.of());
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiAssistantService).generateFlow(any(GenerateFlowRequest.class), eq(userId));
    }

    @Test
    void generateFlow_withRequirementContext_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");
        var context = new GenerateFlowRequest.RequirementContext();
        context.setTriggerType("schedule");
        context.setTriggerDescription("Every 5 minutes");
        context.setOutputTarget("Email notification");
        request.setRequirementContext(context);

        var response = GenerateFlowResponse.success("Building", Map.of(), List.of(), List.of());
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void generateFlow_withExistingFlow_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Add error handling");
        request.setFeedback("Need retry logic");
        var existingFlow = new GenerateFlowRequest.ExistingFlowDefinition();
        existingFlow.setNodes(List.of(Map.of("type", "trigger")));
        existingFlow.setEdges(List.of());
        existingFlow.setUnderstanding("Original flow");
        request.setExistingFlow(existingFlow);

        var response = GenerateFlowResponse.success("Adding error handling", Map.of(), List.of(), List.of());
        when(aiAssistantService.generateFlow(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.generateFlow(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== generateFlowStream (POST /api/ai-assistant/generate-flow/stream) =====

    @Test
    void generateFlowStream_success_returnsFlux() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        var chunk = FlowGenerationChunk.thinking("Processing...");
        when(aiAssistantService.generateFlowStream(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(Flux.just(chunk));

        var result = controller.generateFlowStream(request, principal);

        assertThat(result).isNotNull();
        var events = result.collectList().block();
        assertThat(events).isNotNull();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo(chunk);
    }

    @Test
    void generateFlowStream_unauthenticated_throwsAccessDeniedException() {
        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        assertThatThrownBy(() -> controller.generateFlowStream(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void generateFlowStream_multipleChunks_returnsAll() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new GenerateFlowRequest();
        request.setUserInput("Build a flow");

        var chunk1 = FlowGenerationChunk.thinking("Step 1");
        var chunk2 = FlowGenerationChunk.progress(50, "Building");
        var chunk3 = FlowGenerationChunk.done(Map.of(), List.of());
        when(aiAssistantService.generateFlowStream(any(GenerateFlowRequest.class), eq(userId)))
                .thenReturn(Flux.just(chunk1, chunk2, chunk3));

        var result = controller.generateFlowStream(request, principal);
        var events = result.collectList().block();

        assertThat(events).hasSize(3);
    }

    // ===== getSimilarFlows (GET /api/ai-assistant/similar-flows) =====

    @Test
    void getSimilarFlows_success_returnsFlows() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var flows = List.of(
                SimilarFlow.builder()
                        .flowId(UUID.randomUUID())
                        .name("Email Notification Flow")
                        .description("Sends email notifications")
                        .similarity(0.85)
                        .nodeCount(3)
                        .nodeTypes(List.of("trigger", "email_send"))
                        .createdAt(Instant.now())
                        .matchedKeywords(List.of("email", "notification"))
                        .isTemplate(false)
                        .build()
        );
        when(similarFlowsService.findSimilarFlows(eq(userId), eq("email notification"), eq(5)))
                .thenReturn(flows);

        var result = controller.getSimilarFlows("email notification", 5, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getName()).isEqualTo("Email Notification Flow");
        assertThat(result.getBody().get(0).getSimilarity()).isEqualTo(0.85);
    }

    @Test
    void getSimilarFlows_empty_returnsEmptyList() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(similarFlowsService.findSimilarFlows(eq(userId), eq("nonexistent"), eq(5)))
                .thenReturn(List.of());

        var result = controller.getSimilarFlows("nonexistent", 5, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getSimilarFlows_nullPrincipal_returnsEmptyList() {
        var result = controller.getSimilarFlows("test", 5, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
        verify(similarFlowsService, never()).findSimilarFlows(any(), any(), anyInt());
    }

    @Test
    void getSimilarFlows_authFails_returnsEmptyList() {
        var principalName = "bad-user";
        var principal = testPrincipal(principalName);
        mockAuthServiceFailure(principalName);

        var result = controller.getSimilarFlows("test", 5, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getSimilarFlows_limitClampedToMax50() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(similarFlowsService.findSimilarFlows(eq(userId), eq("test"), eq(50)))
                .thenReturn(List.of());

        // limit > 50 should be clamped to 50
        controller.getSimilarFlows("test", 100, principal);

        verify(similarFlowsService).findSimilarFlows(eq(userId), eq("test"), eq(50));
    }

    @Test
    void getSimilarFlows_limitClampedToMin1() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(similarFlowsService.findSimilarFlows(eq(userId), eq("test"), eq(1)))
                .thenReturn(List.of());

        // limit < 1 should be clamped to 1
        controller.getSimilarFlows("test", 0, principal);

        verify(similarFlowsService).findSimilarFlows(eq(userId), eq("test"), eq(1));
    }

    @Test
    void getSimilarFlows_customLimit_usesProvidedLimit() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(similarFlowsService.findSimilarFlows(eq(userId), eq("test"), eq(10)))
                .thenReturn(List.of());

        controller.getSimilarFlows("test", 10, principal);

        verify(similarFlowsService).findSimilarFlows(eq(userId), eq("test"), eq(10));
    }

    // ===== clarifyRequirements (POST /api/ai-assistant/clarify-requirements) =====

    @Test
    void clarifyRequirements_question_returnsOk() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new RequirementClarificationRequest();
        request.setMessage("I want to monitor a website");

        var conversationId = UUID.randomUUID();
        var response = RequirementClarificationResponse.question(
                conversationId,
                "What URL do you want to monitor?",
                List.of("https://example.com", "Custom URL")
        );
        when(requirementClarificationService.clarify(any(RequirementClarificationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.clarifyRequirements(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().isRequirementComplete()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("What URL do you want to monitor?");
        assertThat(result.getBody().getSuggestedReplies()).containsExactly("https://example.com", "Custom URL");
    }

    @Test
    void clarifyRequirements_complete_returnsSummary() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new RequirementClarificationRequest();
        request.setMessage("Yes, send me an email if it goes down");
        request.setConversationId(UUID.randomUUID());

        var summary = RequirementClarificationResponse.RequirementSummary.builder()
                .triggerType("schedule")
                .triggerDescription("Every 5 minutes")
                .dataSource("HTTP health check")
                .processSteps(List.of("Check HTTP status", "Compare with threshold"))
                .outputTarget("Email notification")
                .errorHandling("Retry 3 times")
                .fullDescription("Monitor website health and send email alert")
                .build();

        var conversationId = UUID.randomUUID();
        var response = RequirementClarificationResponse.complete(
                conversationId,
                "Requirements are complete! Let me generate the flow.",
                summary
        );
        when(requirementClarificationService.clarify(any(RequirementClarificationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.clarifyRequirements(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().isRequirementComplete()).isTrue();
        assertThat(result.getBody().getSummary()).isNotNull();
        assertThat(result.getBody().getSummary().getTriggerType()).isEqualTo("schedule");
        assertThat(result.getBody().getSummary().getOutputTarget()).isEqualTo("Email notification");
    }

    @Test
    void clarifyRequirements_unauthenticated_throwsAccessDeniedException() {
        var request = new RequirementClarificationRequest();
        request.setMessage("Test");

        assertThatThrownBy(() -> controller.clarifyRequirements(request, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void clarifyRequirements_error_returnsOkWithError() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new RequirementClarificationRequest();
        request.setMessage("Test");

        var response = RequirementClarificationResponse.error("Clarification service unavailable");
        when(requirementClarificationService.clarify(any(RequirementClarificationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.clarifyRequirements(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getError()).isEqualTo("Clarification service unavailable");
    }

    @Test
    void clarifyRequirements_withHistory_passesThrough() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new RequirementClarificationRequest();
        request.setMessage("I want email notifications");
        request.setLanguage("zh-TW");

        var history = new ArrayList<RequirementClarificationRequest.ChatMessage>();
        var msg1 = new RequirementClarificationRequest.ChatMessage();
        msg1.setRole("user");
        msg1.setContent("I want to build a flow");
        history.add(msg1);

        var msg2 = new RequirementClarificationRequest.ChatMessage();
        msg2.setRole("assistant");
        msg2.setContent("What kind of flow?");
        history.add(msg2);

        request.setHistory(history);

        var response = RequirementClarificationResponse.question(
                UUID.randomUUID(), "What email service?", List.of("Gmail", "SMTP"));
        when(requirementClarificationService.clarify(any(RequirementClarificationRequest.class), eq(userId)))
                .thenReturn(response);

        var result = controller.clarifyRequirements(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(requirementClarificationService).clarify(any(RequirementClarificationRequest.class), eq(userId));
    }

    // ===== listConversations (GET /api/ai-assistant/conversations) =====

    @Test
    void listConversations_noFlowId_returnsAllConversations() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversations = List.of(
                Conversation.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .title("Conversation 1")
                        .messageCount(5)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                Conversation.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .title("Conversation 2")
                        .flowId(UUID.randomUUID())
                        .messageCount(3)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        when(conversationManager.getUserConversations(eq(userId))).thenReturn(conversations);

        var result = controller.listConversations(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getTitle()).isEqualTo("Conversation 1");
        assertThat(result.getBody().get(0).getMessageCount()).isEqualTo(5);
        assertThat(result.getBody().get(1).getTitle()).isEqualTo("Conversation 2");
        verify(conversationManager).getUserConversations(eq(userId));
    }

    @Test
    void listConversations_withFlowId_returnsFlowConversations() {
        var userId = testUserId();
        var flowId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversations = List.of(
                Conversation.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .title("Flow conversation")
                        .flowId(flowId)
                        .messageCount(10)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        when(conversationManager.getFlowConversations(eq(userId), eq(flowId))).thenReturn(conversations);

        var result = controller.listConversations(flowId.toString(), principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getFlowId()).isEqualTo(flowId);
        verify(conversationManager).getFlowConversations(eq(userId), eq(flowId));
    }

    @Test
    void listConversations_empty_returnsEmptyList() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(conversationManager.getUserConversations(eq(userId))).thenReturn(List.of());

        var result = controller.listConversations(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listConversations_nullPrincipal_returnsEmptyList() {
        var result = controller.listConversations(null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
        verify(conversationManager, never()).getUserConversations(any());
    }

    @Test
    void listConversations_authFails_returnsEmptyList() {
        var principalName = "bad-user";
        var principal = testPrincipal(principalName);
        mockAuthServiceFailure(principalName);

        var result = controller.listConversations(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listConversations_blankFlowId_returnsAllConversations() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(conversationManager.getUserConversations(eq(userId))).thenReturn(List.of());

        var result = controller.listConversations("   ", principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(conversationManager).getUserConversations(eq(userId));
        verify(conversationManager, never()).getFlowConversations(any(), any());
    }

    @Test
    void listConversations_conversationWithNullDates_handlesGracefully() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversations = List.of(
                Conversation.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .title("No dates")
                        .messageCount(0)
                        .createdAt(null)
                        .updatedAt(null)
                        .build()
        );
        when(conversationManager.getUserConversations(eq(userId))).thenReturn(conversations);

        var result = controller.listConversations(null, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getCreatedAt()).isNull();
        assertThat(result.getBody().get(0).getUpdatedAt()).isNull();
    }

    // ===== getConversation (GET /api/ai-assistant/conversations/{conversationId}) =====

    @Test
    void getConversation_found_returnsOk() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Test Conversation")
                .flowId(UUID.randomUUID())
                .messages(List.of(
                        Map.of("role", "user", "content", "Hello"),
                        Map.of("role", "assistant", "content", "Hi there!")
                ))
                .summary("A test conversation")
                .messageCount(2)
                .build();
        when(conversationManager.getConversation(eq(conversationId))).thenReturn(Optional.of(conversation));

        var result = controller.getConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("id")).isEqualTo(conversationId);
        assertThat(result.getBody().get("title")).isEqualTo("Test Conversation");
        assertThat(result.getBody().get("messageCount")).isEqualTo(2);
        assertThat(result.getBody().get("summary")).isEqualTo("A test conversation");
    }

    @Test
    void getConversation_notFound_returnsNotFound() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        when(conversationManager.getConversation(eq(conversationId))).thenReturn(Optional.empty());

        var result = controller.getConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getConversation_wrongUser_returnsNotFound() {
        var userId = testUserId();
        var otherUserId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversation = Conversation.builder()
                .id(conversationId)
                .userId(otherUserId) // Different user
                .title("Other user's conversation")
                .messageCount(1)
                .build();
        when(conversationManager.getConversation(eq(conversationId))).thenReturn(Optional.of(conversation));

        var result = controller.getConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getConversation_unauthenticated_throwsAccessDeniedException() {
        var conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getConversation(conversationId, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void getConversation_nullFields_handlesGracefully() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(null)
                .flowId(null)
                .messages(null)
                .summary(null)
                .messageCount(0)
                .build();
        when(conversationManager.getConversation(eq(conversationId))).thenReturn(Optional.of(conversation));

        var result = controller.getConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("title")).isEqualTo("");
        assertThat(result.getBody().get("flowId")).isEqualTo("");
        assertThat(result.getBody().get("messages")).isEqualTo(List.of());
        assertThat(result.getBody().get("summary")).isEqualTo("");
    }

    // ===== deleteConversation (DELETE /api/ai-assistant/conversations/{conversationId}) =====

    @Test
    void deleteConversation_success_returnsNoContent() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        doNothing().when(conversationManager).deleteConversation(eq(conversationId), eq(userId));

        var result = controller.deleteConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(conversationManager).deleteConversation(eq(conversationId), eq(userId));
    }

    @Test
    void deleteConversation_notFound_returnsNotFound() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        doThrow(new IllegalArgumentException("Conversation not found"))
                .when(conversationManager).deleteConversation(eq(conversationId), eq(userId));

        var result = controller.deleteConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteConversation_wrongUser_returnsNotFound() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        doThrow(new IllegalArgumentException("Conversation not found"))
                .when(conversationManager).deleteConversation(eq(conversationId), eq(userId));

        var result = controller.deleteConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteConversation_nullPrincipal_returnsUnauthorized() {
        var conversationId = UUID.randomUUID();

        var result = controller.deleteConversation(conversationId, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(conversationManager, never()).deleteConversation(any(), any());
    }

    @Test
    void deleteConversation_authFails_returnsUnauthorized() {
        var principalName = "bad-user";
        var principal = testPrincipal(principalName);
        mockAuthServiceFailure(principalName);

        var conversationId = UUID.randomUUID();

        var result = controller.deleteConversation(conversationId, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(conversationManager, never()).deleteConversation(any(), any());
    }

    @Test
    void deleteConversation_extractsUserIdFromPrincipal() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        doNothing().when(conversationManager).deleteConversation(eq(conversationId), eq(userId));

        controller.deleteConversation(conversationId, principal);

        verify(conversationManager).deleteConversation(eq(conversationId), eq(userId));
    }

    // ===== Cross-cutting: getUserId / requireUserId behavior =====

    @Test
    void getUserId_nullPrincipal_returnsNull() {
        // Endpoints using getUserId (not requireUserId) should handle null gracefully
        // getNodeCategories uses getUserId
        when(aiAssistantService.getNodeCategories(isNull())).thenReturn(List.of());

        var result = controller.getNodeCategories(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requireUserId_nullPrincipal_throwsAccessDeniedException() {
        // Endpoints using requireUserId should throw AccessDeniedException
        var request = new ChatStreamRequest();
        request.setMessage("Hello");

        assertThatThrownBy(() -> controller.chat(request, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireUserId_authServiceException_throwsAccessDeniedException() {
        var principalName = "unknown";
        var principal = testPrincipal(principalName);
        mockAuthServiceFailure(principalName);

        var request = new ChatStreamRequest();
        request.setMessage("Hello");

        assertThatThrownBy(() -> controller.chat(request, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    // ===== Response status code verification =====

    @Test
    void chat_returnsHttpStatus200() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        var request = new ChatStreamRequest();
        request.setMessage("Hello");

        when(aiAssistantService.chat(any(), eq(userId)))
                .thenReturn(ChatResponse.success(UUID.randomUUID(), "Hi"));

        var result = controller.chat(request, principal);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void deleteConversation_returnsHttpStatus204() {
        var userId = testUserId();
        var conversationId = UUID.randomUUID();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);

        doNothing().when(conversationManager).deleteConversation(any(), any());

        var result = controller.deleteConversation(conversationId, principal);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
    }

    // ===== All authenticated endpoints verify userId extraction =====

    @Test
    void allEndpoints_requireUserId_verifyUserIdExtraction() {
        var userId = testUserId();
        var principal = testPrincipal(userId.toString());
        mockAuthService(userId);
        var conversationId = UUID.randomUUID();

        // chat
        var chatRequest = new ChatStreamRequest();
        chatRequest.setMessage("Test");
        when(aiAssistantService.chat(any(), eq(userId)))
                .thenReturn(ChatResponse.success(UUID.randomUUID(), "OK"));
        controller.chat(chatRequest, principal);
        verify(aiAssistantService).chat(any(), eq(userId));

        // analyzeForPublish
        var analyzeRequest = AnalyzeForPublishRequest.builder()
                .definition(Map.of())
                .build();
        when(aiAssistantService.analyzeForPublish(any(), eq(userId)))
                .thenReturn(PublishAnalysisResponse.disabled());
        controller.analyzeForPublish(analyzeRequest, principal);
        verify(aiAssistantService).analyzeForPublish(any(), eq(userId));

        // recommendNodes
        var recRequest = new NodeRecommendationRequest();
        recRequest.setSearchQuery("test");
        when(aiAssistantService.recommendNodes(any(), eq(userId)))
                .thenReturn(NodeRecommendationResponse.error("test"));
        controller.recommendNodes(recRequest, principal);
        verify(aiAssistantService).recommendNodes(any(), eq(userId));

        // generateFlow
        var genRequest = new GenerateFlowRequest();
        genRequest.setUserInput("test");
        when(aiAssistantService.generateFlow(any(), eq(userId)))
                .thenReturn(GenerateFlowResponse.error("test"));
        controller.generateFlow(genRequest, principal);
        verify(aiAssistantService).generateFlow(any(), eq(userId));

        // clarifyRequirements
        var clarifyRequest = new RequirementClarificationRequest();
        clarifyRequest.setMessage("test");
        when(requirementClarificationService.clarify(any(), eq(userId)))
                .thenReturn(RequirementClarificationResponse.error("test"));
        controller.clarifyRequirements(clarifyRequest, principal);
        verify(requirementClarificationService).clarify(any(), eq(userId));

        // getConversation
        when(conversationManager.getConversation(eq(conversationId))).thenReturn(Optional.empty());
        controller.getConversation(conversationId, principal);
        verify(conversationManager).getConversation(eq(conversationId));
    }

    @Test
    void allEndpoints_getUserId_verifyNullPrincipalHandling() {
        // getNodeCategories - uses getUserId (null principal => null userId)
        when(aiAssistantService.getNodeCategories(isNull())).thenReturn(List.of());
        var catResult = controller.getNodeCategories(null);
        assertThat(catResult.getStatusCode()).isEqualTo(HttpStatus.OK);

        // getInstalledNodes - uses getUserId
        when(aiAssistantService.getInstalledNodes(isNull(), isNull())).thenReturn(List.of());
        var nodesResult = controller.getInstalledNodes(null, null);
        assertThat(nodesResult.getStatusCode()).isEqualTo(HttpStatus.OK);

        // listConversations - uses getUserId (null => empty list)
        var convResult = controller.listConversations(null, null);
        assertThat(convResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(convResult.getBody()).isEmpty();

        // getSimilarFlows - uses getUserId (null => empty list)
        var simResult = controller.getSimilarFlows("test", 5, null);
        assertThat(simResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(simResult.getBody()).isEmpty();

        // deleteConversation - uses getUserId (null => UNAUTHORIZED)
        var delResult = controller.deleteConversation(UUID.randomUUID(), null);
        assertThat(delResult.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
