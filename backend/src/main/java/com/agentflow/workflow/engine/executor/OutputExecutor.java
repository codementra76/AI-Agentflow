package com.agentflow.workflow.engine.executor;

import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutputExecutor implements NodeExecutor {

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        ObjectMapper mapper = context.getObjectMapper();
        JsonNode incoming = context.getIncomingData(node.id(), sourceNodeIds);

        ObjectNode result = mapper.createObjectNode();
        result.put("nodeId", node.id());
        result.put("status", "SUCCESS");

        if (incoming != null && !incoming.isNull()) {
            if (incoming.hasNonNull("text")) {
                result.put("result", incoming.get("text").asText());
            } else if (incoming.hasNonNull("value")) {
                result.put("result", incoming.get("value").asText());
            } else if (incoming.isTextual()) {
                result.put("result", incoming.asText());
            } else {
                result.set("result", incoming);
            }
            result.set("data", incoming);
        } else {
            result.put("result", "");
        }

        // Set as final workflow output
        context.setFinalOutput(result);

        return result;
    }
}
