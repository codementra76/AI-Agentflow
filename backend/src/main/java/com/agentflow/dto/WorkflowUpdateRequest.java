package com.agentflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowUpdateRequest(
    @NotBlank(message = "Workflow name is required")
    @Size(min = 2, max = 150, message = "Workflow name must be between 2 and 150 characters")
    String name,

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    String description,

    JsonNode workflowDefinition,

    Boolean active
) {}
