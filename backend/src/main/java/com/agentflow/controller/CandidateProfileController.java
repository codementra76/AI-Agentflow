package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.CandidateProfileRequest;
import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.service.CandidateProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidate-profile")
public class CandidateProfileController {

    private final CandidateProfileService profileService;

    public CandidateProfileController(CandidateProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getCurrentProfile() {
        CandidateProfileResponse profile = profileService.getCurrentProfile();
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getProfileById(@PathVariable Long id) {
        CandidateProfileResponse profile = profileService.getProfileById(id);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> createProfile(
            @Valid @RequestBody CandidateProfileRequest request) {
        CandidateProfileResponse created = profileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Candidate profile created successfully", created));
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody CandidateProfileRequest request) {
        CandidateProfileResponse updated = profileService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate profile updated successfully", updated));
    }
}
