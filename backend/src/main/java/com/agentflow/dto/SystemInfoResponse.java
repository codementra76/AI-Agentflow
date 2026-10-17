package com.agentflow.dto;

public record SystemInfoResponse(
    String platform,
    String version,
    String runtime,
    String status,
    String description
) {}
