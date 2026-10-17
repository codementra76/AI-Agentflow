package com.agentflow.workflow.engine.executor;

import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ManualTriggerExecutor implements NodeExecutor {

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        ObjectMapper mapper = context.getObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "TRIGGERED");
        result.put("nodeId", node.id());
        result.put("triggeredAt", Instant.now().toString());

        JsonNode initialInput = context.getInitialInputData();
        if (initialInput != null && !initialInput.isNull() && !initialInput.isEmpty()) {
            result.set("data", initialInput);
            if (initialInput.isTextual()) {
                result.put("text", initialInput.asText());
            } else if (initialInput.has("text")) {
                result.set("text", initialInput.get("text"));
            } else if (initialInput.has("input")) {
                result.set("text", initialInput.get("input"));
            }
        } else if (node.config() != null && !node.config().isNull()) {
            result.set("data", node.config());
            if (node.config().has("text")) {
                result.set("text", node.config().get("text"));
            }
        } else {
            result.put("text", "");
        }

        return result;
    }
}
