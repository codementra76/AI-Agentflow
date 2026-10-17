package com.agentflow.service;

import com.agentflow.dto.WorkflowActivePatchRequest;
import com.agentflow.dto.WorkflowCreateRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.dto.WorkflowUpdateRequest;

import java.util.List;

public interface WorkflowService {
    List<WorkflowResponse> getAllWorkflows(Boolean active);
    WorkflowResponse getWorkflowById(Long id);
    WorkflowResponse createWorkflow(WorkflowCreateRequest request);
    WorkflowResponse updateWorkflow(Long id, WorkflowUpdateRequest request);
    WorkflowResponse updateWorkflowActiveStatus(Long id, WorkflowActivePatchRequest request);
    void deleteWorkflow(Long id);
}
