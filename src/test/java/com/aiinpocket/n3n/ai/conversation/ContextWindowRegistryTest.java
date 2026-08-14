package com.aiinpocket.n3n.ai.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ContextWindowRegistryTest {

    private ContextWindowRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ContextWindowRegistry();
        ReflectionTestUtils.setField(registry, "claude1mEnabled", false);
        ReflectionTestUtils.setField(registry, "defaultWindow", 200_000);
    }

    @Test
    @DisplayName("claude sonnet 4.5 and opus 4 default to 200k")
    void claudeModels_default200k() {
        assertThat(registry.windowFor("claude-sonnet-4-5-20250929")).isEqualTo(200_000);
        assertThat(registry.windowFor("claude-opus-4-1")).isEqualTo(200_000);
    }

    @Test
    @DisplayName("claude 1M beta flag raises sonnet/opus window to 1M")
    void claude1mFlag_raisesWindow() {
        ReflectionTestUtils.setField(registry, "claude1mEnabled", true);
        assertThat(registry.windowFor("claude-sonnet-4-5")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-opus-4-5")).isEqualTo(1_000_000);
        // 4.1 世代不支援 1M beta
        assertThat(registry.windowFor("claude-opus-4-1")).isEqualTo(200_000);
    }

    @Test
    @DisplayName("claude 4.6+ / 5 世代標準即 1M（不需 beta flag）")
    void claude46Plus_default1m() {
        assertThat(registry.windowFor("claude-sonnet-4-6")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-sonnet-5")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-opus-4-8")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-opus-5")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-fable-5")).isEqualTo(1_000_000);
        assertThat(registry.windowFor("claude-haiku-4-5")).isEqualTo(200_000);
    }

    @Test
    @DisplayName("gemini 2.5 pro/flash map to 1_048_576")
    void gemini25_window() {
        assertThat(registry.windowFor("gemini-2.5-pro")).isEqualTo(1_048_576);
        assertThat(registry.windowFor("gemini-2.5-flash")).isEqualTo(1_048_576);
    }

    @Test
    @DisplayName("gpt-4o is 128k and gpt-4.1 is 1_047_576")
    void gptModels_window() {
        assertThat(registry.windowFor("gpt-4o")).isEqualTo(128_000);
        assertThat(registry.windowFor("gpt-4o-mini")).isEqualTo(128_000);
        assertThat(registry.windowFor("gpt-4.1")).isEqualTo(1_047_576);
    }

    @Test
    @DisplayName("matching is case-insensitive and trims whitespace")
    void matching_caseInsensitive() {
        assertThat(registry.windowFor("  GPT-4o  ")).isEqualTo(128_000);
        assertThat(registry.windowFor("Claude-Sonnet-4-5")).isEqualTo(200_000);
    }

    @Test
    @DisplayName("unknown, null, or blank models fall back to default window")
    void unknownModel_fallsBack() {
        ReflectionTestUtils.setField(registry, "defaultWindow", 123_456);
        assertThat(registry.windowFor("some-local-llm")).isEqualTo(123_456);
        assertThat(registry.windowFor(null)).isEqualTo(123_456);
        assertThat(registry.windowFor("  ")).isEqualTo(123_456);
    }
}
