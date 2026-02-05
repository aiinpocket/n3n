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

    /**
     * Generate a chat completion from a list of messages
     * @param messages List of messages with role and content
     * @param model Model to use for generation
     * @return The generated response text
     */
    default String chat(java.util.List<java.util.Map<String, String>> messages, String model) {
        // Default implementation: extract last user message
        String prompt = messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .reduce((first, second) -> second)
                .map(m -> m.get("content"))
                .orElse("");

        String systemPrompt = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst()
                .orElse(null);

        return chat(prompt, systemPrompt);
    }

    /**
     * Generate embedding for text
     * @param text Text to embed
     * @return Embedding vector (typically 1536 dimensions for OpenAI)
     */
    default float[] getEmbedding(String text) {
        // Default implementation returns empty array
        // Providers that support embeddings should override this
        throw new UnsupportedOperationException("This provider does not support embeddings");
    }

    /**
     * Check if this provider supports embeddings
     */
    default boolean supportsEmbeddings() {
        return false;
    }
}
