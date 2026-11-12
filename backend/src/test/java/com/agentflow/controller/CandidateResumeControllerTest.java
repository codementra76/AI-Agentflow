package com.agentflow.controller;

import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.dto.job.CandidateResumeCreateRequest;
import com.agentflow.dto.job.CandidateResumeResponse;
import com.agentflow.dto.job.CandidateResumeUpdateRequest;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.CandidateProfileService;
import com.agentflow.service.CandidateResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateResumeController.class)
class CandidateResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateResumeService resumeService;

    @MockitoBean
    private CandidateProfileService profileService;

    private CandidateResumeResponse sampleResume;
    private CandidateProfileResponse sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = new CandidateProfileResponse(
            1L, "Jane Doe", "jane.doe@example.com", null, null, null, null, null, null, null,
            Instant.now(), Instant.now()
        );

        sampleResume = new CandidateResumeResponse(
            10L,
            1L,
            "Backend Lead v1",
            "# Jane Doe\nStaff Software Engineer",
            true,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/resumes with profileId returns resumes")
    void shouldListResumesWithProfileId() throws Exception {
        when(resumeService.getResumesByProfileId(1L)).thenReturn(List.of(sampleResume));

        mockMvc.perform(get("/api/v1/candidate-profile/resumes?profileId=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(10))
            .andExpect(jsonPath("$.data[0].versionName").value("Backend Lead v1"))
            .andExpect(jsonPath("$.data[0].isPrimary").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/resumes without profileId resolves current profile")
    void shouldListResumesResolvingCurrentProfile() throws Exception {
        when(profileService.getCurrentProfile()).thenReturn(sampleProfile);
        when(resumeService.getResumesByProfileId(1L)).thenReturn(List.of(sampleResume));

        mockMvc.perform(get("/api/v1/candidate-profile/resumes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(10));

        verify(profileService).getCurrentProfile();
        verify(resumeService).getResumesByProfileId(1L);
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/resumes/primary returns primary resume")
    void shouldGetPrimaryResume() throws Exception {
        when(resumeService.getPrimaryResume(1L)).thenReturn(sampleResume);

        mockMvc.perform(get("/api/v1/candidate-profile/resumes/primary?profileId=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.isPrimary").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/resumes/{id} returns resume by ID")
    void shouldGetResumeById() throws Exception {
        when(resumeService.getResumeById(10L)).thenReturn(sampleResume);

        mockMvc.perform(get("/api/v1/candidate-profile/resumes/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/resumes/{id} returns 404 when resume missing")
    void shouldReturn404WhenResumeMissing() throws Exception {
        when(resumeService.getResumeById(99L))
            .thenThrow(new ResourceNotFoundException("Resume not found with ID: 99"));

        mockMvc.perform(get("/api/v1/candidate-profile/resumes/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/candidate-profile/resumes creates resume and returns 201")
    void shouldCreateResumeSuccessfully() throws Exception {
        CandidateResumeCreateRequest request = new CandidateResumeCreateRequest(
            "Backend Lead v1", "# Resume Text", true, null
        );

        when(resumeService.createResume(eq(1L), any(CandidateResumeCreateRequest.class)))
            .thenReturn(sampleResume);

        mockMvc.perform(post("/api/v1/candidate-profile/resumes?profileId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.message").value("Resume version created successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/candidate-profile/resumes rejects invalid request with 400")
    void shouldRejectInvalidResumePayload() throws Exception {
        CandidateResumeCreateRequest invalid = new CandidateResumeCreateRequest(
            "", "", false, null
        );

        mockMvc.perform(post("/api/v1/candidate-profile/resumes?profileId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.versionName").exists())
            .andExpect(jsonPath("$.validationErrors.rawText").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/candidate-profile/resumes/{id} updates resume version")
    void shouldUpdateResumeSuccessfully() throws Exception {
        CandidateResumeUpdateRequest updateReq = new CandidateResumeUpdateRequest(
            "Updated Version", "# Updated Content", false, null
        );

        when(resumeService.updateResume(eq(10L), any(CandidateResumeUpdateRequest.class)))
            .thenReturn(sampleResume);

        mockMvc.perform(put("/api/v1/candidate-profile/resumes/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Resume version updated successfully"));
    }

    @Test
    @DisplayName("PUT /api/v1/candidate-profile/resumes/{id}/primary switches primary flag")
    void shouldSetPrimaryResume() throws Exception {
        when(resumeService.setPrimaryResume(10L)).thenReturn(sampleResume);

        mockMvc.perform(put("/api/v1/candidate-profile/resumes/10/primary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isPrimary").value(true))
            .andExpect(jsonPath("$.message").value("Primary resume updated successfully"));
    }

    @Test
    @DisplayName("DELETE /api/v1/candidate-profile/resumes/{id} deletes resume returning 204")
    void shouldDeleteResumeSuccessfully() throws Exception {
        doNothing().when(resumeService).deleteResume(10L);

        mockMvc.perform(delete("/api/v1/candidate-profile/resumes/10"))
            .andExpect(status().isNoContent());

        verify(resumeService).deleteResume(10L);
    }
}
