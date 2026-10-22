package com.agentflow.controller;

import com.agentflow.dto.ai.AiChatRequest;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.service.OllamaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OllamaService ollamaService;

    @Test
    @DisplayName("POST /api/v1/ai/chat returns generated response when prompt is valid")
    void shouldGenerateAiChatResponse() throws Exception {
        AiChatRequest request = new AiChatRequest("Explain Spring Boot in simple terms.");
        AiChatResponse response = new AiChatResponse(
            "Spring Boot is an open-source Java-based framework used to create microservices.",
            "qwen2.5:3b"
        );

        when(ollamaService.generateChat(eq("Explain Spring Boot in simple terms."), eq(null)))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("AI response generated successfully"))
            .andExpect(jsonPath("$.data.response").value("Spring Boot is an open-source Java-based framework used to create microservices."))
            .andExpect(jsonPath("$.data.model").value("qwen2.5:3b"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat supports optional model override")
    void shouldSupportModelOverride() throws Exception {
        AiChatRequest request = new AiChatRequest("Hello world", "custom-model");
        AiChatResponse response = new AiChatResponse("Custom reply", "custom-model");

        when(ollamaService.generateChat(eq("Hello world"), eq("custom-model")))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.response").value("Custom reply"))
            .andExpect(jsonPath("$.data.model").value("custom-model"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat returns 400 Bad Request when prompt is blank")
    void shouldReturnBadRequestWhenPromptIsBlank() throws Exception {
        AiChatRequest request = new AiChatRequest("   ");

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.validationErrors.prompt").value("Prompt must not be blank"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat returns 400 Bad Request when body is empty")
    void shouldReturnBadRequestWhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.prompt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/ai/chat returns 503 Service Unavailable when Ollama is unreachable")
    void shouldReturnServiceUnavailableWhenOllamaIsDown() throws Exception {
        AiChatRequest request = new AiChatRequest("Explain Spring Boot");

        when(ollamaService.generateChat(eq("Explain Spring Boot"), eq(null)))
            .thenThrow(new AiServiceUnavailableException(
                "Cannot connect to local Ollama service at http://localhost:11434. Please verify Ollama is running ('ollama serve') and accessible."
            ));

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.error").value("Service Unavailable"))
            .andExpect(jsonPath("$.message").value(
                "Cannot connect to local Ollama service at http://localhost:11434. Please verify Ollama is running ('ollama serve') and accessible."
            ));
    }
}
