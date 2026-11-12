package com.agentflow.controller;

import com.agentflow.dto.job.JobMatchRequest;
import com.agentflow.dto.job.JobMatchResultResponse;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.JobMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobMatchingController.class)
class JobMatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobMatchingService matchingService;

    private JobMatchResultResponse sampleResult;

    @BeforeEach
    void setUp() {
        ObjectNode breakdown = objectMapper.createObjectNode();
        breakdown.put("summary", "Candidate is a solid technical fit.");
        breakdown.put("isFallback", false);

        sampleResult = new JobMatchResultResponse(
            50L,
            100L,
            "Senior Backend Engineer",
            "Acme Corp",
            10L,
            "Master Resume v2",
            new BigDecimal("84.50"),
            breakdown,
            Instant.parse("2026-10-25T14:30:00Z"),
            false
        );
    }

    @Test
    @DisplayName("POST /api/v1/jobs/{jobId}/match returns 200 OK and match result")
    void shouldReturn200OnMatchJob() throws Exception {
        when(matchingService.matchJobAndResume(eq(100L), eq(10L))).thenReturn(sampleResult);

        JobMatchRequest request = new JobMatchRequest(10L);

        mockMvc.perform(post("/api/v1/jobs/100/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId").value(100))
            .andExpect(jsonPath("$.data.matchScore").value(84.50))
            .andExpect(jsonPath("$.data.resumeVersionName").value("Master Resume v2"))
            .andExpect(jsonPath("$.data.isFallback").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/{jobId}/match with empty body resolves primary resume")
    void shouldAcceptEmptyBodyOnMatchJob() throws Exception {
        when(matchingService.matchJobAndResume(eq(100L), eq(null))).thenReturn(sampleResult);

        mockMvc.perform(post("/api/v1/jobs/100/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{jobId}/match returns saved match result")
    void shouldReturnSavedMatchResult() throws Exception {
        when(matchingService.getStoredMatchResult(100L)).thenReturn(sampleResult);

        mockMvc.perform(get("/api/v1/jobs/100/match"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.jobId").value(100))
            .andExpect(jsonPath("$.data.matchScore").value(84.50));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{jobId}/match returns 404 when not found")
    void shouldReturn404WhenSavedMatchNotFound() throws Exception {
        when(matchingService.getStoredMatchResult(999L))
            .thenThrow(new ResourceNotFoundException("No match analysis found for job id: 999"));

        mockMvc.perform(get("/api/v1/jobs/999/match"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("No match analysis found for job id: 999"));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/{jobId}/match returns 400 when job description is blank")
    void shouldReturn400WhenJobDescriptionBlank() throws Exception {
        when(matchingService.matchJobAndResume(eq(100L), any()))
            .thenThrow(new AgentFlowException("Job listing has no description content to evaluate"));

        mockMvc.perform(post("/api/v1/jobs/100/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Job listing has no description content to evaluate"));
    }
}

