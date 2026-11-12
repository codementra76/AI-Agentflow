package com.agentflow.dto.job;

public record JobApplicationCountsResponse(
    long saved,
    long applied,
    long interviewing,
    long offered,
    long rejected,
    long archived,
    long total
) {}
