package com.agentflow.service.impl;

import com.agentflow.config.OllamaProperties;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.dto.ai.OllamaChatRequest;
import com.agentflow.dto.ai.OllamaChatResponse;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Service
public class OllamaServiceImpl implements OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaServiceImpl.class);
    private static final int MAX_PROMPT_LENGTH = 20000;

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaServiceImpl(RestClient restClient, OllamaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AiChatResponse generateChat(String prompt, String modelOverride) {
        validatePrompt(prompt);

        String resolvedModel = (modelOverride != null && !modelOverride.trim().isEmpty() && !"default".equalsIgnoreCase(modelOverride.trim()))
            ? modelOverride.trim()
            : properties.getModel();

        String trimmedPrompt = prompt.trim();
        log.info("Sending chat request to Ollama [model: {}, promptLength: {} chars]", resolvedModel, trimmedPrompt.length());

        OllamaChatRequest requestPayload = OllamaChatRequest.userPrompt(resolvedModel, trimmedPrompt);

        try {
            OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(requestPayload)
                .retrieve()
                .body(OllamaChatResponse.class);

            if (response == null || response.message() == null || response.message().content() == null) {
                log.warn("Ollama returned an empty response for model [{}]", resolvedModel);
                throw new AiServiceUnavailableException("Ollama service returned an empty or invalid response.");
            }

            String generatedText = response.getGeneratedText();
            log.info("Received Ollama response [model: {}, responseLength: {} chars]", resolvedModel, generatedText.length());

            return new AiChatResponse(generatedText, resolvedModel);

        } catch (ResourceAccessException ex) {
            Throwable cause = ex.getCause();
            if (isTimeout(ex, cause)) {
                String timeoutMsg = String.format(
                    "Ollama request timed out after %d seconds while generating a response. Try a shorter prompt or verify model performance.",
                    properties.getTimeoutSeconds()
                );
                log.warn("Ollama request timed out for model [{}]: {}", resolvedModel, ex.getMessage());
                throw new AiServiceUnavailableException(timeoutMsg, ex);
            }

            if (isConnectionRefused(ex, cause)) {
                String connectMsg = String.format(
                    "Cannot connect to local Ollama service at %s. Please verify Ollama is running ('ollama serve') and accessible.",
                    properties.getBaseUrl()
                );
                log.warn("Cannot connect to Ollama at [{}]: {}", properties.getBaseUrl(), ex.getMessage());
                throw new AiServiceUnavailableException(connectMsg, ex);
            }

            String generalMsg = String.format(
                "Failed to communicate with local Ollama service at %s: %s",
                properties.getBaseUrl(),
                cause != null ? cause.getMessage() : ex.getMessage()
            );
            log.warn("Communication failure with Ollama: {}", ex.getMessage());
            throw new AiServiceUnavailableException(generalMsg, ex);

        } catch (RestClientResponseException ex) {
            String httpMsg = String.format(
                "Ollama service returned error %d (%s).",
                ex.getStatusCode().value(),
                ex.getStatusText()
            );
            log.warn("Ollama HTTP error [status: {}]: {}", ex.getStatusCode(), ex.getMessage());
            throw new AiServiceUnavailableException(httpMsg, ex);

        } catch (RestClientException ex) {
            String errorMsg = "Error while communicating with Ollama service: " + ex.getMessage();
            log.warn("Ollama client error: {}", ex.getMessage());
            throw new AiServiceUnavailableException(errorMsg, ex);
        }
    }

    @Override
    public String getActiveModel() {
        return properties.getModel();
    }

    @Override
    public String getBaseUrl() {
        return properties.getBaseUrl();
    }

    private void validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new AgentFlowException("Prompt must not be blank");
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new AgentFlowException(String.format("Prompt exceeds maximum length of %d characters", MAX_PROMPT_LENGTH));
        }
    }

    private boolean isTimeout(ResourceAccessException ex, Throwable cause) {
        if (cause instanceof SocketTimeoutException) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && (msg.toLowerCase().contains("timed out") || msg.toLowerCase().contains("timeout"));
    }

    private boolean isConnectionRefused(ResourceAccessException ex, Throwable cause) {
        if (cause instanceof ConnectException) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && (msg.toLowerCase().contains("connection refused") || msg.toLowerCase().contains("failed to connect"));
    }
}
