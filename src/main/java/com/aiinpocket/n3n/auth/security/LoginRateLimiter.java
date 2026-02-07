package com.aiinpocket.n3n.auth.security;

import com.aiinpocket.n3n.auth.exception.RateLimitException;
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
 * 登入頻率限制器
 *
 * 防止暴力破解攻擊，使用雙重限制：
 * 1. 基於 IP：限制每個 IP 的登入嘗試次數
 * 2. 基於帳號：限制每個帳號的登入嘗試次數
 *
 * 採用 Fail-Close 策略：Redis 不可用時拒絕請求
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;

    // IP 限制：每分鐘最多 10 次嘗試
    @Value("${auth.rate-limit.ip-max-attempts:10}")
    private int ipMaxAttempts;

    @Value("${auth.rate-limit.ip-window-seconds:60}")
    private int ipWindowSeconds;

    // 帳號限制：每 5 分鐘最多 5 次失敗嘗試
    @Value("${auth.rate-limit.account-max-attempts:5}")
    private int accountMaxAttempts;

    @Value("${auth.rate-limit.account-window-seconds:300}")
    private int accountWindowSeconds;

    // 帳號鎖定：連續失敗後鎖定 15 分鐘
    @Value("${auth.rate-limit.lockout-duration-seconds:900}")
    private int lockoutDurationSeconds;

    @Value("${auth.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${auth.rate-limit.fail-open:false}")
    private boolean failOpen;

    private static final String KEY_PREFIX = "auth:ratelimit:";
    private static final String IP_KEY_PREFIX = KEY_PREFIX + "ip:";
    private static final String ACCOUNT_KEY_PREFIX = KEY_PREFIX + "account:";
    private static final String LOCKOUT_KEY_PREFIX = KEY_PREFIX + "lockout:";

    /**
     * 滑動視窗 Rate Limit Lua Script
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

    private final DefaultRedisScript<List> slidingWindowScript;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(List.class);
    }

    /**
     * 檢查登入請求是否被允許（登入前呼叫）
     *
     * @param ipAddress 客戶端 IP
     * @param email     登入帳號
     * @throws RateLimitException 如果超過限制
     */
    public void checkLoginAllowed(String ipAddress, String email) {
        if (!enabled) {
            return;
        }

        try {
            // 1. 檢查帳號是否被鎖定
            checkAccountLockout(email);

            // 2. 檢查 IP 限制
            checkIpLimit(ipAddress);

            // 3. 檢查帳號限制（基於失敗次數）
            checkAccountLimit(email);

        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.error("Rate limit check failed: {}", e.getMessage());
            if (!failOpen) {
                throw new RateLimitException("System busy, please try again later");
            }
            // fail-open: 允許請求繼續
            log.warn("Rate limiter fail-open: allowing request due to Redis error");
        }
    }

    /**
     * 記錄登入失敗（登入失敗後呼叫）
     */
    public void recordLoginFailure(String ipAddress, String email) {
        if (!enabled) {
            return;
        }

        try {
            String accountKey = ACCOUNT_KEY_PREFIX + normalizeEmail(email);
            String requestId = UUID.randomUUID().toString();
            long now = Instant.now().toEpochMilli();

            // 記錄失敗嘗試
            redisTemplate.execute(
                slidingWindowScript,
                List.of(accountKey),
                String.valueOf(accountWindowSeconds),
                String.valueOf(accountMaxAttempts + 10), // 允許記錄，但檢查時用 accountMaxAttempts
                String.valueOf(now),
                requestId
            );

            // 檢查是否需要鎖定帳號
            Long failCount = redisTemplate.opsForZSet().zCard(accountKey);
            if (failCount != null && failCount >= accountMaxAttempts) {
                lockAccount(email);
            }

            log.debug("Recorded login failure for {}, total failures: {}", email, failCount);

        } catch (Exception e) {
            log.error("Failed to record login failure: {}", e.getMessage());
        }
    }

    /**
     * 記錄登入成功（登入成功後呼叫，清除失敗計數）
     */
    public void recordLoginSuccess(String email) {
        if (!enabled) {
            return;
        }

        try {
            String accountKey = ACCOUNT_KEY_PREFIX + normalizeEmail(email);
            String lockoutKey = LOCKOUT_KEY_PREFIX + normalizeEmail(email);

            // 清除失敗計數和鎖定狀態
            redisTemplate.delete(List.of(accountKey, lockoutKey));

            log.debug("Cleared login failure count for {}", email);

        } catch (Exception e) {
            log.error("Failed to clear login failure count: {}", e.getMessage());
        }
    }

    private void checkIpLimit(String ipAddress) {
        String key = IP_KEY_PREFIX + ipAddress;
        String requestId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        List<Long> result = redisTemplate.execute(
            slidingWindowScript,
            List.of(key),
            String.valueOf(ipWindowSeconds),
            String.valueOf(ipMaxAttempts),
            String.valueOf(now),
            requestId
        );

        if (result == null || result.isEmpty()) {
            if (!failOpen) {
                throw new RateLimitException("System busy, please try again later");
            }
            return;
        }

        boolean allowed = result.get(0) == 1L;
        long resetMs = result.get(2);

        if (!allowed) {
            log.warn("IP rate limit exceeded for {}", ipAddress);
            throw new RateLimitException(
                String.format("Too many login attempts, please try again in %d second(s)", resetMs / 1000 + 1)
            );
        }
    }

    private void checkAccountLimit(String email) {
        String key = ACCOUNT_KEY_PREFIX + normalizeEmail(email);

        Long failCount = redisTemplate.opsForZSet().zCard(key);
        if (failCount != null && failCount >= accountMaxAttempts) {
            log.warn("Account rate limit exceeded for {}", email);
            throw new RateLimitException(
                String.format("Too many failed login attempts for this account, please try again in %d minute(s)", accountWindowSeconds / 60)
            );
        }
    }

    private void checkAccountLockout(String email) {
        String key = LOCKOUT_KEY_PREFIX + normalizeEmail(email);
        String lockoutUntil = redisTemplate.opsForValue().get(key);

        if (lockoutUntil != null) {
            long remaining = Long.parseLong(lockoutUntil) - System.currentTimeMillis();
            if (remaining > 0) {
                log.warn("Account {} is locked out", email);
                throw new RateLimitException(
                    String.format("Account temporarily locked. Please try again in %d minute(s).", remaining / 60000 + 1)
                );
            }
        }
    }

    private void lockAccount(String email) {
        String key = LOCKOUT_KEY_PREFIX + normalizeEmail(email);
        long lockoutUntil = System.currentTimeMillis() + (lockoutDurationSeconds * 1000L);

        redisTemplate.opsForValue().set(key, String.valueOf(lockoutUntil),
            Duration.ofSeconds(lockoutDurationSeconds));

        log.warn("Account {} locked out for {} seconds", email, lockoutDurationSeconds);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    /**
     * 取得帳號剩餘嘗試次數（供 API 回傳）
     */
    public int getRemainingAttempts(String email) {
        try {
            String key = ACCOUNT_KEY_PREFIX + normalizeEmail(email);
            Long failCount = redisTemplate.opsForZSet().zCard(key);
            int used = failCount != null ? failCount.intValue() : 0;
            return Math.max(0, accountMaxAttempts - used);
        } catch (Exception e) {
            return accountMaxAttempts;
        }
    }

    /**
     * 管理員功能：解鎖帳號
     */
    public void unlockAccount(String email) {
        String accountKey = ACCOUNT_KEY_PREFIX + normalizeEmail(email);
        String lockoutKey = LOCKOUT_KEY_PREFIX + normalizeEmail(email);

        redisTemplate.delete(List.of(accountKey, lockoutKey));
        log.info("Account {} manually unlocked", email);
    }
}
