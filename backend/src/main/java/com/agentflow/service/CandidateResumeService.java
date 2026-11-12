package com.agentflow.service;

import com.agentflow.dto.job.CandidateResumeCreateRequest;
import com.agentflow.dto.job.CandidateResumeResponse;
import com.agentflow.dto.job.CandidateResumeUpdateRequest;

import java.util.List;

public interface CandidateResumeService {

    /**
     * Create a new resume version for an existing candidate profile.
     */
    CandidateResumeResponse createResume(Long profileId, CandidateResumeCreateRequest request);

    /**
     * List all resume versions for a candidate profile in newest-first order.
     */
    List<CandidateResumeResponse> getResumesByProfileId(Long profileId);

    /**
     * Retrieve a resume version by its ID.
     */
    CandidateResumeResponse getResumeById(Long id);

    /**
     * Update an existing resume version.
     */
    CandidateResumeResponse updateResume(Long id, CandidateResumeUpdateRequest request);

    /**
     * Designate a specific resume version as the primary resume for its candidate profile.
     */
    CandidateResumeResponse setPrimaryResume(Long id);

    /**
     * Delete a resume version.
     */
    void deleteResume(Long id);

    /**
     * Retrieve the current primary resume for a candidate profile.
     */
    CandidateResumeResponse getPrimaryResume(Long profileId);
}
