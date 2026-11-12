package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.job.JobListingCreateRequest;
import com.agentflow.dto.job.JobListingResponse;
import com.agentflow.dto.job.JobListingSaveResult;
import com.agentflow.dto.job.JobListingUpdateRequest;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.WorkplaceType;
import com.agentflow.service.JobListingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobListingController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "createdAt", "updatedAt", "title", "company", "location");

    private final JobListingService listingService;

    public JobListingController(JobListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobListingResponse>>> searchJobs(
            @RequestParam(required = false, name = "search") String search,
            @RequestParam(required = false) WorkplaceType workplaceType,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        Pageable pageable = createPageable(page, size, sort, "createdAt");
        Page<JobListingResponse> listings = listingService.searchJobListings(search, workplaceType, employmentType,
                pageable);
        return ResponseEntity.ok(ApiResponse.success(listings));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<JobListingResponse>> getJobById(@PathVariable Long id) {
        JobListingResponse listing = listingService.getJobListingById(id);
        return ResponseEntity.ok(ApiResponse.success(listing));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobListingSaveResult>> createJob(
            @Valid @RequestBody JobListingCreateRequest request) {
        JobListingSaveResult result = listingService.createJobListing(request);
        if (result.isDuplicate()) {
            return ResponseEntity.ok(ApiResponse.success(result.message(), result));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.message(), result));
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<JobListingResponse>> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobListingUpdateRequest request) {
        JobListingResponse updated = listingService.updateJobListing(id, request);
        return ResponseEntity.ok(ApiResponse.success("Job listing updated successfully", updated));
    }

    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        listingService.deleteJobListing(id);
        return ResponseEntity.noContent().build();
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
