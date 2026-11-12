package com.agentflow.service;

import com.agentflow.dto.job.CandidateProfileRequest;
import com.agentflow.dto.job.CandidateProfileResponse;

public interface CandidateProfileService {

    /**
     * Retrieve the current candidate profile (single-user deployment).
     */
    CandidateProfileResponse getCurrentProfile();

    /**
     * Retrieve a candidate profile by its unique ID.
     */
    CandidateProfileResponse getProfileById(Long id);

    /**
     * Create a new candidate profile.
     */
    CandidateProfileResponse createProfile(CandidateProfileRequest request);

    /**
     * Update an existing candidate profile.
     */
    CandidateProfileResponse updateProfile(Long id, CandidateProfileRequest request);

    /**
     * Delete a candidate profile by ID.
     */
    void deleteProfile(Long id);
}
