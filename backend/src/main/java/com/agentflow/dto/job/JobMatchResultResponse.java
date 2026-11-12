package com.agentflow.dto.job;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record JobMatchResultResponse(
    Long applicationId,
    Long jobId,
    String jobTitle,
    String jobCompany,
    Long resumeId,
    String resumeVersionName,
    BigDecimal matchScore,
    JsonNode matchBreakdown,
    Instant evaluatedAt,
    boolean isFallback
) {}

