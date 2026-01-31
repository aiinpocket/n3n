package com.aiinpocket.n3n.ai.exception;

/**
 * Rate Limit 超過例外
 */
public class RateLimitExceededException extends AiProviderException {

    public RateLimitExceededException(String message) {
        super(message, "RATE_LIMIT_EXCEEDED");
    }
}
