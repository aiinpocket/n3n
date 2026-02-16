package com.aiinpocket.n3n.common.config;

import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket channel interceptor that:
 * 1. Sets user Principal on CONNECT (required for convertAndSendToUser)
 * 2. Validates SUBSCRIBE destinations to prevent unauthorized access
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final String EXECUTION_TOPIC_PREFIX = "/topic/executions/";
    private static final String USER_TOPIC_PREFIX = "/topic/users/";

    private final ExecutionRepository executionRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            setUserPrincipal(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null) {
                if (destination.startsWith(EXECUTION_TOPIC_PREFIX)) {
                    validateExecutionSubscription(accessor, destination);
                } else if (destination.startsWith(USER_TOPIC_PREFIX)) {
                    validateUserTopicSubscription(accessor, destination);
                }
            }
        }

        return message;
    }

    /**
     * Set the user Principal from handshake session attributes.
     * This is required for convertAndSendToUser to work.
     */
    private void setUserPrincipal(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && Boolean.TRUE.equals(sessionAttributes.get("authenticated"))) {
            UUID userId = (UUID) sessionAttributes.get("userId");
            if (userId != null) {
                accessor.setUser(new StompPrincipal(userId.toString()));
            }
        }
    }

    private void validateExecutionSubscription(StompHeaderAccessor accessor, String destination) {
        String executionIdStr = destination.substring(EXECUTION_TOPIC_PREFIX.length());

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null || !Boolean.TRUE.equals(sessionAttributes.get("authenticated"))) {
            throw new MessagingException("Unauthorized: not authenticated");
        }

        UUID userId = (UUID) sessionAttributes.get("userId");
        if (userId == null) {
            throw new MessagingException("Unauthorized: no user identity");
        }

        try {
            UUID executionId = UUID.fromString(executionIdStr);
            if (!executionRepository.existsByIdAndTriggeredBy(executionId, userId)) {
                log.warn("User {} attempted to subscribe to execution {} without access", userId, executionId);
                throw new MessagingException("Access denied: you do not own this execution");
            }
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Invalid execution ID format");
        }
    }

    /**
     * Validate that users can only subscribe to their own user-specific topics.
     * Prevents User A from subscribing to /topic/users/{userB}/... topics.
     */
    private void validateUserTopicSubscription(StompHeaderAccessor accessor, String destination) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null || !Boolean.TRUE.equals(sessionAttributes.get("authenticated"))) {
            throw new MessagingException("Unauthorized: not authenticated");
        }

        UUID userId = (UUID) sessionAttributes.get("userId");
        if (userId == null) {
            throw new MessagingException("Unauthorized: no user identity");
        }

        // Extract userId from topic path: /topic/users/{userId}/...
        String remainder = destination.substring(USER_TOPIC_PREFIX.length());
        int slashIdx = remainder.indexOf('/');
        String topicUserId = slashIdx > 0 ? remainder.substring(0, slashIdx) : remainder;

        if (!userId.toString().equals(topicUserId)) {
            log.warn("User {} attempted to subscribe to another user's topic: {}", userId, destination);
            throw new MessagingException("Access denied: cannot subscribe to another user's topic");
        }
    }

    /**
     * Simple Principal implementation for STOMP sessions.
     */
    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
