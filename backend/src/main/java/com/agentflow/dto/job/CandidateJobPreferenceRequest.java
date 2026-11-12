package com.agentflow.dto.job;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CandidateJobPreferenceRequest(
    List<String> targetTitles,

    List<String> preferredLocations,

    List<String> workplaceTypes,

    List<String> employmentTypes,

    @PositiveOrZero(message = "Minimum salary must be greater than or equal to zero")
    BigDecimal minSalary,

    @Size(max = 10, message = "Currency code cannot exceed 10 characters")
    String currency
) {}
