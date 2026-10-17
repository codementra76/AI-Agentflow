package com.agentflow.dto;

public record HealthStatusResponse(
    String status,
    String service,
    long uptimeMillis
) {}
