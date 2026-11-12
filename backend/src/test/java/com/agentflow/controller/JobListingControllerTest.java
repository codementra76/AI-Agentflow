package com.agentflow.controller;

import com.agentflow.dto.job.*;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.entity.WorkplaceType;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.JobListingService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobListingController.class)
class JobListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobListingService listingService;

    private JobListingResponse sampleListing;
    private JobApplicationResponse sampleApp;

    @BeforeEach
    void setUp() {
        sampleListing = new JobListingResponse(
            100L,
            "Staff Systems Architect",
            "Datadog",
            "Seattle, WA",
            WorkplaceType.HYBRID,
            EmploymentType.FULL_TIME,
            "$200,000 - $240,000",
            "manual",
            "https://careers.datadog.com/jobs/123",
            "hash12345",
            "Architecture description",
            null,
            Instant.now(),
            Instant.now()
        );

        sampleApp = new JobApplicationResponse(
            200L,
            100L,
            "Staff Systems Architect",
            "Datadog",
            null,
            null,
            JobApplicationStatus.SAVED,
            null,
            null,
            null,
            BigDecimal.ZERO,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns paginated job listings")
    void shouldSearchJobsWithPagination() throws Exception {
        when(listingService.searchJobListings(eq("architect"), eq(WorkplaceType.HYBRID), eq(EmploymentType.FULL_TIME), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleListing)));

        mockMvc.perform(get("/api/v1/jobs")
                .param("search", "architect")
                .param("workplaceType", "HYBRID")
                .param("employmentType", "FULL_TIME")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "createdAt,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(100))
            .andExpect(jsonPath("$.data.content[0].title").value("Staff Systems Architect"))
            .andExpect(jsonPath("$.data.content[0].company").value("Datadog"));
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns 400 on invalid enum parameter")
    void shouldReturn400OnInvalidEnum() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                .param("workplaceType", "INVALID_TYPE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns job listing by ID")
    void shouldGetJobById() throws Exception {
        when(listingService.getJobListingById(100L)).thenReturn(sampleListing);

        mockMvc.perform(get("/api/v1/jobs/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.title").value("Staff Systems Architect"));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns 404 when ID not found")
    void shouldReturn404WhenJobNotFound() throws Exception {
        when(listingService.getJobListingById(999L))
            .thenThrow(new ResourceNotFoundException("Job listing not found with ID: 999"));

        mockMvc.perform(get("/api/v1/jobs/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/jobs creates new job listing returning 201 CREATED")
    void shouldCreateNewJobListing() throws Exception {
        JobListingCreateRequest request = new JobListingCreateRequest(
            "Staff Systems Architect", "Datadog", "Seattle, WA",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME,
            "$200k", "manual", "https://datadog.com/jobs/1",
            "Description", null, JobApplicationStatus.SAVED
        );

        JobListingSaveResult result = JobListingSaveResult.created(sampleListing, sampleApp);
        when(listingService.createJobListing(any(JobListingCreateRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isDuplicate").value(false))
            .andExpect(jsonPath("$.data.listing.id").value(100))
            .andExpect(jsonPath("$.data.application.status").value("SAVED"));
    }

    @Test
    @DisplayName("POST /api/v1/jobs returns 200 OK with duplicate indicator when listing already exists")
    void shouldHandleDuplicateJobListingCleanly() throws Exception {
        JobListingCreateRequest request = new JobListingCreateRequest(
            "Staff Systems Architect", "Datadog", "Seattle, WA",
            WorkplaceType.HYBRID, EmploymentType.FULL_TIME,
            "$200k", "manual", "https://datadog.com/jobs/1",
            "Description", null, null
        );

        JobListingSaveResult dupResult = JobListingSaveResult.duplicate(sampleListing, sampleApp);
        when(listingService.createJobListing(any(JobListingCreateRequest.class))).thenReturn(dupResult);

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isDuplicate").value(true))
            .andExpect(jsonPath("$.data.listing.id").value(100))
            .andExpect(jsonPath("$.message").value(dupResult.message()));
    }

    @Test
    @DisplayName("POST /api/v1/jobs rejects blank required fields with 400 BAD_REQUEST")
    void shouldRejectInvalidJobPayload() throws Exception {
        JobListingCreateRequest invalid = new JobListingCreateRequest(
            "", "", null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.title").exists())
            .andExpect(jsonPath("$.validationErrors.company").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/jobs/{id} updates job listing details")
    void shouldUpdateJobListing() throws Exception {
        JobListingUpdateRequest updateReq = new JobListingUpdateRequest(
            "Principal Architect", "Datadog", "Remote",
            WorkplaceType.REMOTE, EmploymentType.FULL_TIME,
            "$250k", "manual", "https://datadog.com/jobs/1",
            "Updated Description", null
        );

        when(listingService.updateJobListing(eq(100L), any(JobListingUpdateRequest.class)))
            .thenReturn(sampleListing);

        mockMvc.perform(put("/api/v1/jobs/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Job listing updated successfully"));
    }

    @Test
    @DisplayName("DELETE /api/v1/jobs/{id} deletes listing returning 204")
    void shouldDeleteJobListing() throws Exception {
        doNothing().when(listingService).deleteJobListing(100L);

        mockMvc.perform(delete("/api/v1/jobs/100"))
            .andExpect(status().isNoContent());

        verify(listingService).deleteJobListing(100L);
    }
}
