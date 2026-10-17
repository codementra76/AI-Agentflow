package com.agentflow.workflow.engine.executor;

import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.TransformOperation;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextTransformExecutor implements NodeExecutor {

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        ObjectMapper mapper = context.getObjectMapper();
        JsonNode config = node.config();

        String opStr = null;
        if (config != null && config.hasNonNull("operation")) {
            opStr = config.get("operation").asText();
        } else if (config != null && config.hasNonNull("op")) {
            opStr = config.get("op").asText();
        }

        if (opStr == null || opStr.trim().isEmpty()) {
            throw new WorkflowExecutionException("TEXT_TRANSFORM node '" + node.id() + "' requires an 'operation' parameter (UPPERCASE, LOWERCASE, TRIM, REPLACE)");
        }

        TransformOperation operation = TransformOperation.fromString(opStr);
        if (operation == null) {
            throw new WorkflowExecutionException("Unsupported TEXT_TRANSFORM operation '" + opStr + "' in node '" + node.id() + "'. Supported: UPPERCASE, LOWERCASE, TRIM, REPLACE");
        }

        // Get incoming text
        String inputText = extractInputText(node, sourceNodeIds, context);

        String transformed;
        switch (operation) {
            case UPPERCASE -> transformed = inputText.toUpperCase();
            case LOWERCASE -> transformed = inputText.toLowerCase();
            case TRIM -> transformed = inputText.trim();
            case REPLACE -> {
                String find = null;
                if (config != null) {
                    if (config.hasNonNull("find")) {
                        find = config.get("find").asText();
                    } else if (config.hasNonNull("target")) {
                        find = config.get("target").asText();
                    } else if (config.hasNonNull("search")) {
                        find = config.get("search").asText();
                    }
                }
                if (find == null || find.isEmpty()) {
                    throw new WorkflowExecutionException("REPLACE operation in node '" + node.id() + "' requires 'find' (or 'search'/'target') parameter");
                }
                String replace = "";
                if (config != null) {
                    if (config.hasNonNull("replace")) {
                        replace = config.get("replace").asText();
                    } else if (config.hasNonNull("replacement")) {
                        replace = config.get("replacement").asText();
                    }
                }
                transformed = inputText.replace(find, replace);
            }
            default -> throw new WorkflowExecutionException("Unhandled transform operation: " + operation);
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("text", transformed);
        result.put("operation", operation.name());
        result.put("original", inputText);
        result.put("nodeId", node.id());
        return result;
    }

    private String extractInputText(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        JsonNode incoming = context.getIncomingData(node.id(), sourceNodeIds);
        if (incoming == null || incoming.isNull()) {
            return "";
        }
        if (incoming.isTextual()) {
            return incoming.asText();
        }
        if (incoming.hasNonNull("text")) {
            return incoming.get("text").asText();
        }
        if (incoming.hasNonNull("value")) {
            return incoming.get("value").asText();
        }
        if (incoming.hasNonNull("output")) {
            JsonNode out = incoming.get("output");
            return out.isTextual() ? out.asText() : out.toString();
        }
        if (incoming.hasNonNull("result")) {
            JsonNode res = incoming.get("result");
            return res.isTextual() ? res.asText() : res.toString();
        }
        return incoming.toString();
    }
}
