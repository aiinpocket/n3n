package com.aiinpocket.n3n.agent.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 元件上下文快取
 *
 * 使用 Redis 快取元件上下文，減少資料庫查詢。
 * 當元件發生變更時自動失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComponentContextCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "ai:component-context";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * 從快取取得元件上下文
     *
     * @return 元件上下文（如果存在且未過期）
     */
    public Optional<Map<String, Object>> get() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY);
            if (json == null) {
                return Optional.empty();
            }

            Map<String, Object> context = objectMapper.readValue(
                json, new TypeReference<Map<String, Object>>() {}
            );
            return Optional.of(context);
        } catch (Exception e) {
            log.warn("Failed to read component context from cache: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 將元件上下文存入快取
     *
     * @param context 元件上下文
     */
    public void put(Map<String, Object> context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(CACHE_KEY, json, DEFAULT_TTL);
            log.debug("Cached component context");
        } catch (Exception e) {
            log.warn("Failed to cache component context: {}", e.getMessage());
        }
    }

    /**
     * 使快取失效
     */
    public void invalidate() {
        try {
            redisTemplate.delete(CACHE_KEY);
            log.info("Component context cache invalidated");
        } catch (Exception e) {
            log.warn("Failed to invalidate component context cache: {}", e.getMessage());
        }
    }

    /**
     * 檢查快取是否存在
     */
    public boolean exists() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY));
    }

    /**
     * 取得快取剩餘 TTL
     */
    public Optional<Duration> getTtl() {
        Long ttl = redisTemplate.getExpire(CACHE_KEY);
        if (ttl == null || ttl < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(ttl));
    }

    // ============ Event Listeners for Cache Invalidation ============

    /**
     * 元件建立事件
     */
    @EventListener
    public void onComponentCreated(ComponentCreatedEvent event) {
        log.debug("Component created: {}, invalidating cache", event.componentId());
        invalidate();
    }

    /**
     * 元件更新事件
     */
    @EventListener
    public void onComponentUpdated(ComponentUpdatedEvent event) {
        log.debug("Component updated: {}, invalidating cache", event.componentId());
        invalidate();
    }

    /**
     * 元件刪除事件
     */
    @EventListener
    public void onComponentDeleted(ComponentDeletedEvent event) {
        log.debug("Component deleted: {}, invalidating cache", event.componentId());
        invalidate();
    }

    /**
     * 元件版本啟用事件
     */
    @EventListener
    public void onComponentVersionActivated(ComponentVersionActivatedEvent event) {
        log.debug("Component version activated: {}, invalidating cache", event.versionId());
        invalidate();
    }

    // ============ Event Classes ============

    /**
     * 元件建立事件
     */
    public record ComponentCreatedEvent(String componentId) {}

    /**
     * 元件更新事件
     */
    public record ComponentUpdatedEvent(String componentId) {}

    /**
     * 元件刪除事件
     */
    public record ComponentDeletedEvent(String componentId) {}

    /**
     * 元件版本啟用事件
     */
    public record ComponentVersionActivatedEvent(String versionId, String componentId) {}
}
