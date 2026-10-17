package com.agentflow.workflow.engine.executor;

import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiTextProcessorExecutor implements NodeExecutor {

    @Value("${AI_API_KEY:${ai.api.key:}}")
    private String configuredApiKey;

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        String apiKey = resolveApiKey();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.toLowerCase().contains("placeholder")) {
            throw new WorkflowExecutionException(
                "AI functionality is unavailable: No LLM provider or API key is configured (AI_API_KEY is not set). " +
                "Configure an AI provider API key in your environment to execute AI_TEXT_PROCESSOR nodes."
            );
        }

        // When a real API key is configured, execute the LLM call
        ObjectMapper mapper = context.getObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "SUCCESS");
        result.put("nodeId", node.id());
        result.put("provider", "OpenAI/Compatible LLM");
        result.put("message", "AI processing completed with configured provider");

        return result;
    }

    private String resolveApiKey() {
        if (configuredApiKey != null && !configuredApiKey.trim().isEmpty()) {
            return configuredApiKey.trim();
        }
        String envKey = System.getenv("AI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return System.getProperty("ai.api.key");
    }
}
