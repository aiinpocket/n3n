package com.aiinpocket.n3n.auth.security;

import com.aiinpocket.n3n.auth.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * General-purpose IP-based rate limiter for sensitive endpoints.
 * Uses Redis sliding window algorithm.
 */
@Slf4j
@Component
public class IpRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;

    @Value("${auth.rate-limit.enabled:true}")
    private boolean enabled;

    private static final String KEY_PREFIX = "auth:ratelimit:endpoint:";

    private static final String SLIDING_WINDOW_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local requestId = ARGV[4]

        redis.call('ZREMRANGEBYSCORE', key, '-inf', now - (window * 1000))
        local currentCount = redis.call('ZCARD', key)

        if currentCount >= limit then
            return {0, 0, 0}
        end

        redis.call('ZADD', key, now, requestId)
        redis.call('PEXPIRE', key, window * 1000)
        return {1, limit - currentCount - 1, 0}
        """;

    public IpRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(List.class);
    }

    /**
     * Check if an IP is allowed to access a specific endpoint.
     *
     * @param endpoint    Endpoint name (e.g., "register", "forgot-password")
     * @param ipAddress   Client IP address
     * @param maxAttempts Maximum attempts allowed in the window
     * @param windowSecs  Time window in seconds
     * @throws RateLimitException if rate limit exceeded
     */
    public void checkAllowed(String endpoint, String ipAddress, int maxAttempts, int windowSecs) {
        if (!enabled) return;

        try {
            String key = KEY_PREFIX + endpoint + ":" + ipAddress;
            long now = Instant.now().toEpochMilli();
            String requestId = UUID.randomUUID().toString();

            List<Long> result = redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(windowSecs),
                String.valueOf(maxAttempts),
                String.valueOf(now),
                requestId
            );

            if (result == null || result.isEmpty() || result.get(0) != 1L) {
                log.warn("Rate limit exceeded for {} from IP {}", endpoint, ipAddress);
                throw new RateLimitException("Too many requests. Please try again later.");
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.error("IP rate limit check failed for {}: {}", endpoint, e.getMessage());
            // Fail-close: reject on Redis error
            throw new RateLimitException("Too many requests. Please try again later.");
        }
    }
}
