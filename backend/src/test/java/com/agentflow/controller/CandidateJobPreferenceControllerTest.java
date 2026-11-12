package com.agentflow.controller;

import com.agentflow.dto.job.CandidateJobPreferenceRequest;
import com.agentflow.dto.job.CandidateJobPreferenceResponse;
import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.CandidateJobPreferenceService;
import com.agentflow.service.CandidateProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateJobPreferenceController.class)
class CandidateJobPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateJobPreferenceService preferenceService;

    @MockitoBean
    private CandidateProfileService profileService;

    private CandidateJobPreferenceResponse samplePreferences;
    private CandidateProfileResponse sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfileResponse(
            1L, "Jane Doe", "jane.doe@example.com", null, null, null, null, null, null, null,
            Instant.now(), Instant.now()
        );

        samplePreferences = new CandidateJobPreferenceResponse(
            5L,
            1L,
            objectMapper.valueToTree(List.of("Staff Engineer", "Backend Architect")),
            objectMapper.valueToTree(List.of("Seattle, WA", "Remote")),
            objectMapper.valueToTree(List.of("REMOTE", "HYBRID")),
            objectMapper.valueToTree(List.of("FULL_TIME")),
            new BigDecimal("185000.00"),
            "USD",
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/preferences returns preferences with profileId")
    void shouldGetPreferencesWithProfileId() throws Exception {
        when(preferenceService.getPreferencesByProfileId(1L)).thenReturn(samplePreferences);

        mockMvc.perform(get("/api/v1/candidate-profile/preferences?profileId=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.targetTitles[0]").value("Staff Engineer"));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/preferences without profileId resolves current profile")
    void shouldGetPreferencesResolvingProfile() throws Exception {
        when(profileService.getCurrentProfile()).thenReturn(sampleProfile);
        when(preferenceService.getPreferencesByProfileId(1L)).thenReturn(samplePreferences);

        mockMvc.perform(get("/api/v1/candidate-profile/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5));

        verify(profileService).getCurrentProfile();
        verify(preferenceService).getPreferencesByProfileId(1L);
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/preferences returns 404 when preferences missing")
    void shouldReturn404WhenPreferencesMissing() throws Exception {
        when(preferenceService.getPreferencesByProfileId(1L))
            .thenThrow(new ResourceNotFoundException("No job preferences found for profile id: 1"));

        mockMvc.perform(get("/api/v1/candidate-profile/preferences?profileId=1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/v1/candidate-profile/preferences saves or updates preferences")
    void shouldSaveOrUpdatePreferencesSuccessfully() throws Exception {
        CandidateJobPreferenceRequest request = new CandidateJobPreferenceRequest(
            List.of("Staff Engineer"),
            List.of("Remote"),
            List.of("REMOTE"),
            List.of("FULL_TIME"),
            new BigDecimal("190000.00"),
            "USD"
        );

        when(preferenceService.saveOrUpdatePreferences(eq(1L), any(CandidateJobPreferenceRequest.class)))
            .thenReturn(samplePreferences);

        mockMvc.perform(put("/api/v1/candidate-profile/preferences?profileId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5))
            .andExpect(jsonPath("$.message").value("Job preferences saved successfully"));
    }

    @Test
    @DisplayName("PUT /api/v1/candidate-profile/preferences rejects negative salary with 400")
    void shouldRejectNegativeSalary() throws Exception {
        CandidateJobPreferenceRequest invalid = new CandidateJobPreferenceRequest(
            null, null, null, null,
            new BigDecimal("-1000.00"),
            "USD"
        );

        mockMvc.perform(put("/api/v1/candidate-profile/preferences?profileId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.minSalary").exists());
    }
}
