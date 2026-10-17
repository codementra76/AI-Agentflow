package com.agentflow.dto;

import jakarta.validation.constraints.NotNull;

public record WorkflowActivePatchRequest(
    @NotNull(message = "The 'active' field is required")
    Boolean active
) {}
