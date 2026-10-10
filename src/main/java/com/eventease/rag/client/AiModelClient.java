package com.eventease.rag.client;

/**
 * Interface abstraction for generative AI model inference clients.
 */
public interface AiModelClient {

    /**
     * Generates a grounded response given a system instruction and user prompt.
     *
     * @param systemInstruction The grounding rules and role instructions for the model
     * @param userPrompt The assembled prompt containing the user question and delimited evidence
     * @return AiModelResponse containing the generated text or a safe failure description
     */
    AiModelResponse generateAnswer(String systemInstruction, String userPrompt);

    /**
     * Checks whether the client is properly configured with an API key and enabled.
     */
    boolean isConfigured();

    /**
     * Returns the configured model identifier (e.g., "gemini-3.8-flash").
     */
    String getModelName();
}
