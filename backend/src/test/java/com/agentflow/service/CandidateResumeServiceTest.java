package com.agentflow.service;

import com.agentflow.dto.job.CandidateResumeCreateRequest;
import com.agentflow.dto.job.CandidateResumeResponse;
import com.agentflow.dto.job.CandidateResumeUpdateRequest;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.entity.CandidateResume;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.repository.CandidateResumeRepository;
import com.agentflow.service.impl.CandidateResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateResumeServiceTest {

    @Mock
    private CandidateResumeRepository resumeRepository;

    @Mock
    private CandidateProfileRepository profileRepository;

    @InjectMocks
    private CandidateResumeServiceImpl resumeService;

    private CandidateProfile sampleProfile;
    private CandidateResume sampleResume;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfile("Bob Jones", "bob@example.com");
        sampleProfile.setId(10L);

        sampleResume = new CandidateResume(sampleProfile, "Backend v1", "# Bob Jones Resume", true);
        sampleResume.setId(100L);
    }

    @Test
    @DisplayName("Create first resume and automatically mark as primary")
    void shouldCreateFirstResumeAsPrimaryAutomatically() {
        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "Backend v1", "# Bob Jones Resume Content", false, null
        );

        when(profileRepository.findById(10L)).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(10L, "Backend v1")).thenReturn(false);
        when(resumeRepository.countByProfileId(10L)).thenReturn(0L); // first resume
        when(resumeRepository.save(any(CandidateResume.class))).thenAnswer(invocation -> {
            CandidateResume r = invocation.getArgument(0);
            r.setId(100L);
            return r;
        });

        CandidateResumeResponse response = resumeService.createResume(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.isPrimary()).isTrue(); // auto-promoted since it's the first
        assertThat(response.versionName()).isEqualTo("Backend v1");
    }

    @Test
    @DisplayName("Unset previous primary when new resume is marked primary")
    void shouldUnsetPreviousPrimaryWhenNewPrimaryResumeCreated() {
        CandidateResume existingPrimary = new CandidateResume(sampleProfile, "Old Primary", "Old content", true);
        existingPrimary.setId(99L);

        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "Fullstack v1", "New content", true, null
        );

        when(profileRepository.findById(10L)).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(10L, "Fullstack v1")).thenReturn(false);
        when(resumeRepository.countByProfileId(10L)).thenReturn(1L);
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(10L)).thenReturn(Optional.of(existingPrimary));
        when(resumeRepository.save(any(CandidateResume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateResumeResponse response = resumeService.createResume(10L, request);

        assertThat(response.isPrimary()).isTrue();
        assertThat(existingPrimary.isPrimary()).isFalse();
        verify(resumeRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Reject resume creation with duplicate version name for same profile")
    void shouldRejectDuplicateVersionNameOnCreate() {
        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "Backend v1", "Content", false, null
        );

        when(profileRepository.findById(10L)).thenReturn(Optional.of(sampleProfile));
        when(resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(10L, "Backend v1")).thenReturn(true);

        assertThatThrownBy(() -> resumeService.createResume(10L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Reject resume creation with empty content")
    void shouldRejectEmptyResumeContent() {
        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "Version A", "   ", false, null
        );

        when(profileRepository.findById(10L)).thenReturn(Optional.of(sampleProfile));

        assertThatThrownBy(() -> resumeService.createResume(10L, request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("Set primary resume switches primary flag cleanly")
    void shouldSetPrimaryResume() {
        CandidateResume oldPrimary = new CandidateResume(sampleProfile, "Old", "Old", true);
        oldPrimary.setId(100L);

        CandidateResume newPrimary = new CandidateResume(sampleProfile, "New", "New", false);
        newPrimary.setId(101L);

        when(resumeRepository.findById(101L)).thenReturn(Optional.of(newPrimary));
        when(resumeRepository.findByProfileIdAndIsPrimaryTrue(10L)).thenReturn(Optional.of(oldPrimary));
        when(resumeRepository.save(any(CandidateResume.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateResumeResponse response = resumeService.setPrimaryResume(101L);

        assertThat(response.isPrimary()).isTrue();
        assertThat(oldPrimary.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("Delete primary resume auto-promotes remaining newest resume")
    void shouldAutoPromoteNewPrimaryOnDelete() {
        CandidateResume remaining = new CandidateResume(sampleProfile, "Remaining v2", "Remaining text", false);
        remaining.setId(101L);

        when(resumeRepository.findById(100L)).thenReturn(Optional.of(sampleResume));
        when(resumeRepository.findByProfileIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(remaining));

        resumeService.deleteResume(100L);

        verify(resumeRepository).delete(sampleResume);
        assertThat(remaining.isPrimary()).isTrue();
        verify(resumeRepository).save(remaining);
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when profile does not exist")
    void shouldThrowWhenProfileNotFound() {
        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "v1", "content", true, null
        );

        when(profileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.createResume(999L, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }
}
