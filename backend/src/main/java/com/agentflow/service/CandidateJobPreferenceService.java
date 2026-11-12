package com.agentflow.service;

import com.agentflow.dto.job.CandidateJobPreferenceRequest;
import com.agentflow.dto.job.CandidateJobPreferenceResponse;

public interface CandidateJobPreferenceService {

    /**
     * Retrieve job preferences for a candidate profile.
     */
    CandidateJobPreferenceResponse getPreferencesByProfileId(Long profileId);

    /**
     * Create job preferences for a candidate profile (fails if already existing).
     */
    CandidateJobPreferenceResponse createPreferences(Long profileId, CandidateJobPreferenceRequest request);

    /**
     * Update existing job preferences for a candidate profile.
     */
    CandidateJobPreferenceResponse updatePreferences(Long profileId, CandidateJobPreferenceRequest request);

    /**
     * Save or update (upsert) job preferences for a candidate profile.
     */
    CandidateJobPreferenceResponse saveOrUpdatePreferences(Long profileId, CandidateJobPreferenceRequest request);
}
