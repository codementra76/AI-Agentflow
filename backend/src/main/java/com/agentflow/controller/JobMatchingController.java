package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.JobMatchRequest;
import com.agentflow.dto.job.JobMatchResultResponse;
import com.agentflow.service.JobMatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobMatchingController {

    private final JobMatchingService matchingService;

    public JobMatchingController(JobMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/{jobId:[0-9]+}/match")
    public ResponseEntity<ApiResponse<JobMatchResultResponse>> matchJob(
            @PathVariable Long jobId,
            @RequestBody(required = false) JobMatchRequest request) {
        Long resumeId = (request != null) ? request.resumeId() : null;
        JobMatchResultResponse response = matchingService.matchJobAndResume(jobId, resumeId);
        return ResponseEntity.ok(ApiResponse.success("Resume match analysis completed successfully", response));
    }

    @GetMapping("/{jobId:[0-9]+}/match")
    public ResponseEntity<ApiResponse<JobMatchResultResponse>> getMatchResult(
            @PathVariable Long jobId) {
        JobMatchResultResponse response = matchingService.getStoredMatchResult(jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

