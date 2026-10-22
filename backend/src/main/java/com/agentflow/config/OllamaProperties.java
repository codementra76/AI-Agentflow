package com.agentflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /**
     * Base URL for the local Ollama API (e.g., http://localhost:11434).
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Default model name (e.g., qwen2.5:3b).
     */
    private String model = "qwen2.5:3b";

    /**
     * Request timeout in seconds for LLM generation.
     */
    private int timeoutSeconds = 60;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
