package com.aiinpocket.n3n.execution.handler.handlers.scripting;

import java.util.Map;

/**
 * Interface for script execution engines.
 */
public interface ScriptEngine {

    /**
     * Get the language this engine supports.
     */
    String getLanguage();

    /**
     * Execute a script with the given input data.
     *
     * @param code the script code to execute
     * @param input the input data available to the script
     * @param timeout timeout in milliseconds
     * @return the script execution result
     * @throws ScriptExecutionException if execution fails
     */
    ScriptResult execute(String code, Map<String, Object> input, long timeout) throws ScriptExecutionException;

    /**
     * Validate script syntax without executing.
     *
     * @param code the script code to validate
     * @return true if syntax is valid
     */
    boolean validateSyntax(String code);

    /**
     * Check if this engine is available in the current environment.
     */
    boolean isAvailable();
}
