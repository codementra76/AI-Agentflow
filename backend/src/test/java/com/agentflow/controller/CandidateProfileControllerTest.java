package com.agentflow.controller;

import com.agentflow.dto.job.CandidateProfileRequest;
import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateProfileController.class)
class CandidateProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CandidateProfileService profileService;

    private CandidateProfileResponse sampleResponse;
    private CandidateProfileRequest validRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = new CandidateProfileResponse(
            1L,
            "Jane Doe",
            "jane.doe@example.com",
            "+1-555-0199",
            "Staff Platform Engineer",
            "Seattle, WA",
            "https://janedoe.dev",
            "https://linkedin.com/in/janedoe",
            "https://github.com/janedoe",
            "Experienced distributed systems engineer.",
            Instant.now(),
            Instant.now()
        );

        validRequest = new CandidateProfileRequest(
            "Jane Doe",
            "jane.doe@example.com",
            "+1-555-0199",
            "Staff Platform Engineer",
            "Seattle, WA",
            "https://janedoe.dev",
            "https://linkedin.com/in/janedoe",
            "https://github.com/janedoe",
            "Experienced distributed systems engineer."
        );
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile returns current profile with 200 OK")
    void shouldReturnCurrentProfile() throws Exception {
        when(profileService.getCurrentProfile()).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/candidate-profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile returns 404 when profile does not exist")
    void shouldReturn404WhenCurrentProfileMissing() throws Exception {
        when(profileService.getCurrentProfile())
            .thenThrow(new ResourceNotFoundException("No candidate profile found."));

        mockMvc.perform(get("/api/v1/candidate-profile"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/{id} returns profile by ID with 200 OK")
    void shouldReturnProfileById() throws Exception {
        when(profileService.getProfileById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/candidate-profile/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.fullName").value("Jane Doe"));
    }

    @Test
    @DisplayName("GET /api/v1/candidate-profile/{id} returns 404 when ID not found")
    void shouldReturn404WhenIdNotFound() throws Exception {
        when(profileService.getProfileById(99L))
            .thenThrow(new ResourceNotFoundException("Candidate profile not found with ID: 99"));

        mockMvc.perform(get("/api/v1/candidate-profile/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/candidate-profile creates profile and returns 201 CREATED")
    void shouldCreateProfileSuccessfully() throws Exception {
        when(profileService.createProfile(any(CandidateProfileRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/candidate-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.message").value("Candidate profile created successfully"));
    }

    @Test
    @DisplayName("POST /api/v1/candidate-profile rejects invalid payload with 400 BAD_REQUEST")
    void shouldRejectInvalidPayload() throws Exception {
        CandidateProfileRequest invalidRequest = new CandidateProfileRequest(
            "", // blank fullName
            "not-an-email", // invalid email
            null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/candidate-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.fullName").exists())
            .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/v1/candidate-profile returns 409 CONFLICT on duplicate email")
    void shouldReturn409OnDuplicateEmail() throws Exception {
        when(profileService.createProfile(any(CandidateProfileRequest.class)))
            .thenThrow(new ConflictException("Candidate profile with email jane.doe@example.com already exists"));

        mockMvc.perform(post("/api/v1/candidate-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("PUT /api/v1/candidate-profile/{id} updates profile and returns 200 OK")
    void shouldUpdateProfileSuccessfully() throws Exception {
        when(profileService.updateProfile(eq(1L), any(CandidateProfileRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/candidate-profile/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.message").value("Candidate profile updated successfully"));
    }
}
