package com.agentflow.service;

import com.agentflow.dto.job.JobListingCreateRequest;
import com.agentflow.dto.job.JobListingResponse;
import com.agentflow.dto.job.JobListingSaveResult;
import com.agentflow.dto.job.JobListingUpdateRequest;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobApplication;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.entity.JobListing;
import com.agentflow.entity.WorkplaceType;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.JobApplicationRepository;
import com.agentflow.repository.JobListingRepository;
import com.agentflow.service.impl.JobListingServiceImpl;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobListingServiceTest {

    @Mock
    private JobListingRepository listingRepository;

    @Mock
    private JobApplicationRepository applicationRepository;

    @InjectMocks
    private JobListingServiceImpl listingService;

    private JobListing sampleListing;
    private JobApplication sampleApplication;

    @BeforeEach
    void setUp() {
        sampleListing = new JobListing("Senior Java Engineer", "Netflix", "Los Gatos, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Core streaming backend");
        sampleListing.setId(30L);
        sampleListing.setSourceUrl("https://netflix.jobs/123");

        sampleApplication = new JobApplication(sampleListing, JobApplicationStatus.SAVED);
        sampleApplication.setId(300L);
    }

    @Test
    @DisplayName("Create job listing successfully and initialize application record as SAVED")
    void shouldCreateListingAndInitializeSavedApplication() {
        JobListingCreateRequest request = new JobListingCreateRequest(
            "Senior Java Engineer", "Netflix", "Los Gatos, CA",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "$180,000 - $220,000",
            "MANUAL", "https://netflix.jobs/123", "Core streaming backend", null, null
        );

        String expectedHash = JobListing.calculateContentHash("Netflix", "Senior Java Engineer", "Los Gatos, CA");

        when(listingRepository.findByContentHash(expectedHash)).thenReturn(Optional.empty());
        when(listingRepository.findBySourceUrl("https://netflix.jobs/123")).thenReturn(Optional.empty());
        when(listingRepository.saveAndFlush(any(JobListing.class))).thenAnswer(invocation -> {
            JobListing jl = invocation.getArgument(0);
            jl.setId(30L);
            return jl;
        });
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication ja = invocation.getArgument(0);
            ja.setId(300L);
            return ja;
        });

        JobListingSaveResult result = listingService.createJobListing(request);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.listing()).isNotNull();
        assertThat(result.listing().id()).isEqualTo(30L);
        assertThat(result.application()).isNotNull();
        assertThat(result.application().status()).isEqualTo(JobApplicationStatus.SAVED);

        verify(listingRepository).saveAndFlush(any());
        verify(applicationRepository).save(any());
    }

    @Test
    @DisplayName("Detect duplicate by content hash and preserve existing application status and notes")
    void shouldDetectDuplicateByContentHashAndPreserveApplication() {
        JobListingCreateRequest request = new JobListingCreateRequest(
            "senior java engineer", "  Netflix  ", "los gatos, ca",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME, null, null, null, "Updated text", null, null
        );

        String expectedHash = JobListing.calculateContentHash("Netflix", "Senior Java Engineer", "Los Gatos, CA");

        // Existing listing already has APPLIED status with interview notes
        sampleApplication.setStatus(JobApplicationStatus.APPLIED);
        sampleApplication.setAppliedDate(LocalDate.now().minusDays(3));
        sampleApplication.setNotes("First interview scheduled");

        when(listingRepository.findByContentHash(expectedHash)).thenReturn(Optional.of(sampleListing));
        when(applicationRepository.findByJobId(30L)).thenReturn(Optional.of(sampleApplication));

        JobListingSaveResult result = listingService.createJobListing(request);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.message()).contains("already exists");
        assertThat(result.application().status()).isEqualTo(JobApplicationStatus.APPLIED);
        assertThat(result.application().notes()).isEqualTo("First interview scheduled");

        // Verify no duplicate insert was attempted
        verify(listingRepository, never()).saveAndFlush(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Detect duplicate by source URL and preserve application data")
    void shouldDetectDuplicateBySourceUrl() {
        JobListingCreateRequest request = new JobListingCreateRequest(
            "Different Title", "Different Company", null, null, null, null, null,
            "https://netflix.jobs/123", null, null, null
        );

        when(listingRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
        when(listingRepository.findBySourceUrl("https://netflix.jobs/123")).thenReturn(Optional.of(sampleListing));
        when(applicationRepository.findByJobId(30L)).thenReturn(Optional.of(sampleApplication));

        JobListingSaveResult result = listingService.createJobListing(request);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.listing().id()).isEqualTo(30L);

        verify(listingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update job listing details while preserving application record")
    void shouldUpdateListingSuccessfully() {
        JobListingUpdateRequest request = new JobListingUpdateRequest(
            "Senior Backend Engineer (Platform)", "Netflix", "Los Gatos, CA",
            WorkplaceType.REMOTE, EmploymentType.FULL_TIME, "$200k - $240k", "MANUAL",
            "https://netflix.jobs/123", "Updated description", null
        );

        when(listingRepository.findById(30L)).thenReturn(Optional.of(sampleListing));
        when(listingRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
        when(listingRepository.save(any(JobListing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobListingResponse response = listingService.updateJobListing(30L, request);

        assertThat(response.title()).isEqualTo("Senior Backend Engineer (Platform)");
        assertThat(response.workplaceType()).isEqualTo(WorkplaceType.REMOTE);
    }

    @Test
    @DisplayName("Reject update if new title/company causes collision with another listing")
    void shouldRejectUpdateWithHashCollision() {
        JobListing otherListing = new JobListing("Staff Engineer", "Google", "Mountain View, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "desc");
        otherListing.setId(999L);

        JobListingUpdateRequest request = new JobListingUpdateRequest(
            "Staff Engineer", "Google", "Mountain View, CA", null, null, null, null, null, null, null
        );

        when(listingRepository.findById(30L)).thenReturn(Optional.of(sampleListing));
        when(listingRepository.findByContentHash(anyString())).thenReturn(Optional.of(otherListing));

        assertThatThrownBy(() -> listingService.updateJobListing(30L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Delete job listing successfully")
    void shouldDeleteListing() {
        when(listingRepository.findById(30L)).thenReturn(Optional.of(sampleListing));

        listingService.deleteJobListing(30L);

        verify(listingRepository).delete(sampleListing);
    }

    @Test
    @DisplayName("Search job listings with filters and pagination")
    void shouldSearchJobListingsWithFilters() {
        Page<JobListing> page = new PageImpl<>(List.of(sampleListing), PageRequest.of(0, 10), 1);
        when(listingRepository.searchJobListings(eq("Netflix"), eq(WorkplaceType.HYBRID), eq(null), any(PageRequest.class)))
            .thenReturn(page);

        Page<JobListingResponse> results = listingService.searchJobListings(
            "Netflix", WorkplaceType.HYBRID, null, PageRequest.of(0, 10)
        );

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).company()).isEqualTo("Netflix");
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when listing ID does not exist")
    void shouldThrowWhenListingNotFound() {
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.getJobListingById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }
}
