package com.agentflow.workflow;

public record WorkflowStep(
    String stepId,
    String name,
    String type,
    String agentRole
) {}
