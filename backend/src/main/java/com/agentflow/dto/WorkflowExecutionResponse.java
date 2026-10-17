package com.agentflow.dto;

import com.agentflow.entity.WorkflowExecution;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record WorkflowExecutionResponse(
    Long id,
    Long workflowId,
    String workflowName,
    String status,
    JsonNode inputData,
    JsonNode outputData,
    String errorMessage,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    List<NodeExecutionLogResponse> nodeLogs
) {
    public static WorkflowExecutionResponse fromEntity(WorkflowExecution execution, List<NodeExecutionLogResponse> nodeLogs) {
        return new WorkflowExecutionResponse(
            execution.getId(),
            execution.getWorkflow() != null ? execution.getWorkflow().getId() : null,
            execution.getWorkflow() != null ? execution.getWorkflow().getName() : null,
            execution.getStatus(),
            execution.getInputData(),
            execution.getOutputData(),
            execution.getErrorMessage(),
            execution.getStartedAt(),
            execution.getCompletedAt(),
            execution.getCreatedAt(),
            execution.getUpdatedAt(),
            nodeLogs != null ? nodeLogs : List.of()
        );
    }
}
