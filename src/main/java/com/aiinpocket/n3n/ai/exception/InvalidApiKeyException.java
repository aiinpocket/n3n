package com.aiinpocket.n3n.ai.exception;

/**
 * 無效 API Key 例外
 */
public class InvalidApiKeyException extends AiProviderException {

    public InvalidApiKeyException(String message) {
        super(message, "INVALID_API_KEY");
    }
}
