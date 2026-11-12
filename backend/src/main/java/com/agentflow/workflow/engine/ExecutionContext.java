package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Execution context carrying state during the execution of a workflow instance.
 *
 * Stores initial workflow inputs, node execution outputs, workflow variables,
 * and provides template resolution via {@link VariableResolver}.
 */
public class ExecutionContext {
    private final Long workflowId;
    private final Long executionId;
    private final JsonNode initialInputData;
    private final ObjectMapper objectMapper;
    private final VariableResolver variableResolver;
    private final Map<String, JsonNode> nodeOutputs = new LinkedHashMap<>();
    private JsonNode workflowVariables;
    private JsonNode finalOutput;

    public ExecutionContext(Long workflowId, Long executionId, JsonNode initialInputData, ObjectMapper objectMapper) {
        this(workflowId, executionId, initialInputData, objectMapper, null, null);
    }

    public ExecutionContext(
            Long workflowId,
            Long executionId,
            JsonNode initialInputData,
            ObjectMapper objectMapper,
            VariableResolver variableResolver) {
        this(workflowId, executionId, initialInputData, objectMapper, variableResolver, null);
    }

    public ExecutionContext(
            Long workflowId,
            Long executionId,
            JsonNode initialInputData,
            ObjectMapper objectMapper,
            VariableResolver variableResolver,
            JsonNode workflowVariables) {
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.initialInputData = initialInputData != null ? initialInputData : this.objectMapper.createObjectNode();
        this.variableResolver = variableResolver != null ? variableResolver : new VariableResolver(this.objectMapper);
        this.workflowVariables = workflowVariables != null ? workflowVariables : this.objectMapper.createObjectNode();
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

    public VariableResolver getVariableResolver() {
        return variableResolver;
    }

    public JsonNode getWorkflowVariables() {
        return workflowVariables;
    }

    public void setWorkflowVariables(JsonNode workflowVariables) {
        this.workflowVariables = workflowVariables != null ? workflowVariables : objectMapper.createObjectNode();
    }

    public void setWorkflowVariable(String name, JsonNode value) {
        if (this.workflowVariables == null || !this.workflowVariables.isObject()) {
            this.workflowVariables = objectMapper.createObjectNode();
        }
        ((ObjectNode) this.workflowVariables).set(name, value);
    }

    public void setWorkflowVariable(String name, String value) {
        if (this.workflowVariables == null || !this.workflowVariables.isObject()) {
            this.workflowVariables = objectMapper.createObjectNode();
        }
        if (value == null) {
            ((ObjectNode) this.workflowVariables).putNull(name);
        } else {
            ((ObjectNode) this.workflowVariables).put(name, value);
        }
    }

    /**
     * Resolves variable placeholders in the given template using the current execution state.
     *
     * Supports:
     * - {{input.<field>}} or {{input}}
     * - {{nodes.<nodeId>.<field>}} or {{nodes.<nodeId>}} (only for already executed nodes)
     * - {{variables.<name>}} or {{variables}}
     *
     * @param template template string containing {{...}} placeholders
     * @return resolved string with placeholders replaced, leaving unresolved placeholders intact
     */
    public String resolve(String template) {
        if (template == null) {
            return null;
        }
        return variableResolver.resolve(template, initialInputData, nodeOutputs, workflowVariables);
    }

    /**
     * Resolves a single variable reference or template string directly to its underlying JsonNode.
     * Returns null if a single variable reference is unresolved or missing.
     *
     * @param template expression or template string
     * @return resolved JsonNode or null
     */
    public JsonNode resolveToNode(String template) {
        if (template == null) {
            return null;
        }
        return variableResolver.resolveToNode(template, initialInputData, nodeOutputs, workflowVariables);
    }

    /**
     * Alias for {@link #resolve(String)}.
     */
    public String resolveVariables(String template) {
        return resolve(template);
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

    private final Set<String> skippedNodeIds = new HashSet<>();

    public void markNodeSkipped(String nodeId) {
        if (nodeId != null) {
            skippedNodeIds.add(nodeId);
        }
    }

    public boolean isNodeSkipped(String nodeId) {
        return nodeId != null && skippedNodeIds.contains(nodeId);
    }

    public Set<String> getSkippedNodeIds() {
        return Collections.unmodifiableSet(skippedNodeIds);
    }

    public void setFinalOutput(JsonNode finalOutput) {
        this.finalOutput = finalOutput;
    }
}
