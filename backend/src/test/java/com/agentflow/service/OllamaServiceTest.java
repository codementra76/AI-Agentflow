package com.agentflow.service;

import com.agentflow.config.OllamaProperties;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.service.impl.OllamaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaServiceTest {

    private OllamaProperties properties;
    private MockRestServiceServer mockServer;
    private OllamaService ollamaService;

    @BeforeEach
    void setUp() {
        properties = new OllamaProperties();
        properties.setBaseUrl("http://localhost:11434");
        properties.setModel("qwen2.5:3b");
        properties.setTimeoutSeconds(60);

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ollamaService = new OllamaServiceImpl(restClient, properties);
    }

    @Test
    @DisplayName("Successfully generate chat response from Ollama")
    void shouldSuccessfullyGenerateChatResponse() {
        String mockResponseJson = """
            {
              "model": "qwen2.5:3b",
              "created_at": "2026-10-22T10:00:00Z",
              "message": {
                "role": "assistant",
                "content": "Spring Boot makes microservices simple."
              },
              "done": true,
              "total_duration": 1500000000
            }
            """;

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
            .andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.messages[0].content").value("Explain Spring Boot"))
            .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiChatResponse result = ollamaService.generateChat("Explain Spring Boot");

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.response()).isEqualTo("Spring Boot makes microservices simple.");
        assertThat(result.model()).isEqualTo("qwen2.5:3b");
    }

    @Test
    @DisplayName("Support model override in chat request")
    void shouldSupportModelOverride() {
        String mockResponseJson = """
            {
              "model": "llama3:latest",
              "message": {
                "role": "assistant",
                "content": "Custom model response"
              },
              "done": true
            }
            """;

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("llama3:latest"))
            .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiChatResponse result = ollamaService.generateChat("Hello", "llama3:latest");

        mockServer.verify();
        assertThat(result.response()).isEqualTo("Custom model response");
        assertThat(result.model()).isEqualTo("llama3:latest");
    }

    @Test
    @DisplayName("Fail validation on null prompt")
    void shouldFailOnNullPrompt() {
        assertThatThrownBy(() -> ollamaService.generateChat(null))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Prompt must not be blank");
    }

    @Test
    @DisplayName("Fail validation on blank prompt")
    void shouldFailOnBlankPrompt() {
        assertThatThrownBy(() -> ollamaService.generateChat("   "))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Prompt must not be blank");
    }

    @Test
    @DisplayName("Fail validation on prompt exceeding maximum allowed length")
    void shouldFailOnExcessivelyLongPrompt() {
        String hugePrompt = "A".repeat(20001);
        assertThatThrownBy(() -> ollamaService.generateChat(hugePrompt))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Prompt exceeds maximum length");
    }

    @Test
    @DisplayName("Throw AiServiceUnavailableException on HTTP 500 error from Ollama")
    void shouldHandleOllama500Error() {
        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        assertThatThrownBy(() -> ollamaService.generateChat("Valid prompt"))
            .isInstanceOf(AiServiceUnavailableException.class)
            .hasMessageContaining("Ollama service returned error 500");

        mockServer.verify();
    }

    @Test
    @DisplayName("Throw AiServiceUnavailableException on empty response from Ollama")
    void shouldHandleEmptyResponseFromOllama() {
        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ollamaService.generateChat("Valid prompt"))
            .isInstanceOf(AiServiceUnavailableException.class)
            .hasMessageContaining("empty or invalid response");

        mockServer.verify();
    }

    @Test
    @DisplayName("Fallback to configured default model when override is 'default'")
    void shouldFallbackToConfiguredModelWhenOverrideIsDefault() {
        String mockResponseJson = """
            {
              "model": "qwen2.5:3b",
              "message": {
                "role": "assistant",
                "content": "Answer from default model."
              },
              "done": true
            }
            """;

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
            .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        AiChatResponse result = ollamaService.generateChat("Valid prompt", "default");

        mockServer.verify();
        assertThat(result.model()).isEqualTo("qwen2.5:3b");
    }
}
