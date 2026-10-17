package com.agentflow.workflow.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WorkflowGraph {
    private final List<WorkflowNode> nodes;
    private final List<WorkflowEdge> edges;
    private final Map<String, WorkflowNode> nodeMap;
    private final Map<String, List<String>> incomingEdges;
    private final Map<String, List<String>> outgoingEdges;
    private final List<WorkflowNode> executionOrder;

    public WorkflowGraph(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> incomingEdges,
            Map<String, List<String>> outgoingEdges,
            List<WorkflowNode> executionOrder) {
        this.nodes = nodes;
        this.edges = edges;
        this.nodeMap = nodeMap;
        this.incomingEdges = incomingEdges;
        this.outgoingEdges = outgoingEdges;
        this.executionOrder = executionOrder;
    }

    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    public List<WorkflowEdge> getEdges() {
        return edges;
    }

    public Map<String, WorkflowNode> getNodeMap() {
        return nodeMap;
    }

    public WorkflowNode getNode(String id) {
        return nodeMap.get(id);
    }

    public List<String> getIncomingSourceIds(String nodeId) {
        return incomingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<String> getOutgoingTargetIds(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<WorkflowNode> getExecutionOrder() {
        return executionOrder;
    }
}
