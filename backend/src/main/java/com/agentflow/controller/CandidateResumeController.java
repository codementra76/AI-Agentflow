package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.CandidateResumeCreateRequest;
import com.agentflow.dto.job.CandidateResumeResponse;
import com.agentflow.dto.job.CandidateResumeUpdateRequest;
import com.agentflow.service.CandidateProfileService;
import com.agentflow.service.CandidateResumeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/candidate-profile/resumes")
public class CandidateResumeController {

    private final CandidateResumeService resumeService;
    private final CandidateProfileService profileService;

    public CandidateResumeController(CandidateResumeService resumeService,
            CandidateProfileService profileService) {
        this.resumeService = resumeService;
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CandidateResumeResponse>>> listResumes(
            @RequestParam(required = false) Long profileId) {
        Long targetProfileId = resolveProfileId(profileId);
        List<CandidateResumeResponse> resumes = resumeService.getResumesByProfileId(targetProfileId);
        return ResponseEntity.ok(ApiResponse.success(resumes));
    }

    @GetMapping("/primary")
    public ResponseEntity<ApiResponse<CandidateResumeResponse>> getPrimaryResume(
            @RequestParam(required = false) Long profileId) {
        Long targetProfileId = resolveProfileId(profileId);
        CandidateResumeResponse primary = resumeService.getPrimaryResume(targetProfileId);
        return ResponseEntity.ok(ApiResponse.success(primary));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<CandidateResumeResponse>> getResumeById(@PathVariable Long id) {
        CandidateResumeResponse resume = resumeService.getResumeById(id);
        return ResponseEntity.ok(ApiResponse.success(resume));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CandidateResumeResponse>> createResume(
            @RequestParam(required = false) Long profileId,
            @Valid @RequestBody CandidateResumeCreateRequest request) {
        Long targetProfileId = resolveProfileId(profileId);
        CandidateResumeResponse created = resumeService.createResume(targetProfileId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resume version created successfully", created));
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<CandidateResumeResponse>> updateResume(
            @PathVariable Long id,
            @Valid @RequestBody CandidateResumeUpdateRequest request) {
        CandidateResumeResponse updated = resumeService.updateResume(id, request);
        return ResponseEntity.ok(ApiResponse.success("Resume version updated successfully", updated));
    }

    @PutMapping("/{id:[0-9]+}/primary")
    public ResponseEntity<ApiResponse<CandidateResumeResponse>> setPrimaryResume(@PathVariable Long id) {
        CandidateResumeResponse primary = resumeService.setPrimaryResume(id);
        return ResponseEntity.ok(ApiResponse.success("Primary resume updated successfully", primary));
    }

    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<Void> deleteResume(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveProfileId(Long profileId) {
        if (profileId != null) {
            return profileId;
        }
        return profileService.getCurrentProfile().id();
    }
}
