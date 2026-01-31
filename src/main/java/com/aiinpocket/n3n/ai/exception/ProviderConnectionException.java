package com.aiinpocket.n3n.ai.exception;

/**
 * AI Provider 連線例外
 */
public class ProviderConnectionException extends AiProviderException {

    public ProviderConnectionException(String message) {
        super(message, "PROVIDER_CONNECTION_ERROR");
    }

    public ProviderConnectionException(String message, Throwable cause) {
        super(message, "PROVIDER_CONNECTION_ERROR", cause);
    }
}
