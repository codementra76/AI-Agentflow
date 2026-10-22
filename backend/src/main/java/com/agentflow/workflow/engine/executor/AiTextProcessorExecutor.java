package com.agentflow.workflow.engine.executor;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.service.OllamaService;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class AiTextProcessorExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiTextProcessorExecutor.class);

    private final OllamaService ollamaService;

    public AiTextProcessorExecutor(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        // 1. Read configured prompt and optional model override from node.config()
        String configuredPrompt = null;
        String modelOverride = null;
        JsonNode config = node.config();

        if (config != null && !config.isNull()) {
            if (config.hasNonNull("prompt")) {
                configuredPrompt = config.get("prompt").asText();
            } else if (config.hasNonNull("instruction")) {
                configuredPrompt = config.get("instruction").asText();
            } else if (config.hasNonNull("template")) {
                configuredPrompt = config.get("template").asText();
            } else if (config.hasNonNull("text")) {
                configuredPrompt = config.get("text").asText();
            }

            if (config.hasNonNull("model")) {
                modelOverride = config.get("model").asText();
            }
        }

        // 2. Receive and extract incoming text from upstream nodes
        JsonNode incoming = context.getIncomingData(node.id(), sourceNodeIds);
        String upstreamText = extractIncomingText(incoming);

        // 3. Combine prompt and upstream input in a clear, predictable format
        String finalPrompt = buildFinalPrompt(configuredPrompt, upstreamText, node.id());

        // 4. Invoke OllamaService (logging prompt length only to protect user privacy)
        log.info("Executing AI_TEXT_PROCESSOR node [{}] (prompt length: {} chars, model override: {})",
            node.id(), finalPrompt.length(), modelOverride != null ? modelOverride : "default");

        AiChatResponse aiResponse;
        try {
            aiResponse = ollamaService.generateChat(finalPrompt, modelOverride);
        } catch (Exception ex) {
            log.warn("AI_TEXT_PROCESSOR node [{}] failed during LLM call: {}", node.id(), ex.getMessage());
            throw new WorkflowExecutionException("AI processing failed at node '" + node.id() + "': " + ex.getMessage(), ex);
        }

        if (aiResponse == null || aiResponse.response() == null) {
            throw new WorkflowExecutionException("AI processing returned null response at node '" + node.id() + "'");
        }

        // 5. Construct and return output structure for downstream nodes
        ObjectMapper mapper = context.getObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "SUCCESS");
        result.put("nodeId", node.id());
        result.put("text", aiResponse.response());
        result.put("result", aiResponse.response());
        result.put("model", aiResponse.model());

        return result;
    }

    private String buildFinalPrompt(String configuredPrompt, String upstreamText, String nodeId) {
        boolean hasPrompt = configuredPrompt != null && !configuredPrompt.trim().isEmpty();
        boolean hasInput = upstreamText != null && !upstreamText.trim().isEmpty();

        if (hasPrompt && hasInput) {
            return configuredPrompt.trim() + "\n\nInput:\n" + upstreamText.trim();
        } else if (hasPrompt) {
            return configuredPrompt.trim();
        } else if (hasInput) {
            return upstreamText.trim();
        } else {
            throw new WorkflowExecutionException(
                "AI_TEXT_PROCESSOR node '" + nodeId + "' requires a prompt in config or input text from an upstream node"
            );
        }
    }

    private String extractIncomingText(JsonNode incoming) {
        if (incoming == null || incoming.isNull()) {
            return "";
        }
        if (incoming.isTextual()) {
            return incoming.asText();
        }
        if (incoming.hasNonNull("text")) {
            return incoming.get("text").asText();
        }
        if (incoming.hasNonNull("result")) {
            JsonNode res = incoming.get("result");
            return res.isTextual() ? res.asText() : res.toString();
        }
        if (incoming.hasNonNull("value")) {
            return incoming.get("value").asText();
        }
        if (incoming.hasNonNull("output")) {
            JsonNode out = incoming.get("output");
            return out.isTextual() ? out.asText() : out.toString();
        }

        // Multi-source node output combined object
        if (incoming.isObject()) {
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = incoming.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode child = entry.getValue();
                String childText = null;
                if (child.isTextual()) {
                    childText = child.asText();
                } else if (child.hasNonNull("text")) {
                    childText = child.get("text").asText();
                } else if (child.hasNonNull("result")) {
                    JsonNode res = child.get("result");
                    childText = res.isTextual() ? res.asText() : res.toString();
                }
                if (childText != null && !childText.trim().isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(childText.trim());
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return "";
    }
}
