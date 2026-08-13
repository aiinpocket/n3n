package com.aiinpocket.n3n.ai.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {

    @Test
    @DisplayName("null and empty text estimate to 0")
    void nullAndEmpty_returnZero() {
        assertThat(TokenEstimator.estimate(null)).isZero();
        assertThat(TokenEstimator.estimate("")).isZero();
    }

    @Test
    @DisplayName("latin text estimates ~4 chars per token")
    void latinText_fourCharsPerToken() {
        // 8 latin chars -> 2 tokens
        assertThat(TokenEstimator.estimate("abcdefgh")).isEqualTo(2);
        // 9 chars -> ceil(9/4) = 3
        assertThat(TokenEstimator.estimate("abcdefghi")).isEqualTo(3);
    }

    @Test
    @DisplayName("CJK characters estimate 1 token each")
    void cjkText_oneTokenPerChar() {
        assertThat(TokenEstimator.estimate("繁體中文")).isEqualTo(4);
        assertThat(TokenEstimator.estimate("ひらがな")).isEqualTo(4);
    }

    @Test
    @DisplayName("mixed text sums CJK and latin contributions")
    void mixedText_sums() {
        // 2 CJK + 4 latin = 2 + 1 = 3
        assertThat(TokenEstimator.estimate("中文abcd")).isEqualTo(3);
    }

    @Test
    @DisplayName("estimate is deterministic")
    void deterministic() {
        String text = "使用者偏好 Slack 通知 with some latin text";
        assertThat(TokenEstimator.estimate(text)).isEqualTo(TokenEstimator.estimate(text));
    }

    @Test
    @DisplayName("estimateMessage adds framing overhead")
    void estimateMessage_addsOverhead() {
        Map<String, Object> message = Map.of("role", "user", "content", "abcd");
        // 1 content token + 4 overhead
        assertThat(TokenEstimator.estimateMessage(message)).isEqualTo(5);
        assertThat(TokenEstimator.estimateMessage(null)).isZero();
    }

    @Test
    @DisplayName("estimateMessages sums all messages")
    void estimateMessages_sums() {
        List<Map<String, Object>> messages = List.of(
            Map.of("role", "user", "content", "abcd"),
            Map.of("role", "assistant", "content", "中文")
        );
        // (1+4) + (2+4) = 11
        assertThat(TokenEstimator.estimateMessages(messages)).isEqualTo(11);
        assertThat(TokenEstimator.estimateMessages(null)).isZero();
        assertThat(TokenEstimator.estimateMessages(List.of())).isZero();
    }
}
