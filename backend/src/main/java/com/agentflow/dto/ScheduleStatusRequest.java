package com.agentflow.dto;

import jakarta.validation.constraints.NotNull;

public record ScheduleStatusRequest(
    @NotNull(message = "The 'enabled' field is required")
    Boolean enabled
) {}
