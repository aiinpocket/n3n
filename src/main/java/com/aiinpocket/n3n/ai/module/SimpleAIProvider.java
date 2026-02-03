package com.aiinpocket.n3n.ai.module;

/**
 * Simplified AI Provider interface for the modular AI assistant system.
 * Supports multiple AI backends: Llamafile, Ollama, OpenAI, Gemini, Claude.
 * This is separate from the main AiProvider interface which is more complex.
 */
public interface SimpleAIProvider {

    /**
     * Get the provider name
     */
    String getName();

    /**
     * Check if the provider is available and configured
     */
    boolean isAvailable();

    /**
     * Generate a chat completion
     * @param prompt The user prompt
     * @param systemPrompt Optional system prompt
     * @param maxTokens Maximum tokens to generate
     * @param temperature Temperature for sampling (0.0-1.0)
     * @return The generated response text
     */
    String chat(String prompt, String systemPrompt, int maxTokens, double temperature);

    /**
     * Generate a chat completion with default parameters
     */
    default String chat(String prompt) {
        return chat(prompt, null, 2048, 0.7);
    }

    /**
     * Generate a chat completion with system prompt
     */
    default String chat(String prompt, String systemPrompt) {
        return chat(prompt, systemPrompt, 2048, 0.7);
    }
}
