package com.agentflow.workflow.engine.executor;

import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface NodeExecutor {
    JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context);
}
