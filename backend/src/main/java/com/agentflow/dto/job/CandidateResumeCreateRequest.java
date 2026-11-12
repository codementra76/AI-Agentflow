package com.agentflow.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateResumeCreateRequest(
    @NotBlank(message = "Resume version name is required")
    @Size(max = 100, message = "Version name cannot exceed 100 characters")
    String versionName,

    @NotBlank(message = "Resume content cannot be empty")
    String rawText,

    Boolean isPrimary,

    JsonNode parsedSkills
) {}
