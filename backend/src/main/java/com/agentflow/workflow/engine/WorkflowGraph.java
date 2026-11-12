package com.agentflow.workflow.engine;

import java.util.*;

public class WorkflowGraph {
    private final List<WorkflowNode> nodes;
    private final List<WorkflowEdge> edges;
    private final Map<String, WorkflowNode> nodeMap;
    private final Map<String, List<String>> incomingEdges;
    private final Map<String, List<String>> outgoingEdges;
    private final List<WorkflowNode> executionOrder;
    private final Map<String, List<WorkflowEdge>> outgoingEdgeMap;
    private final Map<String, List<WorkflowEdge>> incomingEdgeMap;

    public WorkflowGraph(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> incomingEdges,
            Map<String, List<String>> outgoingEdges,
            List<WorkflowNode> executionOrder) {
        this(nodes, edges, nodeMap, incomingEdges, outgoingEdges, executionOrder, null, null);
    }

    public WorkflowGraph(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> incomingEdges,
            Map<String, List<String>> outgoingEdges,
            List<WorkflowNode> executionOrder,
            Map<String, List<WorkflowEdge>> outgoingEdgeMap,
            Map<String, List<WorkflowEdge>> incomingEdgeMap) {
        this.nodes = nodes;
        this.edges = edges;
        this.nodeMap = nodeMap;
        this.incomingEdges = incomingEdges;
        this.outgoingEdges = outgoingEdges;
        this.executionOrder = executionOrder;
        this.outgoingEdgeMap = outgoingEdgeMap != null ? outgoingEdgeMap : computeEdgeMap(edges, true);
        this.incomingEdgeMap = incomingEdgeMap != null ? incomingEdgeMap : computeEdgeMap(edges, false);
    }

    private static Map<String, List<WorkflowEdge>> computeEdgeMap(List<WorkflowEdge> edges, boolean bySource) {
        Map<String, List<WorkflowEdge>> map = new HashMap<>();
        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                String key = bySource ? edge.source() : edge.target();
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
            }
        }
        return map;
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

    public List<WorkflowEdge> getOutgoingEdges(String nodeId) {
        return outgoingEdgeMap.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<WorkflowEdge> getIncomingEdges(String nodeId) {
        return incomingEdgeMap.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<WorkflowNode> getExecutionOrder() {
        return executionOrder;
    }
}
