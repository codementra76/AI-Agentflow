package com.agentflow.service;

import com.agentflow.dto.job.JobApplicationCountsResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobApplicationUpdateRequest;
import com.agentflow.entity.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface JobApplicationService {

    /**
     * Retrieve an application tracking record by its ID.
     */
    JobApplicationResponse getApplicationById(Long id);

    /**
     * Retrieve the application tracking record associated with a job listing.
     */
    JobApplicationResponse getApplicationByJobId(Long jobId);

    /**
     * List application tracking records with optional status filter and pagination.
     */
    Page<JobApplicationResponse> getApplications(JobApplicationStatus status, Pageable pageable);

    /**
     * List application tracking records by exact status.
     */
    List<JobApplicationResponse> getApplicationsByStatus(JobApplicationStatus status);

    /**
     * Quick status update for an application.
     */
    JobApplicationResponse updateApplicationStatus(Long id, JobApplicationStatus newStatus);

    /**
     * Update full application details (status, applied date, follow-up date, notes, resume reference, match score).
     */
    JobApplicationResponse updateApplication(Long id, JobApplicationUpdateRequest request);

    /**
     * Aggregate application counts across all statuses for dashboard metrics.
     */
    JobApplicationCountsResponse getApplicationCounts();

    /**
     * Retrieve applications with due or overdue follow-up dates.
     */
    List<JobApplicationResponse> getDueFollowUps(LocalDate upToDate);

    /**
     * Dedicated persistence method to update match score, match breakdown, and resume association
     * without modifying application status, appliedDate, followUpDate, or user notes.
     */
    JobApplicationResponse updateMatchResult(Long applicationId, Long resumeId, java.math.BigDecimal matchScore, com.fasterxml.jackson.databind.JsonNode matchBreakdown);
}
