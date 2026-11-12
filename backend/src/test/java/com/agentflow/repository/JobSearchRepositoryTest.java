package com.agentflow.repository;

import com.agentflow.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JobSearchRepositoryTest {

    @Autowired
    private CandidateProfileRepository profileRepository;

    @Autowired
    private CandidateResumeRepository resumeRepository;

    @Autowired
    private CandidateJobPreferenceRepository preferenceRepository;

    @Autowired
    private JobListingRepository listingRepository;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("CandidateProfileRepository: Save and find by email (case-insensitive) and existence checks")
    void shouldSaveAndQueryCandidateProfiles() {
        CandidateProfile profile = new CandidateProfile("Alice Smith", "alice.smith@example.com");
        profile.setHeadline("Lead Cloud Architect");
        profile.setLocation("Seattle, WA");
        CandidateProfile saved = profileRepository.save(profile);

        assertThat(saved.getId()).isNotNull();

        // Case-insensitive query
        Optional<CandidateProfile> found = profileRepository.findByEmailIgnoreCase("ALICE.SMITH@EXAMPLE.COM");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Alice Smith");

        // Exists checks
        assertThat(profileRepository.existsByEmailIgnoreCase("alice.smith@example.com")).isTrue();
        assertThat(profileRepository.existsByEmailIgnoreCase("ALICE.SMITH@EXAMPLE.COM")).isTrue();
        assertThat(profileRepository.existsByEmailIgnoreCase("unknown@example.com")).isFalse();

        // Exists excluding self ID (for updates)
        assertThat(profileRepository.existsByEmailIgnoreCaseAndIdNot("alice.smith@example.com", saved.getId())).isFalse();
        assertThat(profileRepository.existsByEmailIgnoreCaseAndIdNot("alice.smith@example.com", 9999L)).isTrue();

        // Primary/first profile lookup
        Optional<CandidateProfile> first = profileRepository.findFirstByOrderByCreatedAtAsc();
        assertThat(first).isPresent();
        assertThat(first.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("CandidateResumeRepository: Find resumes by profile, locate primary resume, and check version names")
    void shouldManageCandidateResumes() {
        CandidateProfile profile = profileRepository.save(new CandidateProfile("Bob Jones", "bob@example.com"));

        CandidateResume resume1 = new CandidateResume(profile, "Backend Java", "Raw resume text 1", true);
        CandidateResume resume2 = new CandidateResume(profile, "Full Stack", "Raw resume text 2", false);
        resumeRepository.save(resume1);
        resumeRepository.save(resume2);

        // Find all by profile ID
        List<CandidateResume> allResumes = resumeRepository.findByProfileId(profile.getId());
        assertThat(allResumes).hasSize(2);

        // Find ordered by newest first
        List<CandidateResume> orderedResumes = resumeRepository.findByProfileIdOrderByCreatedAtDesc(profile.getId());
        assertThat(orderedResumes).hasSize(2);

        // Locate primary resume
        Optional<CandidateResume> primary = resumeRepository.findByProfileIdAndIsPrimaryTrue(profile.getId());
        assertThat(primary).isPresent();
        assertThat(primary.get().getVersionName()).isEqualTo("Backend Java");
        assertThat(primary.get().isPrimary()).isTrue();

        // Version name existence checks
        assertThat(resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(profile.getId(), "backend java")).isTrue();
        assertThat(resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(profile.getId(), "nonexistent")).isFalse();
        assertThat(resumeRepository.existsByProfileIdAndVersionNameIgnoreCaseAndIdNot(profile.getId(), "backend java", resume1.getId())).isFalse();
        assertThat(resumeRepository.existsByProfileIdAndVersionNameIgnoreCaseAndIdNot(profile.getId(), "backend java", resume2.getId())).isTrue();

        // Count
        assertThat(resumeRepository.countByProfileId(profile.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("CandidateJobPreferenceRepository: Find and delete preferences by profile ID")
    void shouldManageJobPreferences() {
        CandidateProfile profile = profileRepository.save(new CandidateProfile("Carol Danvers", "carol@example.com"));

        CandidateJobPreference preference = new CandidateJobPreference(profile);
        ArrayNode titles = objectMapper.createArrayNode().add("Engineering Manager").add("Director of Engineering");
        preference.setTargetTitles(titles);
        preference.setMinSalary(new BigDecimal("210000.00"));
        preference.setCurrency("USD");
        preferenceRepository.save(preference);

        // Find by profile ID
        Optional<CandidateJobPreference> found = preferenceRepository.findByProfileId(profile.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCurrency()).isEqualTo("USD");
        assertThat(found.get().getMinSalary()).isEqualByComparingTo(new BigDecimal("210000.00"));
        assertThat(found.get().getTargetTitles().get(0).asText()).isEqualTo("Engineering Manager");

        // Exists by profile ID
        assertThat(preferenceRepository.existsByProfileId(profile.getId())).isTrue();
        assertThat(preferenceRepository.existsByProfileId(9999L)).isFalse();

        // Delete by profile ID
        preferenceRepository.deleteByProfileId(profile.getId());
        assertThat(preferenceRepository.findByProfileId(profile.getId())).isEmpty();
        assertThat(preferenceRepository.existsByProfileId(profile.getId())).isFalse();
    }

    @Test
    @DisplayName("JobListingRepository: Find by content hash and source URL")
    void shouldFindByContentHashAndSourceUrl() {
        JobListing job = new JobListing("Senior Site Reliability Engineer", "Google", "Mountain View, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "SRE job description");
        job.setSourceUrl("https://careers.google.com/jobs/results/123456");
        JobListing saved = listingRepository.save(job);

        String expectedHash = JobListing.calculateContentHash("Google", "Senior Site Reliability Engineer", "Mountain View, CA");
        assertThat(saved.getContentHash()).isEqualTo(expectedHash);

        // Find by content hash
        Optional<JobListing> foundByHash = listingRepository.findByContentHash(expectedHash);
        assertThat(foundByHash).isPresent();
        assertThat(foundByHash.get().getCompany()).isEqualTo("Google");
        assertThat(listingRepository.existsByContentHash(expectedHash)).isTrue();

        // Find by source URL
        Optional<JobListing> foundByUrl = listingRepository.findBySourceUrl("https://careers.google.com/jobs/results/123456");
        assertThat(foundByUrl).isPresent();
        assertThat(foundByUrl.get().getTitle()).isEqualTo("Senior Site Reliability Engineer");
        assertThat(listingRepository.existsBySourceUrl("https://careers.google.com/jobs/results/123456")).isTrue();
        assertThat(listingRepository.existsBySourceUrl("https://unknown.com")).isFalse();
    }

    @Test
    @DisplayName("JobListingRepository: Keyword search, filter by workplace/employment type, pagination, and unified query")
    void shouldSearchAndFilterJobListingsWithPagination() {
        listingRepository.save(new JobListing("Staff Backend Engineer", "Acme Corp", "Remote",
                WorkplaceType.REMOTE, EmploymentType.FULL_TIME, "Backend role"));
        listingRepository.save(new JobListing("Frontend React Specialist", "Acme Corp", "New York, NY",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Frontend role"));
        listingRepository.save(new JobListing("DevOps Contractor", "CloudFlow Inc", "Austin, TX",
                WorkplaceType.REMOTE, EmploymentType.CONTRACT, "DevOps contract"));

        // Case-insensitive title keyword
        Page<JobListing> backendResults = listingRepository
                .findByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase("BACKEND", "BACKEND", PageRequest.of(0, 10));
        assertThat(backendResults.getTotalElements()).isEqualTo(1);
        assertThat(backendResults.getContent().get(0).getTitle()).isEqualTo("Staff Backend Engineer");

        // Case-insensitive company keyword
        Page<JobListing> acmeResults = listingRepository
                .findByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase("acme", "acme", PageRequest.of(0, 10));
        assertThat(acmeResults.getTotalElements()).isEqualTo(2);

        // Filter by workplace type
        Page<JobListing> remoteJobs = listingRepository.findByWorkplaceType(WorkplaceType.REMOTE, PageRequest.of(0, 10));
        assertThat(remoteJobs.getTotalElements()).isEqualTo(2);

        // Filter by employment type
        Page<JobListing> contractJobs = listingRepository.findByEmploymentType(EmploymentType.CONTRACT, PageRequest.of(0, 10));
        assertThat(contractJobs.getTotalElements()).isEqualTo(1);

        // Unified search query with combined filters
        Page<JobListing> unifiedSearch = listingRepository.searchJobListings(
                "Acme", WorkplaceType.REMOTE, EmploymentType.FULL_TIME, PageRequest.of(0, 10));
        assertThat(unifiedSearch.getTotalElements()).isEqualTo(1);
        assertThat(unifiedSearch.getContent().get(0).getTitle()).isEqualTo("Staff Backend Engineer");

        // Unified search with null filters (returns all with pagination)
        Page<JobListing> allPaged = listingRepository.searchJobListings(
                null, null, null, PageRequest.of(0, 2, Sort.by("title").ascending()));
        assertThat(allPaged.getTotalElements()).isEqualTo(3);
        assertThat(allPaged.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("JobApplicationRepository: Track application by job ID, filter by status, counts, and follow-up queries")
    void shouldManageJobApplications() {
        JobListing job1 = listingRepository.save(new JobListing("Job 1", "Company 1", "Remote",
                WorkplaceType.REMOTE, EmploymentType.FULL_TIME, "Desc 1"));
        JobListing job2 = listingRepository.save(new JobListing("Job 2", "Company 2", "Remote",
                WorkplaceType.REMOTE, EmploymentType.FULL_TIME, "Desc 2"));
        JobListing job3 = listingRepository.save(new JobListing("Job 3", "Company 3", "Remote",
                WorkplaceType.REMOTE, EmploymentType.FULL_TIME, "Desc 3"));

        LocalDate today = LocalDate.now();

        JobApplication app1 = new JobApplication(job1, JobApplicationStatus.SAVED);
        applicationRepository.save(app1);

        JobApplication app2 = new JobApplication(job2, JobApplicationStatus.APPLIED);
        app2.setAppliedDate(today.minusDays(2));
        app2.setFollowUpDate(today.plusDays(5));
        applicationRepository.save(app2);

        JobApplication app3 = new JobApplication(job3, JobApplicationStatus.INTERVIEWING);
        app3.setAppliedDate(today.minusDays(7));
        app3.setFollowUpDate(today.minusDays(1)); // Overdue follow-up
        applicationRepository.save(app3);

        // Find by Job ID
        Optional<JobApplication> foundApp1 = applicationRepository.findByJobId(job1.getId());
        assertThat(foundApp1).isPresent();
        assertThat(foundApp1.get().getStatus()).isEqualTo(JobApplicationStatus.SAVED);
        assertThat(applicationRepository.existsByJobId(job1.getId())).isTrue();
        assertThat(applicationRepository.existsByJobId(9999L)).isFalse();

        // Status filters
        List<JobApplication> savedApps = applicationRepository.findByStatus(JobApplicationStatus.SAVED);
        assertThat(savedApps).hasSize(1);

        // Counts
        assertThat(applicationRepository.countByStatus(JobApplicationStatus.SAVED)).isEqualTo(1);
        assertThat(applicationRepository.countByStatus(JobApplicationStatus.APPLIED)).isEqualTo(1);
        assertThat(applicationRepository.countByStatus(JobApplicationStatus.INTERVIEWING)).isEqualTo(1);
        assertThat(applicationRepository.countByStatus(JobApplicationStatus.OFFERED)).isEqualTo(0);

        // Optional status filter query with pagination
        Page<JobApplication> pagedApplied = applicationRepository.findByOptionalStatus(
                JobApplicationStatus.APPLIED, PageRequest.of(0, 10));
        assertThat(pagedApplied.getTotalElements()).isEqualTo(1);

        Page<JobApplication> allPagedApps = applicationRepository.findByOptionalStatus(
                null, PageRequest.of(0, 10, Sort.by("id").ascending()));
        assertThat(allPagedApps.getTotalElements()).isEqualTo(3);

        // Due follow-ups query
        List<JobApplication> dueFollowUps = applicationRepository.findByFollowUpDateLessThanEqualAndStatusIn(
                today, List.of(JobApplicationStatus.APPLIED, JobApplicationStatus.INTERVIEWING));
        assertThat(dueFollowUps).hasSize(1);
        assertThat(dueFollowUps.get(0).getId()).isEqualTo(app3.getId());
    }

    @Test
    @DisplayName("Correct handling of missing records returning Optional.empty and empty collections")
    void shouldHandleMissingRecordsGracefully() {
        assertThat(profileRepository.findById(999999L)).isEmpty();
        assertThat(profileRepository.findByEmailIgnoreCase("nonexistent@domain.com")).isEmpty();

        assertThat(resumeRepository.findByProfileId(999999L)).isEmpty();
        assertThat(resumeRepository.findByProfileIdAndIsPrimaryTrue(999999L)).isEmpty();

        assertThat(preferenceRepository.findByProfileId(999999L)).isEmpty();

        assertThat(listingRepository.findByContentHash("missing_hash")).isEmpty();
        assertThat(listingRepository.findBySourceUrl("https://nowhere.com")).isEmpty();

        assertThat(applicationRepository.findByJobId(999999L)).isEmpty();
        assertThat(applicationRepository.findByStatus(JobApplicationStatus.REJECTED)).isEmpty();
    }

    @Test
    @DisplayName("Coexist cleanly with existing WorkflowRepository in the same test context")
    void shouldCoexistWithExistingWorkflowRepository() throws Exception {
        Workflow workflow = new Workflow("Data Pipeline", "Pipeline Desc", objectMapper.readTree("{\"nodes\":[]}"), true);
        Workflow savedWf = workflowRepository.save(workflow);
        assertThat(savedWf.getId()).isNotNull();

        CandidateProfile profile = profileRepository.save(new CandidateProfile("Ada Lovelace", "ada@example.com"));
        assertThat(profile.getId()).isNotNull();

        JobListing job = listingRepository.save(new JobListing("Algorithm Engineer", "Analytical Engine", "London",
                WorkplaceType.ON_SITE, EmploymentType.FULL_TIME, "Algorithms"));
        assertThat(job.getId()).isNotNull();

        assertThat(workflowRepository.findById(savedWf.getId())).isPresent();
        assertThat(profileRepository.findById(profile.getId())).isPresent();
        assertThat(listingRepository.findById(job.getId())).isPresent();
    }
}
