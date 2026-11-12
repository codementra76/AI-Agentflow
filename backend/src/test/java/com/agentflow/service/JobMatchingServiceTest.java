package com.agentflow.service;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobMatchResultResponse;
import com.agentflow.entity.*;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.repository.CandidateResumeRepository;
import com.agentflow.repository.JobApplicationRepository;
import com.agentflow.repository.JobListingRepository;
import com.agentflow.service.impl.JobMatchingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobMatchingServiceTest {

    @Mock
    private JobListingRepository jobListingRepository;

    @Mock
    private CandidateResumeRepository resumeRepository;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private JobApplicationRepository applicationRepository;

    @Mock
    private JobApplicationService jobApplicationService;

    @Mock
    private OllamaService ollamaService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobMatchingServiceImpl matchingService;

    private CandidateProfile sampleProfile;
    private CandidateResume sampleResume;
    private JobListing sampleJob;
    private JobApplication sampleApplication;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfile();
        sampleProfile.setId(1L);
        sampleProfile.setFullName("Jane Developer");
        sampleProfile.setEmail("jane@example.com");

        sampleResume = new CandidateResume();
        sampleResume.setId(10L);
        sampleResume.setProfile(sampleProfile);
        sampleResume.setVersionName("Senior Backend v1");
        sampleResume.setRawText("Experienced Software Engineer with 6+ years in Java, Spring Boot, MySQL, and Docker. Built scalable REST APIs.");
        sampleResume.setPrimary(true);

        sampleJob = new JobListing();
        sampleJob.setId(100L);
        sampleJob.setTitle("Senior Java Engineer");
        sampleJob.setCompany("Cloud Corp");
        sampleJob.setLocation("Remote");
        sampleJob.setRawDescription("We are looking for a Senior Java Engineer proficient in Java, Spring Boot, AWS, and Kubernetes to build high-scale microservices.");

        sampleApplication = new JobApplication(sampleJob);
        sampleApplication.setId(50L);
        sampleApplication.setStatus(JobApplicationStatus.INTERVIEWING);
        sampleApplication.setAppliedDate(LocalDate.of(2026, 10, 15));
        sampleApplication.setFollowUpDate(LocalDate.of(2026, 10, 25));
        sampleApplication.setNotes("Had great recruiter phone screen");
        sampleApplication.setResume(sampleResume);
    }

    @Test
    @DisplayName("Successful AI resume-to-job matching parses JSON and persists safely")
    void shouldSuccessfullyMatchJobAndResumeWithAi() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        String mockAiJson = """
            {
              "matchScore": 82.5,
              "summary": "Jane is a strong candidate with relevant Java and Spring Boot experience.",
              "matchingSkills": [
                { "skill": "Java", "evidence": "6+ years in Java" },
                { "skill": "Spring Boot", "evidence": "Production experience in Spring Boot" }
              ],
              "missingSkills": [
                { "skill": "Kubernetes", "requirement": "Required for container orchestration" }
              ],
              "unconfirmedSkills": [
                { "skill": "AWS", "note": "Cloud experience not explicitly detailed" }
              ],
              "strengths": [
                "Strong backend Java foundations"
              ],
              "gaps": [
                "Lacks direct Kubernetes mentions"
              ],
              "recommendations": [
                "Add Kubernetes or cloud deployment examples to project descriptions"
              ]
            }
            """;

        when(ollamaService.generateChat(anyString(), eq("qwen2.5:3b")))
            .thenReturn(new AiChatResponse(mockAiJson, "qwen2.5:3b"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        assertThat(response).isNotNull();
        assertThat(response.jobId()).isEqualTo(100L);
        assertThat(response.applicationId()).isEqualTo(50L);
        assertThat(response.resumeId()).isEqualTo(10L);
        assertThat(response.isFallback()).isFalse();
        assertThat(response.matchScore()).isNotNull();
        assertThat(response.matchScore().doubleValue()).isGreaterThan(0.0);
        assertThat(response.matchBreakdown()).isNotNull();
        assertThat(response.matchBreakdown().get("summary").asText()).contains("strong candidate");

        // Verify dedicated persistence was called with matchScore and matchBreakdown
        verify(jobApplicationService, times(1)).updateMatchResult(
            eq(50L), eq(10L), any(BigDecimal.class), any()
        );
    }

    @Test
    @DisplayName("Markdown code fences are cleanly stripped from AI response")
    void shouldStripMarkdownCodeFencesFromAiResponse() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findById(10L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        String fencedAiResponse = """
            ```json
            {
              "matchScore": 78.0,
              "summary": "Solid backend alignment with Java and Spring Boot.",
              "matchingSkills": [
                { "skill": "Java", "evidence": "Years of experience" }
              ],
              "missingSkills": [],
              "unconfirmedSkills": [],
              "strengths": ["Java proficiency"],
              "gaps": [],
              "recommendations": []
            }
            ```
            """;

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenReturn(new AiChatResponse(fencedAiResponse, "qwen2.5:3b"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, 10L);

        assertThat(response.isFallback()).isFalse();
        assertThat(response.matchBreakdown().get("summary").asText()).contains("Solid backend alignment");
    }

    @Test
    @DisplayName("Java does not falsely match JavaScript (word-boundary prevention)")
    void shouldNotFalselyMatchJavaInsideJavaScript() {
        sampleResume.setRawText("Full-stack developer working with JavaScript, Node.js, and React.");

        sampleJob.setRawDescription("We are looking for a core Java engineer with Spring Boot.");

        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenThrow(new AiServiceUnavailableException("Ollama unavailable"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        assertThat(response.isFallback()).isTrue();
        // Java should be missing because candidate only has JavaScript!
        boolean javaInMissing = false;
        for (var node : response.matchBreakdown().get("missingSkills")) {
            if ("Java".equalsIgnoreCase(node.get("skill").asText())) {
                javaInMissing = true;
                break;
            }
        }
        assertThat(javaInMissing).as("Java must be missing when candidate only has JavaScript").isTrue();
    }

    @Test
    @DisplayName("Skill aliases like springboot match Spring Boot case-insensitively")
    void shouldMatchSkillAliasesCaseInsensitively() {
        sampleResume.setRawText("Built microservices using springboot and MySQL.");
        sampleJob.setRawDescription("Requirements include Spring Boot and relational databases.");

        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenThrow(new AiServiceUnavailableException("Ollama down"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        boolean springBootMatched = false;
        for (var node : response.matchBreakdown().get("matchingSkills")) {
            if ("Spring Boot".equalsIgnoreCase(node.get("skill").asText())) {
                springBootMatched = true;
                break;
            }
        }
        assertThat(springBootMatched).as("'springboot' in resume must match 'Spring Boot' requirement").isTrue();
    }

    @Test
    @DisplayName("Ollama service unavailable falls back cleanly to deterministic scoring")
    void shouldFallbackCleanlyWhenOllamaIsUnavailable() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenThrow(new AiServiceUnavailableException("Connection refused at localhost:11434"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        assertThat(response).isNotNull();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.matchScore()).isNotNull();
        assertThat(response.matchBreakdown().get("summary").asText()).contains("Deterministic keyword match");
        verify(jobApplicationService, times(1)).updateMatchResult(
            eq(50L), eq(10L), any(BigDecimal.class), any()
        );
    }

    @Test
    @DisplayName("Malformed AI JSON safely falls back to deterministic analysis")
    void shouldFallbackWhenAiReturnsMalformedJson() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenReturn(new AiChatResponse("I am an AI and here is why the candidate matches...", "qwen2.5:3b"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        assertThat(response.isFallback()).isTrue();
        assertThat(response.matchScore()).isNotNull();
    }

    @Test
    @DisplayName("Out-of-range AI score is clamped to valid range [0, 100]")
    void shouldClampOutOfRangeAiScore() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(sampleResume));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        String outOfRangeJson = """
            {
              "matchScore": 150.0,
              "summary": "Candidate is beyond expectations.",
              "matchingSkills": [],
              "missingSkills": [],
              "unconfirmedSkills": [],
              "strengths": [],
              "gaps": [],
              "recommendations": []
            }
            """;

        when(ollamaService.generateChat(anyString(), anyString()))
            .thenReturn(new AiChatResponse(outOfRangeJson, "qwen2.5:3b"));

        JobMatchResultResponse response = matchingService.matchJobAndResume(100L, null);

        assertThat(response.matchScore().doubleValue()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when candidate has no resume")
    void shouldThrowWhenNoResumeExists() {
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(1L)).thenReturn(Optional.empty());
        when(resumeRepository.findByProfileIdOrderByCreatedAtDesc(1L)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> matchingService.matchJobAndResume(100L, null))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No resume found");
    }

    @Test
    @DisplayName("Throws AgentFlowException when resume belongs to another profile")
    void shouldThrowWhenResumeDoesNotBelongToProfile() {
        CandidateProfile otherProfile = new CandidateProfile();
        otherProfile.setId(999L);
        sampleResume.setProfile(otherProfile);

        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.findById(10L)).thenReturn(Optional.of(sampleResume));

        assertThatThrownBy(() -> matchingService.matchJobAndResume(100L, 10L))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("does not belong to the active candidate profile");
    }

    @Test
    @DisplayName("Throws AgentFlowException when job description is blank")
    void shouldThrowWhenJobDescriptionIsBlank() {
        sampleJob.setRawDescription("   ");
        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));

        assertThatThrownBy(() -> matchingService.matchJobAndResume(100L, null))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("no description content to evaluate");
    }

    @Test
    @DisplayName("getStoredMatchResult returns saved match result successfully")
    void shouldReturnStoredMatchResult() {
        sampleApplication.setMatchScore(new BigDecimal("88.00"));
        sampleApplication.setMatchBreakdown(objectMapper.createObjectNode()
            .put("summary", "Previously evaluated match.")
            .put("isFallback", false));

        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        JobMatchResultResponse response = matchingService.getStoredMatchResult(100L);

        assertThat(response).isNotNull();
        assertThat(response.matchScore()).isEqualTo(new BigDecimal("88.00"));
        assertThat(response.jobTitle()).isEqualTo("Senior Java Engineer");
    }

    @Test
    @DisplayName("getStoredMatchResult throws 404 when no analysis exists yet")
    void shouldThrowWhenNoSavedMatchExists() {
        sampleApplication.setMatchScore(null);
        sampleApplication.setMatchBreakdown(null);

        when(jobListingRepository.findById(100L)).thenReturn(Optional.of(sampleJob));
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));
        when(applicationRepository.findByJobId(100L)).thenReturn(Optional.of(sampleApplication));

        assertThatThrownBy(() -> matchingService.getStoredMatchResult(100L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No match analysis found");
    }
}

