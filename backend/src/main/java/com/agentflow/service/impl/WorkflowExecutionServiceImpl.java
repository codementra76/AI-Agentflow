package com.agentflow.service.impl;

import com.agentflow.dto.NodeExecutionLogResponse;
import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.entity.NodeExecutionLog;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.service.WorkflowExecutionService;
import com.agentflow.workflow.engine.WorkflowExecutionEngine;
import com.agentflow.workflow.engine.WorkflowGraph;
import com.agentflow.workflow.engine.WorkflowValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final NodeExecutionLogRepository nodeLogRepository;
    private final WorkflowValidator validator;
    private final WorkflowExecutionEngine engine;

    public WorkflowExecutionServiceImpl(
            WorkflowRepository workflowRepository,
            WorkflowExecutionRepository executionRepository,
            NodeExecutionLogRepository nodeLogRepository,
            WorkflowValidator validator,
            WorkflowExecutionEngine engine) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.nodeLogRepository = nodeLogRepository;
        this.validator = validator;
        this.engine = engine;
    }

    @Override
    @Transactional
    public WorkflowExecutionResponse executeWorkflow(Long workflowId, WorkflowExecutionRequest request) {
        Workflow workflow = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + workflowId));

        if (!workflow.isActive()) {
            throw new AgentFlowException("Cannot execute inactive workflow with id: " + workflowId);
        }

        // Validate graph structure and detect cycles prior to starting execution
        WorkflowGraph graph = validator.validateAndBuild(workflow.getWorkflowDefinition());

        JsonNode inputData = request != null ? request.inputData() : null;

        // Create initial execution record
        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflow(workflow);
        execution.setStatus("RUNNING");
        execution.setInputData(inputData);
        execution.setStartedAt(Instant.now());
        execution = executionRepository.save(execution);

        // Execute workflow graph in topological order
        execution = engine.run(graph, execution);

        List<NodeExecutionLogResponse> logs = loadNodeLogs(execution.getId());
        return WorkflowExecutionResponse.fromEntity(execution, logs);
    }

    @Override
    public List<WorkflowExecutionResponse> getAllExecutions(Long workflowId, String status) {
        List<WorkflowExecution> executions;

        if (workflowId != null && status != null && !status.trim().isEmpty()) {
            executions = executionRepository.findByWorkflowIdAndStatusOrderByStartedAtDesc(workflowId, status.trim());
        } else if (workflowId != null) {
            executions = executionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId);
        } else if (status != null && !status.trim().isEmpty()) {
            executions = executionRepository.findByStatusOrderByStartedAtDesc(status.trim());
        } else {
            executions = executionRepository.findAllByOrderByStartedAtDesc();
        }

        return executions.stream()
            .map(e -> WorkflowExecutionResponse.fromEntity(e, loadNodeLogs(e.getId())))
            .toList();
    }

    @Override
    public WorkflowExecutionResponse getExecutionById(Long executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execution not found with id: " + executionId));

        List<NodeExecutionLogResponse> logs = loadNodeLogs(executionId);
        return WorkflowExecutionResponse.fromEntity(execution, logs);
    }

    @Override
    public List<WorkflowExecutionResponse> getExecutionsByWorkflowId(Long workflowId) {
        if (!workflowRepository.existsById(workflowId)) {
            throw new ResourceNotFoundException("Workflow not found with id: " + workflowId);
        }

        List<WorkflowExecution> executions = executionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId);
        return executions.stream()
            .map(e -> WorkflowExecutionResponse.fromEntity(e, loadNodeLogs(e.getId())))
            .toList();
    }

    private List<NodeExecutionLogResponse> loadNodeLogs(Long executionId) {
        List<NodeExecutionLog> logs = nodeLogRepository.findByWorkflowExecutionIdOrderByIdAsc(executionId);
        return logs.stream()
            .map(NodeExecutionLogResponse::fromEntity)
            .toList();
    }
}
