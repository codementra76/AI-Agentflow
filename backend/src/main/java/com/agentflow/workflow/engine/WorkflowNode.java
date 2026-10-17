package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowNode(
    String id,
    NodeType type,
    String name,
    JsonNode config
) {}
