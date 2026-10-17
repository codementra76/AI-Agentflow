package com.agentflow.dto;

import com.agentflow.entity.Workflow;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record WorkflowResponse(
    Long id,
    String name,
    String description,
    JsonNode workflowDefinition,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkflowResponse fromEntity(Workflow entity) {
        return new WorkflowResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getWorkflowDefinition(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
