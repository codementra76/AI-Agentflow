package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.ai.AiChatRequest;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.service.OllamaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final OllamaService ollamaService;

    public AiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = ollamaService.generateChat(request.prompt(), request.model());
        return ResponseEntity.ok(ApiResponse.success("AI response generated successfully", response));
    }
}
