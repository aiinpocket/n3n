package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.AgentConversation;
import com.aiinpocket.n3n.agent.entity.AgentConversation.ConversationStatus;
import com.aiinpocket.n3n.agent.entity.AgentConversation.MessageRole;
import com.aiinpocket.n3n.agent.entity.AgentMessage;
import com.aiinpocket.n3n.agent.service.AgentService;
import com.aiinpocket.n3n.agent.service.AgentService.AgentResponse;
import com.aiinpocket.n3n.agent.service.AgentService.StreamChunk;
import com.aiinpocket.n3n.agent.service.ConversationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentService agentService;

    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private AgentController agentController;

    private final UUID userId = UUID.randomUUID();

    private UserDetails testUser() {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUserWithId(UUID id) {
        return User.withUsername(id.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private AgentConversation sampleConversation() {
        return AgentConversation.builder()
                .id(UUID.randomUUID())
                .title("Test Conversation")
                .status(ConversationStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private AgentConversation sampleConversation(UUID id, String title, ConversationStatus status) {
        return AgentConversation.builder()
                .id(id)
                .title(title)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private AgentConversation sampleConversationWithMessages(UUID id, String title) {
        AgentConversation conversation = AgentConversation.builder()
                .id(id)
                .title(title)
                .status(ConversationStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .messages(new ArrayList<>())
                .build();

        AgentMessage msg = AgentMessage.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .role(MessageRole.USER)
                .content("Hello")
                .tokenCount(10)
                .createdAt(Instant.now())
                .build();
        conversation.getMessages().add(msg);

        return conversation;
    }

    private AgentMessage sampleMessage(UUID conversationId) {
        AgentConversation conv = AgentConversation.builder().id(conversationId).build();
        return AgentMessage.builder()
                .id(UUID.randomUUID())
                .conversation(conv)
                .role(MessageRole.USER)
                .content("Test message")
                .tokenCount(5)
                .createdAt(Instant.now())
                .build();
    }

    private AgentMessage sampleAssistantMessage(UUID conversationId) {
        AgentConversation conv = AgentConversation.builder().id(conversationId).build();
        return AgentMessage.builder()
                .id(UUID.randomUUID())
                .conversation(conv)
                .role(MessageRole.ASSISTANT)
                .content("AI response")
                .structuredData(Map.of("understanding", "Test understanding"))
                .tokenCount(50)
                .modelId("claude-3")
                .latencyMs(500L)
                .createdAt(Instant.now())
                .build();
    }

    // ===== createConversation (POST /api/agent/conversations) =====

    @Test
    void createConversation_success_returnsOk() {
        var user = testUser();
        var request = new AgentController.CreateConversationRequest("New Chat");
        var conversation = sampleConversation();
        when(conversationService.createConversation(eq(userId), eq("New Chat"))).thenReturn(conversation);

        var result = agentController.createConversation(user, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(conversation.getId());
        assertThat(result.getBody().title()).isEqualTo("Test Conversation");
        assertThat(result.getBody().status()).isEqualTo("ACTIVE");
        verify(conversationService).createConversation(userId, "New Chat");
    }

    @Test
    void createConversation_withNullTitle_delegatesToService() {
        var user = testUser();
        var request = new AgentController.CreateConversationRequest(null);
        var conversation = sampleConversation();
        when(conversationService.createConversation(eq(userId), isNull())).thenReturn(conversation);

        var result = agentController.createConversation(user, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(conversationService).createConversation(userId, null);
    }

    @Test
    void createConversation_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var request = new AgentController.CreateConversationRequest("Chat");
        var conversation = sampleConversation();
        when(conversationService.createConversation(eq(specificUserId), anyString())).thenReturn(conversation);

        agentController.createConversation(user, request);

        verify(conversationService).createConversation(eq(specificUserId), eq("Chat"));
    }

    @Test
    void createConversation_serviceThrowsException_propagates() {
        var user = testUser();
        var request = new AgentController.CreateConversationRequest("Chat");
        when(conversationService.createConversation(eq(userId), anyString()))
                .thenThrow(new EntityNotFoundException("User not found"));

        assertThatThrownBy(() -> agentController.createConversation(user, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void createConversation_responseContainsAllFields() {
        var user = testUser();
        var request = new AgentController.CreateConversationRequest("My Conversation");
        var convId = UUID.randomUUID();
        var draftFlowId = UUID.randomUUID();
        var now = Instant.now();
        var conversation = AgentConversation.builder()
                .id(convId)
                .title("My Conversation")
                .status(ConversationStatus.COMPLETED)
                .draftFlowId(draftFlowId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(conversationService.createConversation(eq(userId), anyString())).thenReturn(conversation);

        var result = agentController.createConversation(user, request);

        assertThat(result.getBody().id()).isEqualTo(convId);
        assertThat(result.getBody().title()).isEqualTo("My Conversation");
        assertThat(result.getBody().status()).isEqualTo("COMPLETED");
        assertThat(result.getBody().draftFlowId()).isEqualTo(draftFlowId);
        assertThat(result.getBody().createdAt()).isEqualTo(now);
        assertThat(result.getBody().updatedAt()).isEqualTo(now);
    }

    // ===== getConversations (GET /api/agent/conversations) =====

    @Test
    void getConversations_allConversations_returnsPage() {
        var user = testUser();
        var pageable = PageRequest.of(0, 20);
        var conversation = sampleConversation();
        Page<AgentConversation> page = new PageImpl<>(List.of(conversation), pageable, 1);
        when(conversationService.getUserConversations(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = agentController.getConversations(user, false, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(conversationService).getUserConversations(userId, pageable);
        verify(conversationService, never()).getActiveConversations(any(), any());
    }

    @Test
    void getConversations_activeOnly_callsActiveConversations() {
        var user = testUser();
        var pageable = PageRequest.of(0, 20);
        Page<AgentConversation> page = new PageImpl<>(List.of(), pageable, 0);
        when(conversationService.getActiveConversations(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = agentController.getConversations(user, true, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(conversationService).getActiveConversations(userId, pageable);
        verify(conversationService, never()).getUserConversations(any(), any());
    }

    @Test
    void getConversations_emptyPage_returnsOk() {
        var user = testUser();
        var pageable = PageRequest.of(0, 20);
        when(conversationService.getUserConversations(eq(userId), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));

        var result = agentController.getConversations(user, false, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
        assertThat(result.getBody().getContent()).isEmpty();
    }

    @Test
    void getConversations_multipleConversations_returnsAll() {
        var user = testUser();
        var pageable = PageRequest.of(0, 20);
        var conv1 = sampleConversation(UUID.randomUUID(), "Chat 1", ConversationStatus.ACTIVE);
        var conv2 = sampleConversation(UUID.randomUUID(), "Chat 2", ConversationStatus.COMPLETED);
        var conv3 = sampleConversation(UUID.randomUUID(), "Chat 3", ConversationStatus.CANCELLED);
        Page<AgentConversation> page = new PageImpl<>(List.of(conv1, conv2, conv3), pageable, 3);
        when(conversationService.getUserConversations(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = agentController.getConversations(user, false, pageable);

        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent()).hasSize(3);
    }

    @Test
    void getConversations_withPagination_respectsPageParameters() {
        var user = testUser();
        var pageable = PageRequest.of(2, 10);
        Page<AgentConversation> page = new PageImpl<>(List.of(sampleConversation()), pageable, 21);
        when(conversationService.getUserConversations(eq(userId), eq(pageable))).thenReturn(page);

        var result = agentController.getConversations(user, false, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(21);
        assertThat(result.getBody().getNumber()).isEqualTo(2);
        assertThat(result.getBody().getSize()).isEqualTo(10);
    }

    // ===== getConversation (GET /api/agent/conversations/{id}) =====

    @Test
    void getConversation_found_returnsDetailResponse() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var conversation = sampleConversationWithMessages(convId, "Detail Chat");
        when(conversationService.getConversation(eq(userId), eq(convId))).thenReturn(conversation);

        var result = agentController.getConversation(user, convId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().id()).isEqualTo(convId);
        assertThat(result.getBody().title()).isEqualTo("Detail Chat");
        assertThat(result.getBody().messages()).isNotEmpty();
        verify(conversationService).getConversation(userId, convId);
    }

    @Test
    void getConversation_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        when(conversationService.getConversation(eq(userId), eq(convId)))
                .thenThrow(new EntityNotFoundException("Conversation not found: " + convId));

        assertThatThrownBy(() -> agentController.getConversation(user, convId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void getConversation_responseContainsTotalTokens() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var conversation = sampleConversationWithMessages(convId, "Token Chat");
        when(conversationService.getConversation(eq(userId), eq(convId))).thenReturn(conversation);

        var result = agentController.getConversation(user, convId);

        assertThat(result.getBody().totalTokens()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void getConversation_responseContainsMessageDetails() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var conversation = sampleConversationWithMessages(convId, "Msg Chat");
        when(conversationService.getConversation(eq(userId), eq(convId))).thenReturn(conversation);

        var result = agentController.getConversation(user, convId);

        var messages = result.getBody().messages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo("USER");
        assertThat(messages.get(0).content()).isEqualTo("Hello");
    }

    // ===== updateConversation (PATCH /api/agent/conversations/{id}) =====

    @Test
    void updateConversation_success_returnsOk() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.UpdateConversationRequest("Updated Title");
        var conversation = sampleConversation(convId, "Updated Title", ConversationStatus.ACTIVE);
        when(conversationService.updateTitle(eq(userId), eq(convId), eq("Updated Title")))
                .thenReturn(conversation);

        var result = agentController.updateConversation(user, convId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().title()).isEqualTo("Updated Title");
        verify(conversationService).updateTitle(userId, convId, "Updated Title");
    }

    @Test
    void updateConversation_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.UpdateConversationRequest("New Title");
        when(conversationService.updateTitle(eq(userId), eq(convId), anyString()))
                .thenThrow(new EntityNotFoundException("Conversation not found"));

        assertThatThrownBy(() -> agentController.updateConversation(user, convId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void updateConversation_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var convId = UUID.randomUUID();
        var request = new AgentController.UpdateConversationRequest("Title");
        var conversation = sampleConversation();
        when(conversationService.updateTitle(eq(specificUserId), eq(convId), anyString()))
                .thenReturn(conversation);

        agentController.updateConversation(user, convId, request);

        verify(conversationService).updateTitle(eq(specificUserId), eq(convId), eq("Title"));
    }

    // ===== completeConversation (POST /api/agent/conversations/{id}/complete) =====

    @Test
    void completeConversation_withFlowId_returnsOk() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var flowId = UUID.randomUUID();
        var request = new AgentController.CompleteConversationRequest(flowId);
        var conversation = sampleConversation(convId, "Completed", ConversationStatus.COMPLETED);
        conversation.setDraftFlowId(flowId);
        when(conversationService.completeConversation(eq(userId), eq(convId), eq(flowId)))
                .thenReturn(conversation);

        var result = agentController.completeConversation(user, convId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo("COMPLETED");
        assertThat(result.getBody().draftFlowId()).isEqualTo(flowId);
        verify(conversationService).completeConversation(userId, convId, flowId);
    }

    @Test
    void completeConversation_withNullRequest_passesNullFlowId() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var conversation = sampleConversation(convId, "Completed", ConversationStatus.COMPLETED);
        when(conversationService.completeConversation(eq(userId), eq(convId), isNull()))
                .thenReturn(conversation);

        var result = agentController.completeConversation(user, convId, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(conversationService).completeConversation(userId, convId, null);
    }

    @Test
    void completeConversation_withNullFlowIdInRequest_passesNull() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.CompleteConversationRequest(null);
        var conversation = sampleConversation(convId, "Completed", ConversationStatus.COMPLETED);
        when(conversationService.completeConversation(eq(userId), eq(convId), isNull()))
                .thenReturn(conversation);

        var result = agentController.completeConversation(user, convId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(conversationService).completeConversation(userId, convId, null);
    }

    @Test
    void completeConversation_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.CompleteConversationRequest(null);
        when(conversationService.completeConversation(eq(userId), eq(convId), any()))
                .thenThrow(new EntityNotFoundException("Conversation not found"));

        assertThatThrownBy(() -> agentController.completeConversation(user, convId, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ===== cancelConversation (POST /api/agent/conversations/{id}/cancel) =====

    @Test
    void cancelConversation_success_returnsOk() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var conversation = sampleConversation(convId, "Cancelled", ConversationStatus.CANCELLED);
        when(conversationService.cancelConversation(eq(userId), eq(convId))).thenReturn(conversation);

        var result = agentController.cancelConversation(user, convId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().status()).isEqualTo("CANCELLED");
        verify(conversationService).cancelConversation(userId, convId);
    }

    @Test
    void cancelConversation_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        when(conversationService.cancelConversation(eq(userId), eq(convId)))
                .thenThrow(new EntityNotFoundException("Conversation not found"));

        assertThatThrownBy(() -> agentController.cancelConversation(user, convId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cancelConversation_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var convId = UUID.randomUUID();
        var conversation = sampleConversation();
        when(conversationService.cancelConversation(eq(specificUserId), eq(convId)))
                .thenReturn(conversation);

        agentController.cancelConversation(user, convId);

        verify(conversationService).cancelConversation(eq(specificUserId), eq(convId));
    }

    // ===== archiveConversation (DELETE /api/agent/conversations/{id}) =====

    @Test
    void archiveConversation_success_returnsNoContent() {
        var user = testUser();
        var convId = UUID.randomUUID();
        doNothing().when(conversationService).archiveConversation(eq(userId), eq(convId));

        var result = agentController.archiveConversation(user, convId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(conversationService).archiveConversation(userId, convId);
    }

    @Test
    void archiveConversation_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Conversation not found"))
                .when(conversationService).archiveConversation(eq(userId), eq(convId));

        assertThatThrownBy(() -> agentController.archiveConversation(user, convId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void archiveConversation_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var convId = UUID.randomUUID();
        doNothing().when(conversationService).archiveConversation(eq(specificUserId), eq(convId));

        agentController.archiveConversation(user, convId);

        verify(conversationService).archiveConversation(eq(specificUserId), eq(convId));
    }

    // ===== getMessages (GET /api/agent/conversations/{id}/messages) =====

    @Test
    void getMessages_success_returnsList() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var msg1 = sampleMessage(convId);
        var msg2 = sampleAssistantMessage(convId);
        when(conversationService.getMessages(eq(userId), eq(convId))).thenReturn(List.of(msg1, msg2));

        var result = agentController.getMessages(user, convId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).role()).isEqualTo("USER");
        assertThat(result.getBody().get(1).role()).isEqualTo("ASSISTANT");
        verify(conversationService).getMessages(userId, convId);
    }

    @Test
    void getMessages_emptyList_returnsOk() {
        var user = testUser();
        var convId = UUID.randomUUID();
        when(conversationService.getMessages(eq(userId), eq(convId))).thenReturn(List.of());

        var result = agentController.getMessages(user, convId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void getMessages_notFound_throwsException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        when(conversationService.getMessages(eq(userId), eq(convId)))
                .thenThrow(new EntityNotFoundException("Conversation not found"));

        assertThatThrownBy(() -> agentController.getMessages(user, convId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getMessages_messageResponseContainsAllFields() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var assistantMsg = sampleAssistantMessage(convId);
        when(conversationService.getMessages(eq(userId), eq(convId))).thenReturn(List.of(assistantMsg));

        var result = agentController.getMessages(user, convId);

        var msgResponse = result.getBody().get(0);
        assertThat(msgResponse.id()).isEqualTo(assistantMsg.getId());
        assertThat(msgResponse.role()).isEqualTo("ASSISTANT");
        assertThat(msgResponse.content()).isEqualTo("AI response");
        assertThat(msgResponse.structuredData()).isNotNull();
        assertThat(msgResponse.tokenCount()).isEqualTo(50);
        assertThat(msgResponse.modelId()).isEqualTo("claude-3");
        assertThat(msgResponse.latencyMs()).isEqualTo(500L);
        assertThat(msgResponse.createdAt()).isNotNull();
    }

    // ===== sendMessage (POST /api/agent/conversations/{id}/messages) =====

    @Test
    void sendMessage_success_returnsAgentMessageResponse() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Hello AI");
        var msgId = UUID.randomUUID();
        var agentResponse = AgentResponse.builder()
                .messageId(msgId)
                .content("Hello! How can I help?")
                .structuredData(null)
                .model("claude-3")
                .tokenCount(20)
                .latencyMs(300)
                .build();
        when(agentService.chat(eq(userId), eq(convId), eq("Hello AI")))
                .thenReturn(CompletableFuture.completedFuture(agentResponse));

        var result = agentController.sendMessage(user, convId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().messageId()).isEqualTo(msgId);
        assertThat(result.getBody().content()).isEqualTo("Hello! How can I help?");
        assertThat(result.getBody().model()).isEqualTo("claude-3");
        assertThat(result.getBody().tokenCount()).isEqualTo(20);
        assertThat(result.getBody().latencyMs()).isEqualTo(300);
        verify(agentService).chat(userId, convId, "Hello AI");
    }

    @Test
    void sendMessage_withFlowDefinition_hasFlowDefinitionTrue() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Create a flow");
        var agentResponse = AgentResponse.builder()
                .messageId(UUID.randomUUID())
                .content("Here's a flow")
                .structuredData(Map.of("flowDefinition", Map.of("nodes", List.of())))
                .model("claude-3")
                .tokenCount(100)
                .latencyMs(500)
                .build();
        when(agentService.chat(eq(userId), eq(convId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(agentResponse));

        var result = agentController.sendMessage(user, convId, request);

        assertThat(result.getBody().hasFlowDefinition()).isTrue();
        assertThat(result.getBody().hasComponentRecommendations()).isFalse();
    }

    @Test
    void sendMessage_withComponentRecommendations_hasRecommendationsTrue() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Recommend components");
        var agentResponse = AgentResponse.builder()
                .messageId(UUID.randomUUID())
                .content("Here are recommendations")
                .structuredData(Map.of("existingComponents", List.of()))
                .model("claude-3")
                .tokenCount(80)
                .latencyMs(400)
                .build();
        when(agentService.chat(eq(userId), eq(convId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(agentResponse));

        var result = agentController.sendMessage(user, convId, request);

        assertThat(result.getBody().hasComponentRecommendations()).isTrue();
    }

    @Test
    void sendMessage_completionExceptionWithRuntimeCause_unwrapsAndThrows() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Hello");
        var runtimeEx = new IllegalStateException("AI provider not configured");
        var future = new CompletableFuture<AgentResponse>();
        future.completeExceptionally(runtimeEx);
        when(agentService.chat(eq(userId), eq(convId), anyString())).thenReturn(future);

        assertThatThrownBy(() -> agentController.sendMessage(user, convId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI provider not configured");
    }

    @Test
    void sendMessage_completionExceptionWithCheckedCause_wrapsInRuntimeException() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Hello");
        var checkedException = new Exception("Checked exception");
        var future = new CompletableFuture<AgentResponse>();
        future.completeExceptionally(checkedException);
        when(agentService.chat(eq(userId), eq(convId), anyString())).thenReturn(future);

        assertThatThrownBy(() -> agentController.sendMessage(user, convId, request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sendMessage_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var convId = UUID.randomUUID();
        var request = new AgentController.SendMessageRequest("Test");
        var agentResponse = AgentResponse.builder()
                .messageId(UUID.randomUUID())
                .content("Response")
                .model("model")
                .tokenCount(10)
                .latencyMs(100)
                .build();
        when(agentService.chat(eq(specificUserId), eq(convId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(agentResponse));

        agentController.sendMessage(user, convId, request);

        verify(agentService).chat(eq(specificUserId), eq(convId), eq("Test"));
    }

    // ===== streamMessage (GET /api/agent/conversations/{id}/stream) =====

    @Test
    void streamMessage_success_returnsFlux() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var chunks = Flux.just(
                new StreamChunk("Hello", false),
                new StreamChunk(" world", false),
                new StreamChunk(null, true)
        );
        when(agentService.chatStream(eq(userId), eq(convId), eq("Test message"))).thenReturn(chunks);

        var result = agentController.streamMessage(user, convId, "Test message");

        assertThat(result).isNotNull();
        var chunkList = result.collectList().block();
        assertThat(chunkList).hasSize(3);
        assertThat(chunkList.get(0).delta()).isEqualTo("Hello");
        assertThat(chunkList.get(0).done()).isFalse();
        assertThat(chunkList.get(2).done()).isTrue();
        verify(agentService).chatStream(userId, convId, "Test message");
    }

    @Test
    void streamMessage_extractsUserIdFromUserDetails() {
        var specificUserId = UUID.randomUUID();
        var user = testUserWithId(specificUserId);
        var convId = UUID.randomUUID();
        when(agentService.chatStream(eq(specificUserId), eq(convId), anyString()))
                .thenReturn(Flux.empty());

        agentController.streamMessage(user, convId, "Hello");

        verify(agentService).chatStream(eq(specificUserId), eq(convId), eq("Hello"));
    }

    // ===== DTO Conversion Tests =====

    @Test
    void conversationResponse_from_mapsAllFieldsCorrectly() {
        var conversation = AgentConversation.builder()
                .id(UUID.randomUUID())
                .title("Test")
                .status(ConversationStatus.ACTIVE)
                .draftFlowId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var response = AgentController.ConversationResponse.from(conversation);

        assertThat(response.id()).isEqualTo(conversation.getId());
        assertThat(response.title()).isEqualTo(conversation.getTitle());
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.draftFlowId()).isEqualTo(conversation.getDraftFlowId());
        assertThat(response.createdAt()).isEqualTo(conversation.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(conversation.getUpdatedAt());
    }

    @Test
    void messageResponse_from_mapsAllFieldsCorrectly() {
        var convId = UUID.randomUUID();
        var msg = sampleAssistantMessage(convId);

        var response = AgentController.MessageResponse.from(msg);

        assertThat(response.id()).isEqualTo(msg.getId());
        assertThat(response.role()).isEqualTo("ASSISTANT");
        assertThat(response.content()).isEqualTo(msg.getContent());
        assertThat(response.structuredData()).isEqualTo(msg.getStructuredData());
        assertThat(response.tokenCount()).isEqualTo(msg.getTokenCount());
        assertThat(response.modelId()).isEqualTo(msg.getModelId());
        assertThat(response.latencyMs()).isEqualTo(msg.getLatencyMs());
        assertThat(response.createdAt()).isEqualTo(msg.getCreatedAt());
    }

    @Test
    void agentMessageResponse_from_mapsAllFieldsCorrectly() {
        var agentResponse = AgentResponse.builder()
                .messageId(UUID.randomUUID())
                .content("Hello")
                .structuredData(Map.of("key", "value"))
                .model("test-model")
                .tokenCount(42)
                .latencyMs(123)
                .build();

        var response = AgentController.AgentMessageResponse.from(agentResponse);

        assertThat(response.messageId()).isEqualTo(agentResponse.messageId());
        assertThat(response.content()).isEqualTo("Hello");
        assertThat(response.structuredData()).containsEntry("key", "value");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.tokenCount()).isEqualTo(42);
        assertThat(response.latencyMs()).isEqualTo(123);
    }

    // ===== Cross-cutting concerns =====

    @Test
    void allConversationEndpoints_returnResponseEntity() {
        var user = testUser();
        var convId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);

        // Setup mocks
        when(conversationService.createConversation(eq(userId), anyString()))
                .thenReturn(sampleConversation());
        when(conversationService.getUserConversations(eq(userId), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));
        when(conversationService.getConversation(eq(userId), eq(convId)))
                .thenReturn(sampleConversationWithMessages(convId, "Test"));
        when(conversationService.updateTitle(eq(userId), eq(convId), anyString()))
                .thenReturn(sampleConversation());
        when(conversationService.completeConversation(eq(userId), eq(convId), any()))
                .thenReturn(sampleConversation());
        when(conversationService.cancelConversation(eq(userId), eq(convId)))
                .thenReturn(sampleConversation());
        doNothing().when(conversationService).archiveConversation(eq(userId), eq(convId));
        when(conversationService.getMessages(eq(userId), eq(convId)))
                .thenReturn(List.of());

        // Verify each endpoint returns ResponseEntity
        assertThat(agentController.createConversation(user,
                new AgentController.CreateConversationRequest("Chat")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.getConversations(user, false, pageable).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.getConversation(user, convId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.updateConversation(user, convId,
                new AgentController.UpdateConversationRequest("Title")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.completeConversation(user, convId, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.cancelConversation(user, convId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(agentController.archiveConversation(user, convId).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(agentController.getMessages(user, convId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void conversationService_throwsRuntimeException_propagates() {
        var user = testUser();
        var pageable = PageRequest.of(0, 20);
        when(conversationService.getUserConversations(eq(userId), any(Pageable.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> agentController.getConversations(user, false, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");
    }
}
