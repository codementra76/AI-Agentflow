package com.agentflow.service.impl;

import com.agentflow.dto.job.JobApplicationCountsResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobApplicationUpdateRequest;
import com.agentflow.entity.CandidateResume;
import com.agentflow.entity.JobApplication;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateResumeRepository;
import com.agentflow.repository.JobApplicationRepository;
import com.agentflow.service.JobApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobApplicationServiceImpl implements JobApplicationService {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationServiceImpl.class);

    private final JobApplicationRepository applicationRepository;
    private final CandidateResumeRepository resumeRepository;

    public JobApplicationServiceImpl(JobApplicationRepository applicationRepository,
                                     CandidateResumeRepository resumeRepository) {
        this.applicationRepository = applicationRepository;
        this.resumeRepository = resumeRepository;
    }

    @Override
    public JobApplicationResponse getApplicationById(Long id) {
        JobApplication application = findApplicationOrThrow(id);
        return JobApplicationResponse.fromEntity(application);
    }

    @Override
    public JobApplicationResponse getApplicationByJobId(Long jobId) {
        JobApplication application = applicationRepository.findByJobId(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("No application found for job listing id: " + jobId));
        return JobApplicationResponse.fromEntity(application);
    }

    @Override
    public Page<JobApplicationResponse> getApplications(JobApplicationStatus status, Pageable pageable) {
        return applicationRepository.findByOptionalStatus(status, pageable)
            .map(JobApplicationResponse::fromEntity);
    }

    @Override
    public List<JobApplicationResponse> getApplicationsByStatus(JobApplicationStatus status) {
        if (status == null) {
            throw new AgentFlowException("Status cannot be null");
        }
        return applicationRepository.findByStatusOrderByCreatedAtDesc(status)
            .stream()
            .map(JobApplicationResponse::fromEntity)
            .toList();
    }

    @Override
    @Transactional
    public JobApplicationResponse updateApplicationStatus(Long id, JobApplicationStatus newStatus) {
        if (newStatus == null) {
            throw new AgentFlowException("New status cannot be null");
        }

        JobApplication application = findApplicationOrThrow(id);
        application.setStatus(newStatus);

        // If transitioning to APPLIED and appliedDate is unset, record today as applied date
        if (newStatus == JobApplicationStatus.APPLIED && application.getAppliedDate() == null) {
            application.setAppliedDate(LocalDate.now());
        }

        JobApplication updated = applicationRepository.save(application);
        log.info("Updated status for application [id: {}] to [{}]", id, newStatus);

        return JobApplicationResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public JobApplicationResponse updateApplication(Long id, JobApplicationUpdateRequest request) {
        if (request == null) {
            throw new AgentFlowException("Application update request cannot be null");
        }
        if (request.status() == null) {
            throw new AgentFlowException("Application status is required");
        }

        JobApplication application = findApplicationOrThrow(id);

        validateMatchScore(request.matchScore());

        application.setStatus(request.status());
        application.setAppliedDate(request.appliedDate());
        application.setFollowUpDate(request.followUpDate());
        application.setNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);

        if (request.resumeId() != null) {
            CandidateResume resume = resumeRepository.findById(request.resumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + request.resumeId()));
            application.setResume(resume);
        } else {
            application.setResume(null);
        }

        if (request.matchScore() != null) {
            application.setMatchScore(request.matchScore());
        }
        if (request.matchBreakdown() != null) {
            application.setMatchBreakdown(request.matchBreakdown());
        }

        JobApplication updated = applicationRepository.save(application);
        log.info("Updated application details [id: {}, status: {}]", id, request.status());

        return JobApplicationResponse.fromEntity(updated);
    }

    @Override
    public JobApplicationCountsResponse getApplicationCounts() {
        long saved = applicationRepository.countByStatus(JobApplicationStatus.SAVED);
        long applied = applicationRepository.countByStatus(JobApplicationStatus.APPLIED);
        long interviewing = applicationRepository.countByStatus(JobApplicationStatus.INTERVIEWING);
        long offered = applicationRepository.countByStatus(JobApplicationStatus.OFFERED);
        long rejected = applicationRepository.countByStatus(JobApplicationStatus.REJECTED);
        long archived = applicationRepository.countByStatus(JobApplicationStatus.ARCHIVED);
        long total = saved + applied + interviewing + offered + rejected + archived;

        return new JobApplicationCountsResponse(
            saved,
            applied,
            interviewing,
            offered,
            rejected,
            archived,
            total
        );
    }

    @Override
    public List<JobApplicationResponse> getDueFollowUps(LocalDate upToDate) {
        LocalDate targetDate = (upToDate != null) ? upToDate : LocalDate.now();
        List<JobApplicationStatus> activeStatuses = List.of(
            JobApplicationStatus.SAVED,
            JobApplicationStatus.APPLIED,
            JobApplicationStatus.INTERVIEWING
        );

        return applicationRepository.findByFollowUpDateLessThanEqualAndStatusIn(targetDate, activeStatuses)
            .stream()
            .map(JobApplicationResponse::fromEntity)
            .toList();
    }

    @Override
    @Transactional
    public JobApplicationResponse updateMatchResult(Long applicationId, Long resumeId, BigDecimal matchScore, com.fasterxml.jackson.databind.JsonNode matchBreakdown) {
        JobApplication application = findApplicationOrThrow(applicationId);
        validateMatchScore(matchScore);

        application.setMatchScore(matchScore);
        application.setMatchBreakdown(matchBreakdown);

        if (resumeId != null) {
            CandidateResume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + resumeId));
            application.setResume(resume);
        }

        JobApplication updated = applicationRepository.save(application);
        log.info("Dedicated persistence updated match result for application [id: {}, score: {}]", applicationId, matchScore);

        return JobApplicationResponse.fromEntity(updated);
    }

    private JobApplication findApplicationOrThrow(Long id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job application not found with id: " + id));
    }

    private void validateMatchScore(BigDecimal matchScore) {
        if (matchScore == null) {
            return;
        }
        if (matchScore.compareTo(BigDecimal.ZERO) < 0 || matchScore.compareTo(new BigDecimal("100.00")) > 0) {
            throw new AgentFlowException("Match score must be between 0.00 and 100.00");
        }
    }
}
