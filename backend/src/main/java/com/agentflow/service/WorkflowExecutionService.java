package com.agentflow.service;

import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;

import java.util.List;

public interface WorkflowExecutionService {
    WorkflowExecutionResponse executeWorkflow(Long workflowId, WorkflowExecutionRequest request);
    List<WorkflowExecutionResponse> getAllExecutions(Long workflowId, String status);
    WorkflowExecutionResponse getExecutionById(Long executionId);
    List<WorkflowExecutionResponse> getExecutionsByWorkflowId(Long workflowId);
}
