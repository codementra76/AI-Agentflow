package com.agentflow.service;

import com.agentflow.dto.job.JobApplicationCountsResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobApplicationUpdateRequest;
import com.agentflow.entity.*;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateResumeRepository;
import com.agentflow.repository.JobApplicationRepository;
import com.agentflow.service.impl.JobApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository applicationRepository;

    @Mock
    private CandidateResumeRepository resumeRepository;

    @InjectMocks
    private JobApplicationServiceImpl applicationService;

    private JobListing sampleJob;
    private JobApplication sampleApplication;

    @BeforeEach
    void setUp() {
        sampleJob = new JobListing("Platform Engineer", "Uber", "San Francisco, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Platform desc");
        sampleJob.setId(40L);

        sampleApplication = new JobApplication(sampleJob, JobApplicationStatus.SAVED);
        sampleApplication.setId(400L);
    }

    @Test
    @DisplayName("Retrieve application by ID successfully")
    void shouldGetApplicationById() {
        when(applicationRepository.findById(400L)).thenReturn(Optional.of(sampleApplication));

        JobApplicationResponse response = applicationService.getApplicationById(400L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(400L);
        assertThat(response.status()).isEqualTo(JobApplicationStatus.SAVED);
        assertThat(response.jobTitle()).isEqualTo("Platform Engineer");
        assertThat(response.jobCompany()).isEqualTo("Uber");
    }

    @Test
    @DisplayName("Retrieve application by job ID successfully")
    void shouldGetApplicationByJobId() {
        when(applicationRepository.findByJobId(40L)).thenReturn(Optional.of(sampleApplication));

        JobApplicationResponse response = applicationService.getApplicationByJobId(40L);

        assertThat(response.jobId()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo(JobApplicationStatus.SAVED);
    }

    @Test
    @DisplayName("Update status to APPLIED and auto-set appliedDate to today when previously unset")
    void shouldUpdateStatusAndAutoSetAppliedDate() {
        when(applicationRepository.findById(400L)).thenReturn(Optional.of(sampleApplication));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationResponse response = applicationService.updateApplicationStatus(400L, JobApplicationStatus.APPLIED);

        assertThat(response.status()).isEqualTo(JobApplicationStatus.APPLIED);
        assertThat(response.appliedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Update full application details including linking resume and setting follow-up date")
    void shouldUpdateFullApplicationDetails() {
        CandidateProfile profile = new CandidateProfile("Alice", "alice@example.com");
        CandidateResume resume = new CandidateResume(profile, "Backend v1", "text", true);
        resume.setId(500L);

        LocalDate today = LocalDate.now();
        LocalDate followUp = today.plusDays(7);

        JobApplicationUpdateRequest request = new JobApplicationUpdateRequest(
            JobApplicationStatus.INTERVIEWING,
            today.minusDays(5),
            followUp,
            "Completed first round interview",
            500L,
            new BigDecimal("88.50"),
            null
        );

        when(applicationRepository.findById(400L)).thenReturn(Optional.of(sampleApplication));
        when(resumeRepository.findById(500L)).thenReturn(Optional.of(resume));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplicationResponse response = applicationService.updateApplication(400L, request);

        assertThat(response.status()).isEqualTo(JobApplicationStatus.INTERVIEWING);
        assertThat(response.appliedDate()).isEqualTo(today.minusDays(5));
        assertThat(response.followUpDate()).isEqualTo(followUp);
        assertThat(response.notes()).isEqualTo("Completed first round interview");
        assertThat(response.resumeId()).isEqualTo(500L);
        assertThat(response.resumeVersionName()).isEqualTo("Backend v1");
        assertThat(response.matchScore()).isEqualByComparingTo(new BigDecimal("88.50"));
    }

    @Test
    @DisplayName("Reject invalid match score outside 0-100 range")
    void shouldRejectInvalidMatchScore() {
        JobApplicationUpdateRequest request = new JobApplicationUpdateRequest(
            JobApplicationStatus.SAVED, null, null, null, null, new BigDecimal("105.00"), null
        );

        when(applicationRepository.findById(400L)).thenReturn(Optional.of(sampleApplication));

        assertThatThrownBy(() -> applicationService.updateApplication(400L, request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("between 0.00 and 100.00");
    }

    @Test
    @DisplayName("Count applications across all statuses for dashboard metrics")
    void shouldCountApplicationsAcrossStatuses() {
        when(applicationRepository.countByStatus(JobApplicationStatus.SAVED)).thenReturn(5L);
        when(applicationRepository.countByStatus(JobApplicationStatus.APPLIED)).thenReturn(8L);
        when(applicationRepository.countByStatus(JobApplicationStatus.INTERVIEWING)).thenReturn(3L);
        when(applicationRepository.countByStatus(JobApplicationStatus.OFFERED)).thenReturn(1L);
        when(applicationRepository.countByStatus(JobApplicationStatus.REJECTED)).thenReturn(2L);
        when(applicationRepository.countByStatus(JobApplicationStatus.ARCHIVED)).thenReturn(0L);

        JobApplicationCountsResponse counts = applicationService.getApplicationCounts();

        assertThat(counts.saved()).isEqualTo(5L);
        assertThat(counts.applied()).isEqualTo(8L);
        assertThat(counts.interviewing()).isEqualTo(3L);
        assertThat(counts.offered()).isEqualTo(1L);
        assertThat(counts.rejected()).isEqualTo(2L);
        assertThat(counts.archived()).isEqualTo(0L);
        assertThat(counts.total()).isEqualTo(19L);
    }

    @Test
    @DisplayName("Retrieve applications with due follow-up dates")
    void shouldGetDueFollowUps() {
        LocalDate today = LocalDate.now();
        sampleApplication.setFollowUpDate(today.minusDays(1)); // overdue

        when(applicationRepository.findByFollowUpDateLessThanEqualAndStatusIn(eq(today), anyList()))
            .thenReturn(List.of(sampleApplication));

        List<JobApplicationResponse> due = applicationService.getDueFollowUps(today);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).id()).isEqualTo(400L);
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when application does not exist")
    void shouldThrowWhenApplicationNotFound() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplicationById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateMatchResult updates match score and breakdown while preserving status, dates, and notes")
    void shouldUpdateMatchResultPreservingExistingTrackingData() {
        sampleApplication.setStatus(JobApplicationStatus.INTERVIEWING);
        sampleApplication.setAppliedDate(LocalDate.of(2026, 10, 10));
        sampleApplication.setFollowUpDate(LocalDate.of(2026, 10, 20));
        sampleApplication.setNotes("Existing important notes");

        CandidateResume resume = new CandidateResume();
        resume.setId(25L);
        resume.setVersionName("Platform Lead v2");

        when(applicationRepository.findById(400L)).thenReturn(Optional.of(sampleApplication));
        when(resumeRepository.findById(25L)).thenReturn(Optional.of(resume));
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode breakdown = mapper.createObjectNode().put("summary", "Great fit");

        JobApplicationResponse response = applicationService.updateMatchResult(
            400L, 25L, new BigDecimal("87.50"), breakdown
        );

        assertThat(response).isNotNull();
        assertThat(response.matchScore()).isEqualTo(new BigDecimal("87.50"));
        assertThat(response.status()).isEqualTo(JobApplicationStatus.INTERVIEWING);
        assertThat(response.appliedDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(response.followUpDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(response.notes()).isEqualTo("Existing important notes");
        assertThat(response.resumeId()).isEqualTo(25L);
        assertThat(sampleApplication.getMatchScore()).isEqualTo(new BigDecimal("87.50"));
        assertThat(sampleApplication.getMatchBreakdown()).isEqualTo(breakdown);
    }
}
