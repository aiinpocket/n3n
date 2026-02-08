package com.aiinpocket.n3n.ai.security;

import com.aiinpocket.n3n.ai.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 請求 Rate Limiter
 *
 * 使用 Redis 滑動視窗算法實現分散式 rate limiting
 */
@Slf4j
@Component
public class AiRateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${ai.rate-limit.default-rpm:60}")
    private int defaultRequestsPerMinute;

    @Value("${ai.rate-limit.default-tpm:100000}")
    private int defaultTokensPerMinute;

    @Value("${ai.rate-limit.burst-multiplier:1.5}")
    private double burstMultiplier;

    /**
     * Fail-close 模式：Redis 不可用時拒絕請求（預設開啟）
     * 設為 false 可改為 fail-open（不建議用於生產環境）
     */
    @Value("${ai.rate-limit.fail-close:true}")
    private boolean failClose;

    private static final String KEY_PREFIX = "ai:ratelimit:";
    private static final String REQUEST_KEY_SUFFIX = ":requests";
    private static final String TOKEN_KEY_SUFFIX = ":tokens";

    /**
     * 滑動視窗 Rate Limit Lua Script
     *
     * KEYS[1] = rate limit key
     * ARGV[1] = window size in seconds
     * ARGV[2] = max requests
     * ARGV[3] = current timestamp (ms)
     * ARGV[4] = request id (for uniqueness)
     *
     * Returns: [allowed (0/1), remaining, resetMs]
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local requestId = ARGV[4]

        -- 移除過期的記錄
        local windowStart = now - (window * 1000)
        redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

        -- 計算當前請求數
        local currentCount = redis.call('ZCARD', key)

        if currentCount >= limit then
            -- 超過限制
            local oldestScore = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local resetMs = 0
            if #oldestScore > 0 then
                resetMs = tonumber(oldestScore[2]) + (window * 1000) - now
            end
            return {0, limit - currentCount, resetMs}
        end

        -- 允許請求，加入記錄
        redis.call('ZADD', key, now, requestId)
        redis.call('PEXPIRE', key, window * 1000)

        return {1, limit - currentCount - 1, 0}
        """;

    /**
     * Token 計數 Lua Script
     *
     * KEYS[1] = token count key
     * ARGV[1] = window size in seconds
     * ARGV[2] = max tokens
     * ARGV[3] = tokens to add
     *
     * Returns: [allowed (0/1), remaining, resetSeconds]
     */
    private static final String TOKEN_COUNT_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local tokensToAdd = tonumber(ARGV[3])

        local current = tonumber(redis.call('GET', key) or '0')
        local ttl = redis.call('TTL', key)

        if ttl < 0 then
            ttl = window
        end

        if current + tokensToAdd > limit then
            return {0, limit - current, ttl}
        end

        local newCount = redis.call('INCRBY', key, tokensToAdd)
        if ttl == window then
            redis.call('EXPIRE', key, window)
        end

        return {1, limit - newCount, ttl}
        """;

    private final DefaultRedisScript<List> slidingWindowScript;
    private final DefaultRedisScript<List> tokenCountScript;

    public AiRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(List.class);

        this.tokenCountScript = new DefaultRedisScript<>();
        this.tokenCountScript.setScriptText(TOKEN_COUNT_SCRIPT);
        this.tokenCountScript.setResultType(List.class);
    }

    /**
     * 檢查並消耗一個請求配額
     *
     * @param userId 使用者 ID
     * @throws RateLimitExceededException 如果超過限制
     */
    public void checkRequestLimit(UUID userId) {
        checkRequestLimit(userId, defaultRequestsPerMinute);
    }

    /**
     * 檢查並消耗一個請求配額
     *
     * @param userId 使用者 ID
     * @param maxRequestsPerMinute 每分鐘最大請求數
     * @throws RateLimitExceededException 如果超過限制
     */
    public void checkRequestLimit(UUID userId, int maxRequestsPerMinute) {
        String key = KEY_PREFIX + userId + REQUEST_KEY_SUFFIX;
        String requestId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        // Prevent integer overflow: compute in long, then clamp
        long effectiveLimitLong = (long) (maxRequestsPerMinute * burstMultiplier);
        int effectiveLimit = (int) Math.min(effectiveLimitLong, Integer.MAX_VALUE);

        List<Long> result = redisTemplate.execute(
            slidingWindowScript,
            List.of(key),
            "60",
            String.valueOf(effectiveLimit),
            String.valueOf(now),
            requestId
        );

        if (result == null || result.isEmpty()) {
            if (failClose) {
                log.error("Rate limit check failed for user {} (Redis unavailable), denying request", userId);
                throw new RateLimitExceededException("System busy, please try again later");
            }
            log.warn("Rate limit check failed for user {}, allowing request (fail-open mode)", userId);
            return;
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long resetMs = result.get(2);

        if (!allowed) {
            log.warn("Rate limit exceeded for user {}, reset in {}ms", userId, resetMs);
            throw new RateLimitExceededException(
                String.format("Request rate limit exceeded, please try again in %d second(s)", resetMs / 1000 + 1)
            );
        }

        log.debug("Request allowed for user {}, remaining: {}", userId, remaining);
    }

    /**
     * 檢查 Token 使用量
     *
     * @param userId 使用者 ID
     * @param tokenCount 預估 token 數量
     * @throws RateLimitExceededException 如果超過限制
     */
    public void checkTokenLimit(UUID userId, int tokenCount) {
        checkTokenLimit(userId, tokenCount, defaultTokensPerMinute);
    }

    /**
     * 檢查 Token 使用量
     *
     * @param userId 使用者 ID
     * @param tokenCount 預估 token 數量
     * @param maxTokensPerMinute 每分鐘最大 token 數
     * @throws RateLimitExceededException 如果超過限制
     */
    public void checkTokenLimit(UUID userId, int tokenCount, int maxTokensPerMinute) {
        String key = KEY_PREFIX + userId + TOKEN_KEY_SUFFIX;

        List<Long> result = redisTemplate.execute(
            tokenCountScript,
            List.of(key),
            "60",
            String.valueOf(maxTokensPerMinute),
            String.valueOf(tokenCount)
        );

        if (result == null || result.isEmpty()) {
            if (failClose) {
                log.error("Token limit check failed for user {} (Redis unavailable), denying request", userId);
                throw new RateLimitExceededException("System busy, please try again later");
            }
            log.warn("Token limit check failed for user {}, allowing request (fail-open mode)", userId);
            return;
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long resetSeconds = result.get(2);

        if (!allowed) {
            log.warn("Token limit exceeded for user {}, used {} tokens, reset in {}s",
                userId, tokenCount, resetSeconds);
            throw new RateLimitExceededException(
                String.format("Token usage limit exceeded, please try again in %d second(s)", resetSeconds)
            );
        }

        log.debug("Token usage allowed for user {}, used: {}, remaining: {}",
            userId, tokenCount, remaining);
    }

    /**
     * 記錄實際使用的 token 數量（用於事後校正）
     *
     * @param userId 使用者 ID
     * @param actualTokens 實際使用的 token 數
     * @param estimatedTokens 預估的 token 數
     */
    public void recordActualTokenUsage(UUID userId, int actualTokens, int estimatedTokens) {
        int difference = actualTokens - estimatedTokens;

        if (difference > 0) {
            // 實際使用量超過預估，需要補計
            String key = KEY_PREFIX + userId + TOKEN_KEY_SUFFIX;
            redisTemplate.opsForValue().increment(key, difference);
            log.debug("Adjusted token count for user {}, added {} tokens", userId, difference);
        }
        // 如果實際使用量少於預估，我們選擇不退還配額（保守策略）
    }

    /**
     * 取得使用者當前的 rate limit 狀態
     *
     * @param userId 使用者 ID
     * @return Rate limit 狀態
     */
    public RateLimitStatus getStatus(UUID userId) {
        String requestKey = KEY_PREFIX + userId + REQUEST_KEY_SUFFIX;
        String tokenKey = KEY_PREFIX + userId + TOKEN_KEY_SUFFIX;

        Long requestCount = redisTemplate.opsForZSet().zCard(requestKey);
        String tokenCountStr = redisTemplate.opsForValue().get(tokenKey);
        Long tokenCount = tokenCountStr != null ? Long.parseLong(tokenCountStr) : 0L;

        Long requestTtl = redisTemplate.getExpire(requestKey);
        Long tokenTtl = redisTemplate.getExpire(tokenKey);

        return new RateLimitStatus(
            requestCount != null ? requestCount.intValue() : 0,
            defaultRequestsPerMinute,
            tokenCount.intValue(),
            defaultTokensPerMinute,
            requestTtl != null && requestTtl > 0 ? Duration.ofSeconds(requestTtl) : Duration.ZERO,
            tokenTtl != null && tokenTtl > 0 ? Duration.ofSeconds(tokenTtl) : Duration.ZERO
        );
    }

    /**
     * 重置使用者的 rate limit（管理員功能）
     *
     * @param userId 使用者 ID
     */
    public void reset(UUID userId) {
        String requestKey = KEY_PREFIX + userId + REQUEST_KEY_SUFFIX;
        String tokenKey = KEY_PREFIX + userId + TOKEN_KEY_SUFFIX;

        redisTemplate.delete(List.of(requestKey, tokenKey));
        log.info("Rate limit reset for user {}", userId);
    }

    /**
     * Rate Limit 狀態
     */
    public record RateLimitStatus(
        int requestsUsed,
        int requestsLimit,
        int tokensUsed,
        int tokensLimit,
        Duration requestsResetIn,
        Duration tokensResetIn
    ) {
        public int requestsRemaining() {
            return Math.max(0, requestsLimit - requestsUsed);
        }

        public int tokensRemaining() {
            return Math.max(0, tokensLimit - tokensUsed);
        }
    }
}
