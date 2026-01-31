package com.aiinpocket.n3n.auth.exception;

/**
 * 頻率限制例外
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
