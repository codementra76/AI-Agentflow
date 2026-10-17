package com.agentflow.dto;

import com.agentflow.entity.NodeExecutionLog;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record NodeExecutionLogResponse(
    Long id,
    Long executionId,
    String nodeId,
    String nodeType,
    String status,
    JsonNode inputData,
    JsonNode outputData,
    String errorMessage,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt
) {
    public static NodeExecutionLogResponse fromEntity(NodeExecutionLog log) {
        return new NodeExecutionLogResponse(
            log.getId(),
            log.getWorkflowExecution() != null ? log.getWorkflowExecution().getId() : null,
            log.getNodeId(),
            log.getNodeType(),
            log.getStatus(),
            log.getInputData(),
            log.getOutputData(),
            log.getErrorMessage(),
            log.getStartedAt(),
            log.getCompletedAt(),
            log.getCreatedAt()
        );
    }
}
