package com.agentflow.workflow.engine;

public record WorkflowEdge(
    String id,
    String source,
    String target
) {}
