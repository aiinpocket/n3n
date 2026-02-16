package com.aiinpocket.n3n.common.config;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WebSocketSubscriptionInterceptorTest extends BaseServiceTest {

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private MessageChannel channel;

    private WebSocketSubscriptionInterceptor interceptor;

    private UUID userId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketSubscriptionInterceptor(executionRepository);
        userId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    // ─── SEND Blocking Tests ────────────────────────────────────

    @Test
    void shouldBlockClientSendToTopicExecution() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/executions/" + executionId);
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("clients cannot publish to broker topics");
    }

    @Test
    void shouldBlockClientSendToTopicUsers() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/users/" + userId + "/notifications");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("clients cannot publish to broker topics");
    }

    @Test
    void shouldBlockClientSendToQueue() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/queue/some-queue");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("clients cannot publish to broker topics");
    }

    @Test
    void shouldAllowClientSendToAppDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/some-action");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    // ─── SUBSCRIBE Validation Tests ─────────────────────────────

    @Test
    void shouldAllowSubscribeToOwnExecution() {
        when(executionRepository.existsByIdAndTriggeredBy(executionId, userId)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/executions/" + executionId);
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldBlockSubscribeToOtherUserExecution() {
        when(executionRepository.existsByIdAndTriggeredBy(executionId, userId)).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/executions/" + executionId);
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void shouldBlockSubscribeWithoutAuth() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/executions/" + executionId);
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void shouldAllowSubscribeToOwnUserTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/users/" + userId + "/notifications");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldBlockSubscribeToOtherUserTopic() {
        UUID otherUserId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/users/" + otherUserId + "/notifications");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void shouldBlockSubscribeWithInvalidExecutionId() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/executions/not-a-uuid");
        setAuthenticatedSession(accessor);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Invalid execution ID");
    }

    // ─── CONNECT Tests ──────────────────────────────────────────

    @Test
    void shouldSetPrincipalOnConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        setAuthenticatedSession(accessor);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void shouldNotSetPrincipalWhenNotAuthenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);
        assertThat(accessor.getUser()).isNull();
    }

    // ─── Helper ─────────────────────────────────────────────────

    private void setAuthenticatedSession(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("authenticated", true);
        sessionAttrs.put("userId", userId);
        accessor.setSessionAttributes(sessionAttrs);
    }
}
