package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionContext {
    private final Long workflowId;
    private final Long executionId;
    private final JsonNode initialInputData;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> nodeOutputs = new LinkedHashMap<>();
    private JsonNode finalOutput;

    public ExecutionContext(Long workflowId, Long executionId, JsonNode initialInputData, ObjectMapper objectMapper) {
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.initialInputData = initialInputData != null ? initialInputData : objectMapper.createObjectNode();
        this.objectMapper = objectMapper;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public JsonNode getInitialInputData() {
        return initialInputData;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setNodeOutput(String nodeId, JsonNode output) {
        nodeOutputs.put(nodeId, output);
    }

    public JsonNode getNodeOutput(String nodeId) {
        return nodeOutputs.get(nodeId);
    }

    public Map<String, JsonNode> getAllNodeOutputs() {
        return Collections.unmodifiableMap(nodeOutputs);
    }

    public JsonNode getIncomingData(String nodeId, List<String> sourceNodeIds) {
        if (sourceNodeIds == null || sourceNodeIds.isEmpty()) {
            return initialInputData;
        }

        if (sourceNodeIds.size() == 1) {
            JsonNode single = nodeOutputs.get(sourceNodeIds.get(0));
            return single != null ? single : objectMapper.createObjectNode();
        }

        ObjectNode combined = objectMapper.createObjectNode();
        for (String sourceId : sourceNodeIds) {
            JsonNode out = nodeOutputs.get(sourceId);
            if (out != null) {
                combined.set(sourceId, out);
            }
        }
        return combined;
    }

    public JsonNode getFinalOutput() {
        return finalOutput != null ? finalOutput : objectMapper.createObjectNode();
    }

    public void setFinalOutput(JsonNode finalOutput) {
        this.finalOutput = finalOutput;
    }
}
