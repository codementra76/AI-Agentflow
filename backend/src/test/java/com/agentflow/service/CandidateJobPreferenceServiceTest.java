package com.agentflow.service;

import com.agentflow.dto.job.CandidateJobPreferenceRequest;
import com.agentflow.dto.job.CandidateJobPreferenceResponse;
import com.agentflow.entity.CandidateJobPreference;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateJobPreferenceRepository;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.service.impl.CandidateJobPreferenceServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateJobPreferenceServiceTest {

    @Mock
    private CandidateJobPreferenceRepository preferenceRepository;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CandidateJobPreferenceServiceImpl preferenceService;

    private CandidateProfile sampleProfile;
    private CandidateJobPreference samplePreference;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfile("Carol Danvers", "carol@example.com");
        sampleProfile.setId(20L);

        samplePreference = new CandidateJobPreference(sampleProfile);
        samplePreference.setId(200L);
        samplePreference.setCurrency("USD");
        samplePreference.setMinSalary(new BigDecimal("150000.00"));
    }

    @Test
    @DisplayName("Create job preferences successfully with valid lists and salary")
    void shouldCreateJobPreferencesSuccessfully() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            List.of("Staff Engineer", "Tech Lead"),
            List.of("Remote", "San Francisco, CA"),
            List.of("REMOTE", "HYBRID"),
            List.of("FULL_TIME"),
            new BigDecimal("185000.00"),
            "USD"
        );

        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(false);
        when(preferenceRepository.save(any(CandidateJobPreference.class))).thenAnswer(invocation -> {
            CandidateJobPreference p = invocation.getArgument(0);
            p.setId(200L);
            return p;
        });

        CandidateJobPreferenceResponse response = preferenceService.createPreferences(20L, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.minSalary()).isEqualByComparingTo(new BigDecimal("185000.00"));
        assertThat(response.targetTitles().get(0).asText()).isEqualTo("Staff Engineer");
    }

    @Test
    @DisplayName("Reject duplicate job preferences creation for same profile")
    void shouldRejectDuplicatePreferencesCreation() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            List.of("DevOps"), null, null, null, null, null
        );

        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(true);

        assertThatThrownBy(() -> preferenceService.createPreferences(20L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exist");
    }

    @Test
    @DisplayName("Reject negative minimum salary")
    void shouldRejectNegativeSalary() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            null, null, null, null, new BigDecimal("-100.00"), "USD"
        );

        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(false);

        assertThatThrownBy(() -> preferenceService.createPreferences(20L, request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Reject invalid workplace type value")
    void shouldRejectInvalidWorkplaceType() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            null, null, List.of("INVALID_TYPE"), null, null, "USD"
        );

        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(false);

        assertThatThrownBy(() -> preferenceService.createPreferences(20L, request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Invalid workplace type");
    }

    @Test
    @DisplayName("Reject invalid employment type value")
    void shouldRejectInvalidEmploymentType() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            null, null, null, List.of("UNKNOWN_EMPLOYMENT"), null, "USD"
        );

        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(false);

        assertThatThrownBy(() -> preferenceService.createPreferences(20L, request))
            .isInstanceOf(AgentFlowException.class)
            .hasMessageContaining("Invalid employment type");
    }

    @Test
    @DisplayName("Save or update creates preferences when none exist and updates when already present")
    void shouldUpsertPreferences() {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            List.of("Java Developer"), null, null, null, new BigDecimal("120000"), "USD"
        );

        // When does not exist -> creates
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(false);
        when(profileRepository.findById(20L)).thenReturn(Optional.of(sampleProfile));
        when(preferenceRepository.save(any(CandidateJobPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateJobPreferenceResponse created = preferenceService.saveOrUpdatePreferences(20L, request);
        assertThat(created).isNotNull();

        // When already exists -> updates
        when(preferenceRepository.existsByProfileId(20L)).thenReturn(true);
        when(profileRepository.existsById(20L)).thenReturn(true);
        when(preferenceRepository.findByProfileId(20L)).thenReturn(Optional.of(samplePreference));

        CandidateJobPreferenceResponse updated = preferenceService.saveOrUpdatePreferences(20L, request);
        assertThat(updated).isNotNull();
    }
}
