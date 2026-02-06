package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.AgentConversation;
import com.aiinpocket.n3n.agent.entity.AgentMessage;
import com.aiinpocket.n3n.agent.service.AgentService;
import com.aiinpocket.n3n.agent.service.AgentService.AgentResponse;
import com.aiinpocket.n3n.agent.service.AgentService.StreamChunk;
import com.aiinpocket.n3n.agent.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * AI Agent API Controller
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Agents", description = "AI agent conversation and management")
public class AgentController {

    private final AgentService agentService;
    private final ConversationService conversationService;

    // ==================== Conversation Endpoints ====================

    /**
     * 建立新對話
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateConversationRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        AgentConversation conversation = conversationService.createConversation(
            userId, request.title());
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    /**
     * 取得對話列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationResponse>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        Page<AgentConversation> conversations = activeOnly
            ? conversationService.getActiveConversations(userId, pageable)
            : conversationService.getUserConversations(userId, pageable);

        return ResponseEntity.ok(conversations.map(ConversationResponse::from));
    }

    /**
     * 取得單一對話
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        AgentConversation conversation = conversationService.getConversation(userId, id);
        return ResponseEntity.ok(ConversationDetailResponse.from(conversation));
    }

    /**
     * 更新對話標題
     */
    @PatchMapping("/conversations/{id}")
    public ResponseEntity<ConversationResponse> updateConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        AgentConversation conversation = conversationService.updateTitle(userId, id, request.title());
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    /**
     * 結束對話
     */
    @PostMapping("/conversations/{id}/complete")
    public ResponseEntity<ConversationResponse> completeConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteConversationRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID flowId = request != null ? request.flowId() : null;
        AgentConversation conversation = conversationService.completeConversation(userId, id, flowId);
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    /**
     * 取消對話
     */
    @PostMapping("/conversations/{id}/cancel")
    public ResponseEntity<ConversationResponse> cancelConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        AgentConversation conversation = conversationService.cancelConversation(userId, id);
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    /**
     * 封存對話
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> archiveConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        conversationService.archiveConversation(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Message Endpoints ====================

    /**
     * 取得對話訊息
     */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<AgentMessage> messages = conversationService.getMessages(userId, id);
        return ResponseEntity.ok(messages.stream().map(MessageResponse::from).toList());
    }

    /**
     * 發送訊息（非串流）
     *
     * 注意：這裡使用同步方式處理以確保例外能被 @ExceptionHandler 正確捕捉。
     * CompletableFuture 的例外處理在 Spring Security 環境下有已知問題。
     */
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<AgentMessageResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());

        try {
            AgentResponse response = agentService.chat(userId, id, request.content()).join();
            return ResponseEntity.ok(AgentMessageResponse.from(response));
        } catch (CompletionException ex) {
            // 解包 CompletionException 並重新拋出內部例外
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * 發送訊息（SSE 串流）
     */
    @GetMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunk> streamMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam String message) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return agentService.chatStream(userId, id, message);
    }

    // ==================== DTOs ====================

    public record CreateConversationRequest(
        @Size(max = 255, message = "標題最多 255 字元")
        String title
    ) {}

    public record UpdateConversationRequest(
        @NotBlank(message = "標題不能為空")
        @Size(max = 255, message = "標題最多 255 字元")
        String title
    ) {}

    public record CompleteConversationRequest(UUID flowId) {}

    public record SendMessageRequest(
        @NotBlank(message = "訊息不能為空")
        @Size(max = 4000, message = "訊息最多 4000 字元")
        String content
    ) {}

    public record ConversationResponse(
        UUID id,
        String title,
        String status,
        UUID draftFlowId,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ConversationResponse from(AgentConversation c) {
            return new ConversationResponse(
                c.getId(),
                c.getTitle(),
                c.getStatus().name(),
                c.getDraftFlowId(),
                c.getCreatedAt(),
                c.getUpdatedAt()
            );
        }
    }

    public record ConversationDetailResponse(
        UUID id,
        String title,
        String status,
        UUID draftFlowId,
        List<MessageResponse> messages,
        int totalTokens,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ConversationDetailResponse from(AgentConversation c) {
            return new ConversationDetailResponse(
                c.getId(),
                c.getTitle(),
                c.getStatus().name(),
                c.getDraftFlowId(),
                c.getMessages().stream().map(MessageResponse::from).toList(),
                c.estimateTotalTokens(),
                c.getCreatedAt(),
                c.getUpdatedAt()
            );
        }
    }

    public record MessageResponse(
        UUID id,
        String role,
        String content,
        Map<String, Object> structuredData,
        Integer tokenCount,
        String modelId,
        Long latencyMs,
        Instant createdAt
    ) {
        public static MessageResponse from(AgentMessage m) {
            return new MessageResponse(
                m.getId(),
                m.getRole().name(),
                m.getContent(),
                m.getStructuredData(),
                m.getTokenCount(),
                m.getModelId(),
                m.getLatencyMs(),
                m.getCreatedAt()
            );
        }
    }

    public record AgentMessageResponse(
        UUID messageId,
        String content,
        Map<String, Object> structuredData,
        String model,
        int tokenCount,
        long latencyMs,
        boolean hasFlowDefinition,
        boolean hasComponentRecommendations
    ) {
        public static AgentMessageResponse from(AgentResponse r) {
            return new AgentMessageResponse(
                r.messageId(),
                r.content(),
                r.structuredData(),
                r.model(),
                r.tokenCount(),
                r.latencyMs(),
                r.hasFlowDefinition(),
                r.hasComponentRecommendations()
            );
        }
    }
}
