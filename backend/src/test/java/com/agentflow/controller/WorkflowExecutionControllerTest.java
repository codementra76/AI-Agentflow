package com.agentflow.controller;

import com.agentflow.dto.NodeExecutionLogResponse;
import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.service.WorkflowExecutionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowExecutionController.class)
class WorkflowExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkflowExecutionService executionService;

    private WorkflowExecutionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        NodeExecutionLogResponse log1 = new NodeExecutionLogResponse(
            1L, 10L, "trig-1", "MANUAL_TRIGGER", "COMPLETED",
            null, objectMapper.createObjectNode().put("status", "TRIGGERED"),
            null, Instant.now(), Instant.now(), Instant.now()
        );

        sampleResponse = new WorkflowExecutionResponse(
            10L, 1L, "Sample Workflow", "COMPLETED",
            objectMapper.createObjectNode().put("input", "test"),
            objectMapper.createObjectNode().put("result", "TEST"),
            null, Instant.now(), Instant.now(), Instant.now(), Instant.now(),
            List.of(log1)
        );
    }

    @Test
    @DisplayName("POST /api/v1/workflows/{id}/execute returns execution response")
    void shouldExecuteWorkflow() throws Exception {
        when(executionService.executeWorkflow(eq(1L), any())).thenReturn(sampleResponse);

        WorkflowExecutionRequest request = new WorkflowExecutionRequest(
            objectMapper.createObjectNode().put("input", "test")
        );

        mockMvc.perform(post("/api/v1/workflows/1/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.workflowId").value(1))
            .andExpect(jsonPath("$.data.outputData.result").value("TEST"))
            .andExpect(jsonPath("$.data.nodeLogs").isArray())
            .andExpect(jsonPath("$.data.nodeLogs[0].nodeId").value("trig-1"));
    }

    @Test
    @DisplayName("GET /api/v1/executions returns all executions")
    void shouldReturnAllExecutions() throws Exception {
        when(executionService.getAllExecutions(null, null)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(10))
            .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/executions/{id} returns single execution")
    void shouldReturnExecutionById() throws Exception {
        when(executionService.getExecutionById(10L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/executions/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.nodeLogs[0].nodeType").value("MANUAL_TRIGGER"));
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id}/executions returns executions for workflow")
    void shouldReturnExecutionsByWorkflowId() throws Exception {
        when(executionService.getExecutionsByWorkflowId(1L)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/workflows/1/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].workflowId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/executions/{id} returns 404 when not found")
    void shouldReturn404WhenExecutionNotFound() throws Exception {
        when(executionService.getExecutionById(99L))
            .thenThrow(new ResourceNotFoundException("Execution not found with id: 99"));

        mockMvc.perform(get("/api/v1/executions/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Execution not found with id: 99"));
    }
}
