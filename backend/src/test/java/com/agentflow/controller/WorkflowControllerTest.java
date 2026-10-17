package com.agentflow.controller;

import com.agentflow.dto.WorkflowActivePatchRequest;
import com.agentflow.dto.WorkflowCreateRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.dto.WorkflowUpdateRequest;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.WorkflowService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkflowService workflowService;

    private ObjectNode sampleGraph;
    private WorkflowResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleGraph = objectMapper.createObjectNode();
        sampleGraph.put("version", "1.0");

        sampleResponse = new WorkflowResponse(
            1L, "Test Workflow", "Description", sampleGraph, true, Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/workflows returns list of workflows")
    void shouldReturnWorkflowList() throws Exception {
        when(workflowService.getAllWorkflows(null)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/workflows"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].name").value("Test Workflow"))
            .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/workflows?active=true returns filtered list")
    void shouldReturnFilteredWorkflowList() throws Exception {
        when(workflowService.getAllWorkflows(true)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/workflows").param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id} returns single workflow")
    void shouldReturnSingleWorkflow() throws Exception {
        when(workflowService.getWorkflowById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/workflows/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.name").value("Test Workflow"));
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id} returns 404 when workflow missing")
    void shouldReturnNotFoundWhenMissing() throws Exception {
        when(workflowService.getWorkflowById(99L))
            .thenThrow(new ResourceNotFoundException("Workflow not found with id: 99"));

        mockMvc.perform(get("/api/v1/workflows/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Workflow not found with id: 99"));
    }

    @Test
    @DisplayName("POST /api/v1/workflows creates new workflow with 201 Created")
    void shouldCreateWorkflow() throws Exception {
        WorkflowCreateRequest request = new WorkflowCreateRequest("New Workflow", "Desc", sampleGraph, true);
        when(workflowService.createWorkflow(any(WorkflowCreateRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.name").value("Test Workflow"));
    }

    @Test
    @DisplayName("POST /api/v1/workflows returns 400 Bad Request on invalid input")
    void shouldReturnBadRequestOnInvalidCreate() throws Exception {
        WorkflowCreateRequest invalidRequest = new WorkflowCreateRequest("", "", null, null);

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workflows returns 409 Conflict when name exists")
    void shouldReturnConflictOnDuplicateName() throws Exception {
        WorkflowCreateRequest request = new WorkflowCreateRequest("Duplicate", "Desc", null, null);
        when(workflowService.createWorkflow(any(WorkflowCreateRequest.class)))
            .thenThrow(new ConflictException("A workflow with name 'Duplicate' already exists"));

        mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("A workflow with name 'Duplicate' already exists"));
    }

    @Test
    @DisplayName("PUT /api/v1/workflows/{id} updates workflow with 200 OK")
    void shouldUpdateWorkflow() throws Exception {
        WorkflowUpdateRequest request = new WorkflowUpdateRequest("Updated Name", "Updated Desc", sampleGraph, false);
        WorkflowResponse updatedResponse = new WorkflowResponse(
            1L, "Updated Name", "Updated Desc", sampleGraph, false, Instant.now(), Instant.now()
        );
        when(workflowService.updateWorkflow(eq(1L), any(WorkflowUpdateRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/workflows/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Updated Name"))
            .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/workflows/{id} returns 404 when workflow missing")
    void shouldReturnNotFoundOnPutMissing() throws Exception {
        WorkflowUpdateRequest request = new WorkflowUpdateRequest("Updated Name", "Updated Desc", null, true);
        when(workflowService.updateWorkflow(eq(99L), any(WorkflowUpdateRequest.class)))
            .thenThrow(new ResourceNotFoundException("Workflow not found with id: 99"));

        mockMvc.perform(put("/api/v1/workflows/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/workflows/{id}/active toggles active status with 200 OK")
    void shouldPatchActiveStatus() throws Exception {
        WorkflowActivePatchRequest patchRequest = new WorkflowActivePatchRequest(false);
        WorkflowResponse patchedResponse = new WorkflowResponse(
            1L, "Test Workflow", "Description", sampleGraph, false, Instant.now(), Instant.now()
        );
        when(workflowService.updateWorkflowActiveStatus(eq(1L), any(WorkflowActivePatchRequest.class))).thenReturn(patchedResponse);

        mockMvc.perform(patch("/api/v1/workflows/1/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/workflows/{id} deletes workflow with 204 No Content")
    void shouldDeleteWorkflow() throws Exception {
        doNothing().when(workflowService).deleteWorkflow(1L);

        mockMvc.perform(delete("/api/v1/workflows/1"))
            .andExpect(status().isNoContent());

        verify(workflowService, times(1)).deleteWorkflow(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/workflows/{id} returns 404 when workflow missing")
    void shouldReturnNotFoundOnDeleteMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Workflow not found with id: 99"))
            .when(workflowService).deleteWorkflow(99L);

        mockMvc.perform(delete("/api/v1/workflows/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }
}
