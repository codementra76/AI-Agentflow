package com.agentflow.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class JobSearchEntityPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Persist and query candidate profile with auto timestamps")
    void shouldPersistAndRetrieveCandidateProfile() {
        CandidateProfile profile = new CandidateProfile("Alice Smith", "alice.smith@example.com");
        profile.setPhone("+1-555-0199");
        profile.setHeadline("Staff Software Engineer");
        profile.setLocation("San Francisco, CA");
        profile.setPortfolioUrl("https://alicesmith.dev");
        profile.setLinkedinUrl("https://linkedin.com/in/alicesmith");
        profile.setGithubUrl("https://github.com/alicesmith");
        profile.setSummary("Passionate backend engineer with 8+ years experience in distributed systems.");

        CandidateProfile persisted = entityManager.persistAndFlush(profile);
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();

        entityManager.clear();

        CandidateProfile found = entityManager.find(CandidateProfile.class, persisted.getId());
        assertThat(found).isNotNull();
        assertThat(found.getFullName()).isEqualTo("Alice Smith");
        assertThat(found.getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(found.getPhone()).isEqualTo("+1-555-0199");
        assertThat(found.getHeadline()).isEqualTo("Staff Software Engineer");
        assertThat(found.getLocation()).isEqualTo("San Francisco, CA");
        assertThat(found.getPortfolioUrl()).isEqualTo("https://alicesmith.dev");
        assertThat(found.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/alicesmith");
        assertThat(found.getGithubUrl()).isEqualTo("https://github.com/alicesmith");
        assertThat(found.getSummary()).contains("8+ years experience");
    }

    @Test
    @DisplayName("Enforce unique email constraint on candidate profiles")
    void shouldEnforceCandidateProfileEmailUniqueness() {
        CandidateProfile profile1 = new CandidateProfile("First Candidate", "duplicate@example.com");
        entityManager.persistAndFlush(profile1);

        CandidateProfile profile2 = new CandidateProfile("Second Candidate", "duplicate@example.com");
        assertThatThrownBy(() -> entityManager.persistAndFlush(profile2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Persist multiple resume versions and cascade delete on profile removal")
    void shouldPersistMultipleResumeVersionsAndCascadeDelete() {
        CandidateProfile profile = new CandidateProfile("Bob Jones", "bob.jones@example.com");
        CandidateProfile savedProfile = entityManager.persistAndFlush(profile);

        CandidateResume resume1 = new CandidateResume(savedProfile, "Java Backend v1", "# Bob Jones\nExperienced Java Dev", true);
        CandidateResume resume2 = new CandidateResume(savedProfile, "Full-Stack v1", "# Bob Jones\nFull-Stack React & Spring", false);

        CandidateResume savedResume1 = entityManager.persistAndFlush(resume1);
        CandidateResume savedResume2 = entityManager.persistAndFlush(resume2);

        assertThat(savedResume1.getId()).isNotNull();
        assertThat(savedResume2.getId()).isNotNull();
        assertThat(savedResume1.isPrimary()).isTrue();
        assertThat(savedResume2.isPrimary()).isFalse();

        entityManager.clear();

        // Verify both resumes are retrievable
        assertThat(entityManager.find(CandidateResume.class, savedResume1.getId())).isNotNull();
        assertThat(entityManager.find(CandidateResume.class, savedResume2.getId())).isNotNull();

        // Delete candidate profile and verify cascade removal of both resumes
        CandidateProfile profileToDelete = entityManager.find(CandidateProfile.class, savedProfile.getId());
        entityManager.remove(profileToDelete);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(CandidateProfile.class, savedProfile.getId())).isNull();
        assertThat(entityManager.find(CandidateResume.class, savedResume1.getId())).isNull();
        assertThat(entityManager.find(CandidateResume.class, savedResume2.getId())).isNull();
    }

    @Test
    @DisplayName("Persist and retrieve candidate job preferences with JSON arrays and salary")
    void shouldPersistJobPreferencesWithJsonColumns() {
        CandidateProfile profile = new CandidateProfile("Carol White", "carol.white@example.com");
        CandidateProfile savedProfile = entityManager.persistAndFlush(profile);

        CandidateJobPreference pref = new CandidateJobPreference(savedProfile);

        ArrayNode targetTitles = objectMapper.createArrayNode();
        targetTitles.add("Senior Backend Engineer").add("Java Architect");
        pref.setTargetTitles(targetTitles);

        ArrayNode preferredLocations = objectMapper.createArrayNode();
        preferredLocations.add("New York, NY").add("Remote");
        pref.setPreferredLocations(preferredLocations);

        ArrayNode workplaceTypes = objectMapper.createArrayNode();
        workplaceTypes.add("REMOTE").add("HYBRID");
        pref.setWorkplaceTypes(workplaceTypes);

        ArrayNode employmentTypes = objectMapper.createArrayNode();
        employmentTypes.add("FULL_TIME");
        pref.setEmploymentTypes(employmentTypes);

        pref.setMinSalary(new BigDecimal("145000.00"));
        pref.setCurrency("USD");

        CandidateJobPreference savedPref = entityManager.persistAndFlush(pref);
        assertThat(savedPref.getId()).isNotNull();

        entityManager.clear();

        CandidateJobPreference found = entityManager.find(CandidateJobPreference.class, savedPref.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.getMinSalary()).isEqualByComparingTo(new BigDecimal("145000.00"));
        assertThat(found.getTargetTitles().get(0).asText()).isEqualTo("Senior Backend Engineer");
        assertThat(found.getPreferredLocations().get(1).asText()).isEqualTo("Remote");
        assertThat(found.getWorkplaceTypes().get(0).asText()).isEqualTo("REMOTE");
        assertThat(found.getEmploymentTypes().get(0).asText()).isEqualTo("FULL_TIME");
    }

    @Test
    @DisplayName("Enforce 1-to-1 unique preference record per candidate profile")
    void shouldEnforceOnePreferenceRecordPerProfile() {
        CandidateProfile profile = new CandidateProfile("David Lee", "david.lee@example.com");
        CandidateProfile savedProfile = entityManager.persistAndFlush(profile);

        CandidateJobPreference pref1 = new CandidateJobPreference(savedProfile);
        entityManager.persistAndFlush(pref1);

        CandidateJobPreference pref2 = new CandidateJobPreference(savedProfile);
        assertThatThrownBy(() -> entityManager.persistAndFlush(pref2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Persist job listing with auto-calculated deterministic content hash")
    void shouldPersistJobListingAndAutoCalculateContentHash() {
        JobListing job = new JobListing();
        job.setTitle("  Staff Backend Engineer ");
        job.setCompany("  Stripe, Inc. ");
        job.setLocation(" Remote - US ");
        job.setWorkplaceType(WorkplaceType.REMOTE);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setSalaryRange("$180,000 - $220,000");
        job.setSource("MANUAL");
        job.setSourceUrl("https://stripe.com/jobs/12345");
        job.setRawDescription("We are looking for a staff backend engineer to scale our core payment infrastructure.");

        JobListing savedJob = entityManager.persistAndFlush(job);

        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getContentHash()).isNotBlank();
        assertThat(savedJob.getContentHash()).hasSize(64);

        // Verify hash matches deterministic normalized calculation
        String expectedHash = JobListing.calculateContentHash("Stripe, Inc.", "Staff Backend Engineer", "Remote - US");
        assertThat(savedJob.getContentHash()).isEqualTo(expectedHash);

        entityManager.clear();

        JobListing found = entityManager.find(JobListing.class, savedJob.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("  Staff Backend Engineer ");
        assertThat(found.getWorkplaceType()).isEqualTo(WorkplaceType.REMOTE);
        assertThat(found.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(found.getSource()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Enforce unique constraint on job listing content hash to prevent duplicate listings")
    void shouldEnforceJobListingContentHashUniqueness() {
        JobListing job1 = new JobListing("Senior Java Engineer", "Google", "Sunnyvale, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Java role 1");
        entityManager.persistAndFlush(job1);

        // Identical role, company, location -> same content hash
        JobListing job2 = new JobListing("senior java engineer", "  google  ", "sunnyvale, ca",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Java role 2");
        assertThatThrownBy(() -> entityManager.persistAndFlush(job2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Persist job application in SAVED status without requiring applied_date")
    void shouldPersistJobApplicationInSavedStatusWithoutAppliedDate() {
        JobListing job = new JobListing("DevOps Engineer", "Netflix", "Los Gatos, CA",
                WorkplaceType.ON_SITE, EmploymentType.FULL_TIME, "DevOps role");
        JobListing savedJob = entityManager.persistAndFlush(job);

        JobApplication application = new JobApplication(savedJob);
        application.setNotes("Saved from engineering blog link. Looks promising.");

        JobApplication savedApp = entityManager.persistAndFlush(application);
        assertThat(savedApp.getId()).isNotNull();
        assertThat(savedApp.getStatus()).isEqualTo(JobApplicationStatus.SAVED);
        assertThat(savedApp.getAppliedDate()).isNull();
        assertThat(savedApp.getFollowUpDate()).isNull();

        // Advance to APPLIED status with dates
        LocalDate today = LocalDate.now();
        LocalDate followUp = today.plusDays(7);
        savedApp.setStatus(JobApplicationStatus.APPLIED);
        savedApp.setAppliedDate(today);
        savedApp.setFollowUpDate(followUp);

        entityManager.persistAndFlush(savedApp);
        entityManager.clear();

        JobApplication found = entityManager.find(JobApplication.class, savedApp.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo(JobApplicationStatus.APPLIED);
        assertThat(found.getAppliedDate()).isEqualTo(today);
        assertThat(found.getFollowUpDate()).isEqualTo(followUp);
    }

    @Test
    @DisplayName("Cascade delete job application when job listing is deleted")
    void shouldCascadeDeleteJobApplicationWhenJobListingDeleted() {
        JobListing job = new JobListing("Platform Engineer", "Uber", "San Francisco, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Platform role");
        JobListing savedJob = entityManager.persistAndFlush(job);

        JobApplication application = new JobApplication(savedJob, JobApplicationStatus.SAVED);
        JobApplication savedApp = entityManager.persistAndFlush(application);

        Long appId = savedApp.getId();
        Long jobId = savedJob.getId();

        entityManager.clear();

        // Remove the job listing
        JobListing jobToDelete = entityManager.find(JobListing.class, jobId);
        entityManager.remove(jobToDelete);
        entityManager.flush();
        entityManager.clear();

        // Both job listing and application should be deleted
        assertThat(entityManager.find(JobListing.class, jobId)).isNull();
        assertThat(entityManager.find(JobApplication.class, appId)).isNull();
    }

    @Test
    @DisplayName("Set resume reference to null on job application when candidate resume is deleted")
    void shouldSetNullOnResumeWhenCandidateResumeDeleted() {
        CandidateProfile profile = new CandidateProfile("Eve Adams", "eve.adams@example.com");
        CandidateProfile savedProfile = entityManager.persistAndFlush(profile);

        CandidateResume resume = new CandidateResume(savedProfile, "Version A", "# Eve Adams", true);
        CandidateResume savedResume = entityManager.persistAndFlush(resume);

        JobListing job = new JobListing("Security Engineer", "Cloudflare", "Austin, TX",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Security role");
        JobListing savedJob = entityManager.persistAndFlush(job);

        JobApplication application = new JobApplication(savedJob, JobApplicationStatus.APPLIED);
        application.setResume(savedResume);
        JobApplication savedApp = entityManager.persistAndFlush(application);

        Long appId = savedApp.getId();
        Long resumeId = savedResume.getId();

        entityManager.clear();

        // Delete the resume
        CandidateResume resumeToDelete = entityManager.find(CandidateResume.class, resumeId);
        entityManager.remove(resumeToDelete);
        entityManager.flush();
        entityManager.clear();

        // Application still exists, but resume reference is null
        JobApplication updatedApp = entityManager.find(JobApplication.class, appId);
        assertThat(updatedApp).isNotNull();
        assertThat(updatedApp.getResume()).isNull();
    }

    @Test
    @DisplayName("Enforce single application record per job listing (prevent duplicate tracking)")
    void shouldEnforceSingleApplicationPerJobListing() {
        JobListing job = new JobListing("Site Reliability Engineer", "Datadog", "Boston, MA",
                WorkplaceType.ON_SITE, EmploymentType.FULL_TIME, "SRE role");
        JobListing savedJob = entityManager.persistAndFlush(job);

        JobApplication app1 = new JobApplication(savedJob, JobApplicationStatus.SAVED);
        entityManager.persistAndFlush(app1);

        JobApplication app2 = new JobApplication(savedJob, JobApplicationStatus.APPLIED);
        assertThatThrownBy(() -> entityManager.persistAndFlush(app2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Persist parsed requirements JSON and match breakdown JSON")
    void shouldPersistParsedRequirementsAndMatchBreakdownJson() {
        JobListing job = new JobListing("AI Research Engineer", "Anthropic", "San Francisco, CA",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "AI engineer role");

        ObjectNode requirements = objectMapper.createObjectNode();
        ArrayNode requiredSkills = requirements.putArray("requiredSkills");
        requiredSkills.add("PyTorch").add("CUDA").add("Distributed Training");
        requirements.put("experienceYears", 5);
        job.setParsedRequirements(requirements);

        JobListing savedJob = entityManager.persistAndFlush(job);

        JobApplication app = new JobApplication(savedJob, JobApplicationStatus.INTERVIEWING);
        app.setMatchScore(new BigDecimal("92.50"));

        ObjectNode breakdown = objectMapper.createObjectNode();
        breakdown.put("overallScore", 92.50);
        ArrayNode matched = breakdown.putArray("matchedSkills");
        matched.add("PyTorch").add("Distributed Training");
        breakdown.put("rationale", "Candidate exceeds distributed systems experience requirements.");
        app.setMatchBreakdown(breakdown);

        JobApplication savedApp = entityManager.persistAndFlush(app);

        entityManager.clear();

        JobListing foundJob = entityManager.find(JobListing.class, savedJob.getId());
        assertThat(foundJob.getParsedRequirements()).isNotNull();
        assertThat(foundJob.getParsedRequirements().get("experienceYears").asInt()).isEqualTo(5);
        assertThat(foundJob.getParsedRequirements().get("requiredSkills").get(0).asText()).isEqualTo("PyTorch");

        JobApplication foundApp = entityManager.find(JobApplication.class, savedApp.getId());
        assertThat(foundApp.getMatchScore()).isEqualByComparingTo(new BigDecimal("92.50"));
        assertThat(foundApp.getMatchBreakdown()).isNotNull();
        assertThat(foundApp.getMatchBreakdown().get("rationale").asText()).contains("distributed systems");
    }

    @Test
    @DisplayName("Coexist cleanly with existing Workflow entities in persistence context")
    void shouldCoexistWithExistingWorkflowEntities() throws Exception {
        // Persist existing platform workflow entity
        Workflow workflow = new Workflow("Job Search Sync Workflow", "Syncs jobs", objectMapper.readTree("{\"nodes\":[]}"), true);
        Workflow savedWorkflow = entityManager.persistAndFlush(workflow);

        // Persist Job Search Assistant entities
        CandidateProfile profile = new CandidateProfile("Grace Hopper", "grace.hopper@example.com");
        CandidateProfile savedProfile = entityManager.persistAndFlush(profile);

        JobListing job = new JobListing("Compiler Engineer", "Naval Research", "Arlington, VA",
                WorkplaceType.ON_SITE, EmploymentType.FULL_TIME, "Compilers role");
        JobListing savedJob = entityManager.persistAndFlush(job);

        entityManager.clear();

        assertThat(entityManager.find(Workflow.class, savedWorkflow.getId())).isNotNull();
        assertThat(entityManager.find(CandidateProfile.class, savedProfile.getId())).isNotNull();
        assertThat(entityManager.find(JobListing.class, savedJob.getId())).isNotNull();
    }
}
