package com.aiinpocket.n3n.execution.handler.handlers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyUtilTest {

    @Test
    void sha256Hex_isDeterministic() {
        assertThat(CacheKeyUtil.sha256Hex("secret"))
            .isEqualTo(CacheKeyUtil.sha256Hex("secret"));
    }

    @Test
    void sha256Hex_producesLowercase64CharHex() {
        String hex = CacheKeyUtil.sha256Hex("anything");
        assertThat(hex).hasSize(64);
        assertThat(hex).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256Hex_differentInputsYieldDifferentDigests() {
        // Core of the auth-bypass fix: a different password must produce a different key.
        assertThat(CacheKeyUtil.sha256Hex("password-A"))
            .isNotEqualTo(CacheKeyUtil.sha256Hex("password-B"));
    }

    @Test
    void sha256Hex_neverContainsThePlaintextSecret() {
        String secret = "SuperSecretPassword123";
        assertThat(CacheKeyUtil.sha256Hex(secret)).doesNotContain(secret);
    }

    @Test
    void sha256Hex_treatsNullAsEmptyString() {
        assertThat(CacheKeyUtil.sha256Hex(null))
            .isEqualTo(CacheKeyUtil.sha256Hex(""));
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        // Known SHA-256 of the empty string.
        assertThat(CacheKeyUtil.sha256Hex(""))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void nullToEmpty_returnsEmptyForNull() {
        assertThat(CacheKeyUtil.nullToEmpty(null)).isEmpty();
        assertThat(CacheKeyUtil.nullToEmpty("x")).isEqualTo("x");
    }
}
