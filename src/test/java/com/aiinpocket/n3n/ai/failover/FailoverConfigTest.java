package com.aiinpocket.n3n.ai.failover;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverConfigTest {

    @Test
    void defaultConfig_shouldHaveExpectedDefaults() {
        FailoverConfig config = FailoverConfig.defaultConfig();

        assertThat(config.getProviderOrder()).containsExactly("user_default", "llamafile");
        assertThat(config.getMaxRetries()).isEqualTo(2);
        assertThat(config.getRetryDelayMs()).isEqualTo(1000);
        assertThat(config.isEnableCircuitBreaker()).isTrue();
        assertThat(config.getCircuitBreakerThreshold()).isEqualTo(3);
        assertThat(config.getCircuitBreakerResetMs()).isEqualTo(60000);
    }

    @Test
    void localOnly_shouldOnlyUseLlamafile() {
        FailoverConfig config = FailoverConfig.localOnly();

        assertThat(config.getProviderOrder()).containsExactly("llamafile");
        assertThat(config.isEnableCircuitBreaker()).isFalse();
    }

    @Test
    void builder_shouldAllowCustomConfiguration() {
        FailoverConfig config = FailoverConfig.builder()
            .providerOrder(List.of("openai", "claude", "llamafile"))
            .maxRetries(3)
            .retryDelayMs(500)
            .enableCircuitBreaker(true)
            .circuitBreakerThreshold(5)
            .circuitBreakerResetMs(30000)
            .build();

        assertThat(config.getProviderOrder()).containsExactly("openai", "claude", "llamafile");
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getRetryDelayMs()).isEqualTo(500);
        assertThat(config.getCircuitBreakerThreshold()).isEqualTo(5);
        assertThat(config.getCircuitBreakerResetMs()).isEqualTo(30000);
    }
}
