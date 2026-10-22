package com.agentflow.service;

import com.agentflow.dto.ai.AiChatResponse;

public interface OllamaService {

    /**
     * Send a prompt to Ollama using the default configured model.
     *
     * @param prompt User prompt to process.
     * @return Generated AI response and model name.
     */
    default AiChatResponse generateChat(String prompt) {
        return generateChat(prompt, null);
    }

    /**
     * Send a prompt to Ollama with an optional model override.
     *
     * @param prompt User prompt to process.
     * @param modelOverride Optional model name override (defaults to configured model if null or blank).
     * @return Generated AI response and model name.
     */
    AiChatResponse generateChat(String prompt, String modelOverride);

    /**
     * Retrieve the currently configured default model.
     */
    String getActiveModel();

    /**
     * Retrieve the currently configured base URL.
     */
    String getBaseUrl();
}
