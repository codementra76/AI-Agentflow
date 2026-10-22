package com.agentflow.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
    String model,
    List<OllamaMessage> messages,
    boolean stream
) {
    public static OllamaChatRequest userPrompt(String model, String prompt) {
        return new OllamaChatRequest(
            model,
            List.of(new OllamaMessage("user", prompt)),
            false
        );
    }

    public static OllamaChatRequest withSystem(String model, String systemMessage, String prompt) {
        return new OllamaChatRequest(
            model,
            List.of(
                new OllamaMessage("system", systemMessage),
                new OllamaMessage("user", prompt)
            ),
            false
        );
    }
}
