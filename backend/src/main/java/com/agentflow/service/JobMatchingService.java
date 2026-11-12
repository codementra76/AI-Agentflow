package com.agentflow.service;

import com.agentflow.dto.job.JobMatchResultResponse;

public interface JobMatchingService {

    /**
     * Analyze and evaluate resume fit against a job listing using deterministic skill matching
     * and local Ollama AI contextual analysis, then persist match results safely.
     *
     * @param jobId ID of the target job listing.
     * @param resumeId Optional ID of the resume version to evaluate (defaults to primary if null).
     * @return Full match analysis response.
     */
    JobMatchResultResponse matchJobAndResume(Long jobId, Long resumeId);

    /**
     * Retrieve the previously saved match result for the active candidate profile's application.
     *
     * @param jobId ID of the job listing.
     * @return Stored match analysis result.
     */
    JobMatchResultResponse getStoredMatchResult(Long jobId);
}

