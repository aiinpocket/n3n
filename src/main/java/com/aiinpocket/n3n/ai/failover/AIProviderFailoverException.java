package com.aiinpocket.n3n.ai.failover;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Provider Failover 失敗異常
 * 當所有 Provider 都失敗時拋出
 */
public class AIProviderFailoverException extends RuntimeException {

    private final List<FailoverAIProviderWrapper.ProviderAttempt> attempts;

    public AIProviderFailoverException(String message, List<FailoverAIProviderWrapper.ProviderAttempt> attempts) {
        super(buildMessage(message, attempts));
        this.attempts = attempts;
    }

    public List<FailoverAIProviderWrapper.ProviderAttempt> getAttempts() {
        return attempts;
    }

    private static String buildMessage(String message, List<FailoverAIProviderWrapper.ProviderAttempt> attempts) {
        String attemptSummary = attempts.stream()
            .map(a -> String.format("%s: %s", a.providerName(), a.message()))
            .collect(Collectors.joining(", "));
        return message + " - Attempts: [" + attemptSummary + "]";
    }
}
