package com.agentflow.workflow.engine;

public record WorkflowEdge(
    String id,
    String source,
    String target,
    String sourceHandle,
    String label
) {
    public WorkflowEdge(String id, String source, String target) {
        this(id, source, target, null, null);
    }
}
