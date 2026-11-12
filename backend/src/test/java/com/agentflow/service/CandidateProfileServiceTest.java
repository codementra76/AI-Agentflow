package com.agentflow.service;

import com.agentflow.dto.job.CandidateProfileRequest;
import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.service.impl.CandidateProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateProfileServiceTest {

    @Mock
    private CandidateProfileRepository profileRepository;

    @InjectMocks
    private CandidateProfileServiceImpl profileService;

    private CandidateProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfile("Alice Smith", "alice@example.com");
        sampleProfile.setId(1L);
        sampleProfile.setHeadline("Software Architect");
    }

    @Test
    @DisplayName("Create candidate profile successfully with normalized email")
    void shouldCreateProfileSuccessfully() {
        CandidateProfileRequest request = new CandidateProfileRequest(
            "Alice Smith", "  ALICE@EXAMPLE.COM ", "+1-555-0100", "Software Architect",
            "Seattle, WA", "https://alicesmith.dev", "https://linkedin.com/in/alice",
            "https://github.com/alice", "Experienced architect"
        );

        when(profileRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> {
            CandidateProfile p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        CandidateProfileResponse response = profileService.createProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Alice Smith");
        assertThat(response.email()).isEqualTo("alice@example.com"); // normalized
        assertThat(response.headline()).isEqualTo("Software Architect");
    }

    @Test
    @DisplayName("Reject candidate profile creation when email already exists")
    void shouldRejectDuplicateEmailOnCreate() {
        CandidateProfileRequest request = new CandidateProfileRequest(
            "Bob Jones", "existing@example.com", null, null, null, null, null, null, null
        );

        when(profileRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> profileService.createProfile(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject candidate profile with malformed URL")
    void shouldRejectMalformedUrl() {
        CandidateProfileRequest request = new CandidateProfileRequest(
            "Carol Danvers", "carol@example.com", null, null, null, "invalid-url", null, null, null
        );

        assertThatThrownBy(() -> profileService.createProfile(request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Invalid URL format");

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update candidate profile successfully")
    void shouldUpdateProfileSuccessfully() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(profileRepository.existsByEmailIgnoreCaseAndIdNot("alice.new@example.com", 1L)).thenReturn(false);
        when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateProfileRequest request = new CandidateProfileRequest(
            "Alice W. Smith", "alice.new@example.com", "+1-555-9999", "Principal Architect",
            "San Francisco, CA", null, null, null, "Updated summary"
        );

        CandidateProfileResponse response = profileService.updateProfile(1L, request);

        assertThat(response.fullName()).isEqualTo("Alice W. Smith");
        assertThat(response.email()).isEqualTo("alice.new@example.com");
        assertThat(response.headline()).isEqualTo("Principal Architect");
    }

    @Test
    @DisplayName("Reject candidate profile update when new email belongs to another profile")
    void shouldRejectDuplicateEmailOnUpdate() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(profileRepository.existsByEmailIgnoreCaseAndIdNot("other@example.com", 1L)).thenReturn(true);

        CandidateProfileRequest request = new CandidateProfileRequest(
            "Alice Smith", "other@example.com", null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Get current profile returns first created profile")
    void shouldGetCurrentProfile() {
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(sampleProfile));

        CandidateProfileResponse response = profileService.getCurrentProfile();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when no candidate profile exists")
    void shouldThrowWhenNoProfileExists() {
        when(profileRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getCurrentProfile())
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No candidate profile found");
    }

    @Test
    @DisplayName("Delete candidate profile successfully")
    void shouldDeleteProfileSuccessfully() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));

        profileService.deleteProfile(1L);

        verify(profileRepository).delete(sampleProfile);
    }
}
