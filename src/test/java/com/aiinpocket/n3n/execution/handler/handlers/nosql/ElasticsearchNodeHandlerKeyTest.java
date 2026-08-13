package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Elasticsearch client cache key folds in all authentication material (password,
 * api-key) via SHA-256, so a caller with the correct host/username but a wrong secret cannot
 * reuse another user's authenticated client.
 */
class ElasticsearchNodeHandlerKeyTest {

    private ElasticsearchNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ElasticsearchNodeHandler(new ObjectMapper());
    }

    @Test
    void differentPassword_producesDifferentKey() {
        Map<String, Object> a = Map.of("host", "h", "port", "9200", "username", "u", "password", "pw1");
        Map<String, Object> b = Map.of("host", "h", "port", "9200", "username", "u", "password", "pw2");
        assertThat(handler.generateCacheKey(a)).isNotEqualTo(handler.generateCacheKey(b));
    }

    @Test
    void differentApiKey_producesDifferentKey() {
        Map<String, Object> a = Map.of("host", "h", "port", "9200", "username", "u", "apiKey", "key-A");
        Map<String, Object> b = Map.of("host", "h", "port", "9200", "username", "u", "apiKey", "key-B");
        assertThat(handler.generateCacheKey(a)).isNotEqualTo(handler.generateCacheKey(b));
    }

    @Test
    void sameCredentials_produceSameKey() {
        Map<String, Object> a = Map.of("host", "h", "port", "9200", "username", "u", "password", "pw");
        Map<String, Object> b = Map.of("host", "h", "port", "9200", "username", "u", "password", "pw");
        assertThat(handler.generateCacheKey(a)).isEqualTo(handler.generateCacheKey(b));
    }

    @Test
    void key_neverContainsPlaintextPassword() {
        Map<String, Object> a = Map.of("host", "h", "port", "9200", "username", "u", "password", "SuperSecret");
        assertThat(handler.generateCacheKey(a)).doesNotContain("SuperSecret");
    }
}
