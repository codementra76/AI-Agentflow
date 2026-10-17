package com.agentflow.service;

import com.agentflow.dto.WorkflowActivePatchRequest;
import com.agentflow.dto.WorkflowCreateRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.dto.WorkflowUpdateRequest;
import com.agentflow.entity.Workflow;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.service.impl.WorkflowServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    private Workflow sampleWorkflow;
    private ObjectNode sampleGraph;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        sampleGraph = mapper.createObjectNode();
        sampleGraph.put("version", "1.0");

        sampleWorkflow = new Workflow("Customer Support Workflow", "Automates customer inquiries", sampleGraph, true);
        sampleWorkflow.setId(1L);
    }

    @Test
    @DisplayName("getAllWorkflows without active filter returns all workflows")
    void shouldReturnAllWorkflows() {
        when(workflowRepository.findAll()).thenReturn(List.of(sampleWorkflow));

        List<WorkflowResponse> result = workflowService.getAllWorkflows(null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Customer Support Workflow");
        verify(workflowRepository, times(1)).findAll();
        verify(workflowRepository, never()).findByActive(anyBoolean());
    }

    @Test
    @DisplayName("getAllWorkflows with active filter calls findByActive")
    void shouldReturnFilteredWorkflows() {
        when(workflowRepository.findByActive(true)).thenReturn(List.of(sampleWorkflow));

        List<WorkflowResponse> result = workflowService.getAllWorkflows(true);

        assertThat(result).hasSize(1);
        verify(workflowRepository, times(1)).findByActive(true);
    }

    @Test
    @DisplayName("getWorkflowById returns workflow if found")
    void shouldReturnWorkflowWhenFound() {
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));

        WorkflowResponse result = workflowService.getWorkflowById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Customer Support Workflow");
        assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("getWorkflowById throws ResourceNotFoundException when missing")
    void shouldThrowExceptionWhenWorkflowNotFound() {
        when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowService.getWorkflowById(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Workflow not found with id: 99");
    }

    @Test
    @DisplayName("createWorkflow saves and returns new workflow with graph")
    void shouldCreateWorkflowSuccessfully() {
        WorkflowCreateRequest request = new WorkflowCreateRequest("Data Sync Workflow", "Syncs CRM data", sampleGraph, true);
        when(workflowRepository.existsByNameIgnoreCase("Data Sync Workflow")).thenReturn(false);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> {
            Workflow def = invocation.getArgument(0);
            def.setId(10L);
            return def;
        });

        WorkflowResponse created = workflowService.createWorkflow(request);

        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.name()).isEqualTo("Data Sync Workflow");
        assertThat(created.active()).isTrue();
        assertThat(created.workflowDefinition()).isEqualTo(sampleGraph);
    }

    @Test
    @DisplayName("createWorkflow throws ConflictException if name already exists")
    void shouldThrowExceptionOnDuplicateName() {
        WorkflowCreateRequest request = new WorkflowCreateRequest("Customer Support Workflow", "Duplicate name", sampleGraph, true);
        when(workflowRepository.existsByNameIgnoreCase("Customer Support Workflow")).thenReturn(true);

        assertThatThrownBy(() -> workflowService.createWorkflow(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateWorkflow updates fields and persists")
    void shouldUpdateWorkflowSuccessfully() {
        WorkflowUpdateRequest request = new WorkflowUpdateRequest("Updated Workflow", "Updated Description", sampleGraph, false);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));
        when(workflowRepository.existsByNameIgnoreCaseAndIdNot("Updated Workflow", 1L)).thenReturn(false);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowResponse updated = workflowService.updateWorkflow(1L, request);

        assertThat(updated.name()).isEqualTo("Updated Workflow");
        assertThat(updated.description()).isEqualTo("Updated Description");
        assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("updateWorkflow throws ConflictException when renaming to existing workflow")
    void shouldThrowConflictWhenUpdatingToDuplicateName() {
        WorkflowUpdateRequest request = new WorkflowUpdateRequest("Existing Workflow", "Desc", sampleGraph, true);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));
        when(workflowRepository.existsByNameIgnoreCaseAndIdNot("Existing Workflow", 1L)).thenReturn(true);

        assertThatThrownBy(() -> workflowService.updateWorkflow(1L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("updateWorkflowActiveStatus toggles active state")
    void shouldUpdateActiveStatus() {
        WorkflowActivePatchRequest request = new WorkflowActivePatchRequest(false);
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowResponse updated = workflowService.updateWorkflowActiveStatus(1L, request);

        assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("deleteWorkflow deletes entity when found")
    void shouldDeleteWorkflowWhenFound() {
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(sampleWorkflow));

        workflowService.deleteWorkflow(1L);

        verify(workflowRepository, times(1)).delete(sampleWorkflow);
    }

    @Test
    @DisplayName("deleteWorkflow throws ResourceNotFoundException when missing")
    void shouldThrowExceptionWhenDeletingMissingWorkflow() {
        when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowService.deleteWorkflow(99L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Workflow not found with id: 99");

        verify(workflowRepository, never()).delete(any());
    }
}
