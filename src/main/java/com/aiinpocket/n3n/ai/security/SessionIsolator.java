package com.aiinpocket.n3n.ai.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * AI 對話 Session 隔離器
 *
 * 確保不同使用者的 AI 對話完全隔離，防止跨用戶資料洩漏
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionIsolator {

    private final StringRedisTemplate redisTemplate;

    private static final String SESSION_KEY_PREFIX = "ai:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "ai:user-sessions:";
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(24);
    private static final int MAX_SESSIONS_PER_USER = 10;

    /**
     * 建立新的 AI 對話 session
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @return Session context
     */
    public SessionContext createSession(UUID userId, UUID conversationId) {
        String sessionKey = buildSessionKey(conversationId);
        String userSessionsKey = buildUserSessionsKey(userId);

        // 檢查使用者是否已達最大 session 數
        Long sessionCount = redisTemplate.opsForSet().size(userSessionsKey);
        if (sessionCount != null && sessionCount >= MAX_SESSIONS_PER_USER) {
            // 移除最舊的 session
            cleanupOldestSession(userId);
        }

        // 建立 session 記錄
        String sessionData = buildSessionData(userId, conversationId);
        redisTemplate.opsForValue().set(sessionKey, sessionData, DEFAULT_SESSION_TTL);

        // 將 session 加入使用者的 session 清單
        redisTemplate.opsForSet().add(userSessionsKey, conversationId.toString());
        redisTemplate.expire(userSessionsKey, DEFAULT_SESSION_TTL);

        log.info("Created AI session for user {} conversation {}", userId, conversationId);

        return new SessionContext(userId, conversationId, Instant.now());
    }

    /**
     * 驗證使用者對 session 的存取權限
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @return true 如果使用者有權存取
     * @throws SessionAccessDeniedException 如果無權存取
     */
    public boolean validateAccess(UUID userId, UUID conversationId) {
        String sessionKey = buildSessionKey(conversationId);
        String sessionData = redisTemplate.opsForValue().get(sessionKey);

        if (sessionData == null) {
            log.warn("Session not found for conversation {}", conversationId);
            throw new SessionAccessDeniedException("Session 不存在或已過期");
        }

        UUID sessionOwnerId = extractUserId(sessionData);
        if (!userId.equals(sessionOwnerId)) {
            log.warn("User {} attempted to access session owned by {}",
                userId, sessionOwnerId);
            throw new SessionAccessDeniedException("無權存取此對話");
        }

        // 延長 session 存活時間
        redisTemplate.expire(sessionKey, DEFAULT_SESSION_TTL);

        return true;
    }

    /**
     * 取得 session 上下文
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @return Session context
     */
    public SessionContext getSession(UUID userId, UUID conversationId) {
        validateAccess(userId, conversationId);

        String sessionKey = buildSessionKey(conversationId);
        String sessionData = redisTemplate.opsForValue().get(sessionKey);

        if (sessionData == null) {
            throw new SessionAccessDeniedException("Session 不存在或已過期");
        }

        Instant createdAt = extractCreatedAt(sessionData);
        return new SessionContext(userId, conversationId, createdAt);
    }

    /**
     * 取得使用者所有活躍的 session ID
     *
     * @param userId 使用者 ID
     * @return Session ID 清單
     */
    public Set<String> getUserSessions(UUID userId) {
        String userSessionsKey = buildUserSessionsKey(userId);
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);
        return sessions != null ? sessions : Set.of();
    }

    /**
     * 終止 session
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     */
    public void terminateSession(UUID userId, UUID conversationId) {
        validateAccess(userId, conversationId);

        String sessionKey = buildSessionKey(conversationId);
        String userSessionsKey = buildUserSessionsKey(userId);

        redisTemplate.delete(sessionKey);
        redisTemplate.opsForSet().remove(userSessionsKey, conversationId.toString());

        log.info("Terminated AI session for user {} conversation {}", userId, conversationId);
    }

    /**
     * 終止使用者所有 sessions
     *
     * @param userId 使用者 ID
     */
    public void terminateAllSessions(UUID userId) {
        String userSessionsKey = buildUserSessionsKey(userId);
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        if (sessions != null && !sessions.isEmpty()) {
            for (String sessionId : sessions) {
                String sessionKey = buildSessionKey(UUID.fromString(sessionId));
                redisTemplate.delete(sessionKey);
            }
            redisTemplate.delete(userSessionsKey);

            log.info("Terminated all {} AI sessions for user {}", sessions.size(), userId);
        }
    }

    /**
     * 清理過期的 sessions（由排程呼叫）
     */
    public void cleanupExpiredSessions() {
        // Redis TTL 會自動清理過期的 session keys
        // 這裡主要處理 user-sessions set 中可能殘留的失效 session ID

        log.debug("Cleanup expired sessions triggered");

        // 實作上可透過 SCAN 遍歷 user-sessions:* keys
        // 但這裡依賴 Redis TTL 自動清理機制
    }

    /**
     * 為對話訊息建立隔離的上下文 ID
     * 用於確保訊息只屬於特定的 conversation
     *
     * @param conversationId 對話 ID
     * @param messageIndex 訊息索引
     * @return 隔離的訊息 context ID
     */
    public String buildIsolatedMessageContextId(UUID conversationId, int messageIndex) {
        return String.format("conv:%s:msg:%d", conversationId, messageIndex);
    }

    private String buildSessionKey(UUID conversationId) {
        return SESSION_KEY_PREFIX + conversationId;
    }

    private String buildUserSessionsKey(UUID userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }

    private String buildSessionData(UUID userId, UUID conversationId) {
        return String.format("%s|%s|%d",
            userId,
            conversationId,
            Instant.now().toEpochMilli()
        );
    }

    private UUID extractUserId(String sessionData) {
        String[] parts = sessionData.split("\\|");
        return UUID.fromString(parts[0]);
    }

    private Instant extractCreatedAt(String sessionData) {
        String[] parts = sessionData.split("\\|");
        return Instant.ofEpochMilli(Long.parseLong(parts[2]));
    }

    private void cleanupOldestSession(UUID userId) {
        Set<String> sessions = getUserSessions(userId);
        if (sessions.isEmpty()) {
            return;
        }

        // 找出最舊的 session（基於 session 建立時間）
        String oldestSessionId = null;
        Instant oldestTime = Instant.MAX;

        for (String sessionIdStr : sessions) {
            try {
                UUID sessionId = UUID.fromString(sessionIdStr);
                String sessionKey = buildSessionKey(sessionId);
                String sessionData = redisTemplate.opsForValue().get(sessionKey);

                if (sessionData == null) {
                    // Session 已過期但還在清單中，直接移除
                    redisTemplate.opsForSet().remove(buildUserSessionsKey(userId), sessionIdStr);
                    continue;
                }

                Instant createdAt = extractCreatedAt(sessionData);
                if (createdAt.isBefore(oldestTime)) {
                    oldestTime = createdAt;
                    oldestSessionId = sessionIdStr;
                }
            } catch (Exception e) {
                log.warn("Error processing session {}: {}", sessionIdStr, e.getMessage());
            }
        }

        if (oldestSessionId != null) {
            UUID sessionId = UUID.fromString(oldestSessionId);
            String sessionKey = buildSessionKey(sessionId);
            String userSessionsKey = buildUserSessionsKey(userId);

            redisTemplate.delete(sessionKey);
            redisTemplate.opsForSet().remove(userSessionsKey, oldestSessionId);

            log.info("Cleaned up oldest session {} for user {}", oldestSessionId, userId);
        }
    }

    /**
     * Session 上下文
     */
    public record SessionContext(
        UUID userId,
        UUID conversationId,
        Instant createdAt
    ) {
        public Duration getAge() {
            return Duration.between(createdAt, Instant.now());
        }
    }

    /**
     * Session 存取被拒絕例外
     */
    public static class SessionAccessDeniedException extends RuntimeException {
        public SessionAccessDeniedException(String message) {
            super(message);
        }
    }
}
