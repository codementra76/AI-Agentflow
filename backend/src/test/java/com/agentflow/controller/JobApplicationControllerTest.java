package com.agentflow.controller;

import com.agentflow.dto.job.JobApplicationCountsResponse;
import com.agentflow.dto.job.JobApplicationResponse;
import com.agentflow.dto.job.JobApplicationUpdateRequest;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.JobApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobApplicationController.class)
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobApplicationService applicationService;

    private JobApplicationResponse sampleApp;

    @BeforeEach
    void setUp() {
        sampleApp = new JobApplicationResponse(
            50L,
            100L,
            "Staff Systems Architect",
            "Datadog",
            10L,
            "Backend Lead v1",
            JobApplicationStatus.APPLIED,
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 27),
            "Applied through referral",
            new BigDecimal("92.50"),
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/applications returns paginated applications")
    void shouldGetApplicationsWithPagination() throws Exception {
        when(applicationService.getApplications(eq(JobApplicationStatus.APPLIED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleApp)));

        mockMvc.perform(get("/api/v1/applications")
                .param("status", "APPLIED")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "appliedDate,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(50))
            .andExpect(jsonPath("$.data.content[0].status").value("APPLIED"))
            .andExpect(jsonPath("$.data.content[0].jobCompany").value("Datadog"));
    }

    @Test
    @DisplayName("GET /api/v1/applications/stats returns application counts by status")
    void shouldGetApplicationStats() throws Exception {
        JobApplicationCountsResponse stats = new JobApplicationCountsResponse(
            3L, 5L, 2L, 0L, 0L, 0L, 10L
        );
        when(applicationService.getApplicationCounts()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/applications/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.saved").value(3))
            .andExpect(jsonPath("$.data.applied").value(5))
            .andExpect(jsonPath("$.data.total").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/applications/follow-ups returns due follow-ups")
    void shouldGetDueFollowUps() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 31);
        when(applicationService.getDueFollowUps(date)).thenReturn(List.of(sampleApp));

        mockMvc.perform(get("/api/v1/applications/follow-ups")
                .param("through", "2026-10-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(50))
            .andExpect(jsonPath("$.data[0].followUpDate").value("2026-10-27"));
    }

    @Test
    @DisplayName("GET /api/v1/applications/follow-ups returns 400 on invalid date format")
    void shouldReturn400OnInvalidDateFormat() throws Exception {
        mockMvc.perform(get("/api/v1/applications/follow-ups")
                .param("through", "not-a-date"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/applications/by-job/{jobId} returns application for listing")
    void shouldGetApplicationByJobId() throws Exception {
        when(applicationService.getApplicationByJobId(100L)).thenReturn(sampleApp);

        mockMvc.perform(get("/api/v1/applications/by-job/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(50))
            .andExpect(jsonPath("$.data.jobId").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/applications/{id} returns application by ID")
    void shouldGetApplicationById() throws Exception {
        when(applicationService.getApplicationById(50L)).thenReturn(sampleApp);

        mockMvc.perform(get("/api/v1/applications/50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/applications/{id} returns 404 when ID not found")
    void shouldReturn404WhenApplicationNotFound() throws Exception {
        when(applicationService.getApplicationById(999L))
            .thenThrow(new ResourceNotFoundException("Job application not found with ID: 999"));

        mockMvc.perform(get("/api/v1/applications/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/v1/applications/{id} updates application tracking details")
    void shouldUpdateApplicationDetails() throws Exception {
        JobApplicationUpdateRequest updateReq = new JobApplicationUpdateRequest(
            JobApplicationStatus.INTERVIEWING,
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 11, 5),
            "Technical screen scheduled",
            10L,
            new BigDecimal("95.00"),
            null
        );

        when(applicationService.updateApplication(eq(50L), any(JobApplicationUpdateRequest.class)))
            .thenReturn(sampleApp);

        mockMvc.perform(put("/api/v1/applications/50")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Application updated successfully"));
    }

    @Test
    @DisplayName("PUT /api/v1/applications/{id} returns 400 when required status is missing")
    void shouldRejectMissingStatus() throws Exception {
        JobApplicationUpdateRequest invalid = new JobApplicationUpdateRequest(
            null, null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/applications/50")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.status").exists());
    }
}
