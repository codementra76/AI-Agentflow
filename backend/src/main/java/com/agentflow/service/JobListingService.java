package com.agentflow.service;

import com.agentflow.dto.job.JobListingCreateRequest;
import com.agentflow.dto.job.JobListingResponse;
import com.agentflow.dto.job.JobListingSaveResult;
import com.agentflow.dto.job.JobListingUpdateRequest;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.WorkplaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface JobListingService {

    /**
     * Create a job listing or detect existing duplicates.
     * When a duplicate is detected, existing application tracking notes, dates, and status are preserved.
     */
    JobListingSaveResult createJobListing(JobListingCreateRequest request);

    /**
     * Retrieve a job listing by its unique ID.
     */
    JobListingResponse getJobListingById(Long id);

    /**
     * Search and filter job listings with keyword, workplace type, employment type, and pagination.
     */
    Page<JobListingResponse> searchJobListings(String search, WorkplaceType workplaceType,
                                              EmploymentType employmentType, Pageable pageable);

    /**
     * Update an existing job listing's details while preserving its application tracking data.
     */
    JobListingResponse updateJobListing(Long id, JobListingUpdateRequest request);

    /**
     * Delete a job listing by ID (cascades to application tracking record).
     */
    void deleteJobListing(Long id);

    /**
     * Find a job listing by its deterministic 64-character SHA-256 content hash.
     */
    Optional<JobListingResponse> findByContentHash(String contentHash);

    /**
     * Find a job listing by its canonical source URL.
     */
    Optional<JobListingResponse> findBySourceUrl(String sourceUrl);
}
