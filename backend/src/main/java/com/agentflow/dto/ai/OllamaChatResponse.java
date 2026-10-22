package com.agentflow.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
    String model,
    @JsonProperty("created_at") String createdAt,
    OllamaMessage message,
    Boolean done,
    @JsonProperty("total_duration") Long totalDuration
) {
    public String getGeneratedText() {
        return message != null && message.content() != null ? message.content() : "";
    }
}
