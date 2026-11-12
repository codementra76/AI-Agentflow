package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.CandidateJobPreferenceRequest;
import com.agentflow.dto.job.CandidateJobPreferenceResponse;
import com.agentflow.service.CandidateJobPreferenceService;
import com.agentflow.service.CandidateProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidate-profile/preferences")
public class CandidateJobPreferenceController {

    private final CandidateJobPreferenceService preferenceService;
    private final CandidateProfileService profileService;

    public CandidateJobPreferenceController(CandidateJobPreferenceService preferenceService,
                                            CandidateProfileService profileService) {
        this.preferenceService = preferenceService;
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CandidateJobPreferenceResponse>> getPreferences(
            @RequestParam(required = false) Long profileId) {
        Long targetProfileId = resolveProfileId(profileId);
        CandidateJobPreferenceResponse preferences = preferenceService.getPreferencesByProfileId(targetProfileId);
        return ResponseEntity.ok(ApiResponse.success(preferences));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CandidateJobPreferenceResponse>> saveOrUpdatePreferences(
            @RequestParam(required = false) Long profileId,
            @Valid @RequestBody CandidateJobPreferenceRequest request) {
        Long targetProfileId = resolveProfileId(profileId);
        CandidateJobPreferenceResponse saved = preferenceService.saveOrUpdatePreferences(targetProfileId, request);
        return ResponseEntity.ok(ApiResponse.success("Job preferences saved successfully", saved));
    }

    private Long resolveProfileId(Long profileId) {
        if (profileId != null) {
            return profileId;
        }
        return profileService.getCurrentProfile().id();
    }
}
