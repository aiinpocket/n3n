package com.aiinpocket.n3n.base;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Base class for service unit tests using Mockito.
 * Provides common mock setup for Redis dependencies.
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseServiceTest {

    // Note: Redis mocks should be declared in subclasses using @Mock
    // Example:
    // @Mock protected StringRedisTemplate stringRedisTemplate;
    // @Mock protected RedisTemplate<String, Object> redisTemplate;
}
