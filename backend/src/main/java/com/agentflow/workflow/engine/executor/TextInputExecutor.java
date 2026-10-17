package com.agentflow.workflow.engine.executor;

import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextInputExecutor implements NodeExecutor {

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        ObjectMapper mapper = context.getObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        String text = null;
        JsonNode config = node.config();
        if (config != null && !config.isNull()) {
            if (config.hasNonNull("text")) {
                text = config.get("text").asText();
            } else if (config.hasNonNull("value")) {
                text = config.get("value").asText();
            } else if (config.isTextual()) {
                text = config.asText();
            }
        }

        // If text not in config, check incoming data from upstream
        if (text == null) {
            JsonNode incoming = context.getIncomingData(node.id(), sourceNodeIds);
            if (incoming != null && !incoming.isNull()) {
                if (incoming.hasNonNull("text")) {
                    text = incoming.get("text").asText();
                } else if (incoming.hasNonNull("value")) {
                    text = incoming.get("value").asText();
                } else if (incoming.isTextual()) {
                    text = incoming.asText();
                }
            }
        }

        if (text == null) {
            text = "";
        }

        result.put("text", text);
        result.put("value", text);
        result.put("nodeId", node.id());
        return result;
    }
}
