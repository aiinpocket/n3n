package com.aiinpocket.n3n.execution.handler.handlers.scripting;

/**
 * Exception thrown when script execution fails.
 */
public class ScriptExecutionException extends RuntimeException {

    private final String errorType;

    public ScriptExecutionException(String message) {
        super(message);
        this.errorType = "EXECUTION_ERROR";
    }

    public ScriptExecutionException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = "EXECUTION_ERROR";
    }

    public ScriptExecutionException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
