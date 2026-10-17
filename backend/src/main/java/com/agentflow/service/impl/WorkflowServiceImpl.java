package com.agentflow.service.impl;

import com.agentflow.dto.WorkflowActivePatchRequest;
import com.agentflow.dto.WorkflowCreateRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.dto.WorkflowUpdateRequest;
import com.agentflow.entity.Workflow;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;

    public WorkflowServiceImpl(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public List<WorkflowResponse> getAllWorkflows(Boolean active) {
        List<Workflow> workflows = (active != null)
            ? workflowRepository.findByActive(active)
            : workflowRepository.findAll();

        return workflows.stream()
            .map(WorkflowResponse::fromEntity)
            .toList();
    }

    @Override
    public WorkflowResponse getWorkflowById(Long id) {
        Workflow workflow = findWorkflowOrThrow(id);
        return WorkflowResponse.fromEntity(workflow);
    }

    @Override
    @Transactional
    public WorkflowResponse createWorkflow(WorkflowCreateRequest request) {
        String trimmedName = request.name().trim();
        if (workflowRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ConflictException("A workflow with name '" + trimmedName + "' already exists");
        }

        boolean isActive = request.active() == null || request.active();
        String description = request.description() != null ? request.description().trim() : null;

        Workflow workflow = new Workflow(
            trimmedName,
            description,
            request.workflowDefinition(),
            isActive
        );

        Workflow saved = workflowRepository.save(workflow);
        return WorkflowResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public WorkflowResponse updateWorkflow(Long id, WorkflowUpdateRequest request) {
        Workflow existing = findWorkflowOrThrow(id);
        String trimmedName = request.name().trim();

        if (workflowRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException("Another workflow with name '" + trimmedName + "' already exists");
        }

        existing.setName(trimmedName);
        existing.setDescription(request.description() != null ? request.description().trim() : null);
        existing.setWorkflowDefinition(request.workflowDefinition());
        if (request.active() != null) {
            existing.setActive(request.active());
        }

        Workflow updated = workflowRepository.save(existing);
        return WorkflowResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public WorkflowResponse updateWorkflowActiveStatus(Long id, WorkflowActivePatchRequest request) {
        Workflow existing = findWorkflowOrThrow(id);
        existing.setActive(request.active());

        Workflow updated = workflowRepository.save(existing);
        return WorkflowResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteWorkflow(Long id) {
        Workflow existing = findWorkflowOrThrow(id);
        workflowRepository.delete(existing);
    }

    private Workflow findWorkflowOrThrow(Long id) {
        return workflowRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + id));
    }
}
