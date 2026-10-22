package com.agentflow.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
    @NotBlank(message = "Prompt must not be blank")
    @Size(max = 20000, message = "Prompt exceeds maximum allowed length of 20000 characters")
    String prompt,

    String model
) {
    public AiChatRequest(String prompt) {
        this(prompt, null);
    }
}
