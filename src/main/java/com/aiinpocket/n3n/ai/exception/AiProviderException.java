package com.aiinpocket.n3n.ai.exception;

/**
 * AI Provider 基礎例外
 */
public class AiProviderException extends RuntimeException {

    private final String errorCode;

    public AiProviderException(String message) {
        super(message);
        this.errorCode = "AI_ERROR";
    }

    public AiProviderException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AI_ERROR";
    }

    public AiProviderException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
