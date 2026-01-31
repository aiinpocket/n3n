package com.aiinpocket.n3n.ai.exception;

/**
 * 模型找不到例外
 */
public class ModelNotFoundException extends AiProviderException {

    public ModelNotFoundException(String message) {
        super(message, "MODEL_NOT_FOUND");
    }
}
