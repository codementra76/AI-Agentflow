package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.JobApplicationCountsResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobApplicationUpdateRequest;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.service.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/applications")
public class JobApplicationController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
        "appliedDate", "followUpDate", "createdAt", "updatedAt", "status", "matchScore"
    );

    private final JobApplicationService applicationService;

    public JobApplicationController(JobApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobApplicationResponse>>> getApplications(
            @RequestParam(required = false) JobApplicationStatus status,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "appliedDate,desc") String sort) {

        Pageable pageable = createPageable(page, size, sort, "appliedDate");
        Page<JobApplicationResponse> applications = applicationService.getApplications(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<JobApplicationCountsResponse>> getApplicationStats() {
        JobApplicationCountsResponse counts = applicationService.getApplicationCounts();
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/follow-ups")
    public ResponseEntity<ApiResponse<List<JobApplicationResponse>>> getDueFollowUps(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        LocalDate targetDate = through != null ? through : LocalDate.now();
        List<JobApplicationResponse> due = applicationService.getDueFollowUps(targetDate);
        return ResponseEntity.ok(ApiResponse.success(due));
    }

    @GetMapping("/by-job/{jobId:[0-9]+}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> getApplicationByJobId(@PathVariable Long jobId) {
        JobApplicationResponse app = applicationService.getApplicationByJobId(jobId);
        return ResponseEntity.ok(ApiResponse.success(app));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> getApplicationById(@PathVariable Long id) {
        JobApplicationResponse app = applicationService.getApplicationById(id);
        return ResponseEntity.ok(ApiResponse.success(app));
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationUpdateRequest request) {
        JobApplicationResponse updated = applicationService.updateApplication(id, request);
        return ResponseEntity.ok(ApiResponse.success("Application updated successfully", updated));
    }

    private Pageable createPageable(int page, int size, String sortParam, String defaultProperty) {
        int pageNum = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), MAX_SIZE);

        String property = defaultProperty;
        Sort.Direction direction = Sort.Direction.DESC;

        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String candidateProp = parts[0].trim();
            if (ALLOWED_SORT_PROPERTIES.contains(candidateProp)) {
                property = candidateProp;
            }
            if (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")) {
                direction = Sort.Direction.ASC;
            }
        }

        return PageRequest.of(pageNum, pageSize, Sort.by(direction, property));
    }
}
