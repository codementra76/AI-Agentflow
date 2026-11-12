package com.agentflow.dto.job;

import com.agentflow.entity.JobApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JobApplicationUpdateRequest(
    @NotNull(message = "Application status is required")
    JobApplicationStatus status,

    LocalDate appliedDate,

    LocalDate followUpDate,

    String notes,

    Long resumeId,

    BigDecimal matchScore,

    JsonNode matchBreakdown
) {}
