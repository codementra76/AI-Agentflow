package com.agentflow.service;

import com.agentflow.dto.job.*;
import com.agentflow.entity.*;
import com.agentflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JobSearchServiceIntegrationTest {

    @Autowired
    private CandidateProfileService profileService;

    @Autowired
    private CandidateResumeService resumeService;

    @Autowired
    private CandidateJobPreferenceService preferenceService;

    @Autowired
    private JobListingService listingService;

    @Autowired
    private JobApplicationService applicationService;

    @Autowired
    private WorkflowRepository workflowRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("End-to-end integration: Profile -> Resumes -> Preferences -> Job -> Tracking Pipeline")
    void shouldExecuteFullJobSearchLifecycle() {
        // 1. Create candidate profile
        CandidateProfileRequest profileReq = new CandidateProfileRequest(
            "Jane Developer", "jane.dev@example.com", "+1-555-0202",
            "Lead Backend Engineer", "Austin, TX", "https://janedev.io",
            "https://linkedin.com/in/janedev", "https://github.com/janedev",
            "Expert in Spring Boot and high-throughput systems."
        );
        CandidateProfileResponse profile = profileService.createProfile(profileReq);
        assertThat(profile.id()).isNotNull();
        assertThat(profile.email()).isEqualTo("jane.dev@example.com");

        // 2. Add two resumes and toggle primary
        CandidateResumeCreateRequest resume1Req = new CandidateResumeCreateRequest(
            "Java Specialist v1", "# Jane Dev\nSenior Java Resume", true, null
        );
        CandidateResumeResponse resume1 = resumeService.createResume(profile.id(), resume1Req);
        assertThat(resume1.isPrimary()).isTrue();

        CandidateResumeCreateRequest resume2Req = new CandidateResumeCreateRequest(
            "Cloud Native v1", "# Jane Dev\nCloud Engineer Resume", false, null
        );
        CandidateResumeResponse resume2 = resumeService.createResume(profile.id(), resume2Req);
        assertThat(resume2.isPrimary()).isFalse();

        // Switch primary to resume 2
        CandidateResumeResponse updatedResume2 = resumeService.setPrimaryResume(resume2.id());
        assertThat(updatedResume2.isPrimary()).isTrue();
        assertThat(resumeService.getResumeById(resume1.id()).isPrimary()).isFalse();

        // 3. Set job preferences
        CandidateJobPreferenceRequest prefReq = new CandidateJobPreferenceRequest(
            List.of("Staff Engineer", "Principal Architect"),
            List.of("Austin, TX", "Remote"),
            List.of("REMOTE", "HYBRID"),
            List.of("FULL_TIME"),
            new BigDecimal("190000.00"),
            "USD"
        );
        CandidateJobPreferenceResponse pref = preferenceService.saveOrUpdatePreferences(profile.id(), prefReq);
        assertThat(pref.minSalary()).isEqualByComparingTo(new BigDecimal("190000.00"));

        // 4. Ingest/Create a job listing
        JobListingCreateRequest jobReq = new JobListingCreateRequest(
            "Staff Systems Architect", "Datadog", "Austin, TX",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "$180,000 - $230,000",
            "IMPORT", "https://careers.datadog.com/jobs/9988", "High scale metrics platform",
            null, JobApplicationStatus.SAVED
        );
        JobListingSaveResult saveResult = listingService.createJobListing(jobReq);
        assertThat(saveResult.isDuplicate()).isFalse();
        assertThat(saveResult.listing().id()).isNotNull();
        assertThat(saveResult.application().status()).isEqualTo(JobApplicationStatus.SAVED);

        Long jobId = saveResult.listing().id();
        Long appId = saveResult.application().id();

        // 5. Update application: transition to APPLIED, link primary resume, set follow-up date
        LocalDate today = LocalDate.now();
        JobApplicationUpdateRequest appUpdate = new JobApplicationUpdateRequest(
            JobApplicationStatus.APPLIED,
            today,
            today.plusDays(7),
            "Applied via company careers portal using Cloud Native resume",
            resume2.id(),
            new BigDecimal("94.00"),
            null
        );
        JobApplicationResponse updatedApp = applicationService.updateApplication(appId, appUpdate);
        assertThat(updatedApp.status()).isEqualTo(JobApplicationStatus.APPLIED);
        assertThat(updatedApp.resumeId()).isEqualTo(resume2.id());
        assertThat(updatedApp.resumeVersionName()).isEqualTo("Cloud Native v1");

        // 6. Test duplicate detection: attempt to import the same job again
        JobListingCreateRequest duplicateJobReq = new JobListingCreateRequest(
            "staff systems architect", "  Datadog  ", "austin, tx",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME, null,
            "IMPORT", "https://careers.datadog.com/jobs/9988", null, null, null
        );
        JobListingSaveResult duplicateResult = listingService.createJobListing(duplicateJobReq);

        assertThat(duplicateResult.isDuplicate()).isTrue();
        assertThat(duplicateResult.listing().id()).isEqualTo(jobId);
        // Existing application status and notes MUST be preserved!
        assertThat(duplicateResult.application().status()).isEqualTo(JobApplicationStatus.APPLIED);
        assertThat(duplicateResult.application().notes()).contains("Applied via company careers portal");
        assertThat(duplicateResult.application().appliedDate()).isEqualTo(today);

        // 7. Test application counts metric
        JobApplicationCountsResponse counts = applicationService.getApplicationCounts();
        assertThat(counts.applied()).isGreaterThanOrEqualTo(1L);
        assertThat(counts.total()).isGreaterThanOrEqualTo(1L);

        // 8. Search job listings
        Page<JobListingResponse> searchResults = listingService.searchJobListings(
            "datadog", WorkplaceType.HYBRID, null, PageRequest.of(0, 10)
        );
        assertThat(searchResults.getContent()).isNotEmpty();
        assertThat(searchResults.getContent().get(0).company()).isEqualTo("Datadog");

        // 9. Coexistence check: verify existing Workflow entity saves without issue
        Workflow workflow = new Workflow("Job Search Sync", "Integration test flow", objectMapper.createObjectNode(), true);
        Workflow savedWf = workflowRepository.save(workflow);
        assertThat(savedWf.getId()).isNotNull();
    }
}
