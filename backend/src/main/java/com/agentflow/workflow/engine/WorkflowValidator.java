package com.agentflow.workflow.engine;

import com.agentflow.exception.WorkflowValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkflowValidator {

    public WorkflowGraph validateAndBuild(JsonNode workflowDefinition) {
        if (workflowDefinition == null || workflowDefinition.isNull() || workflowDefinition.isEmpty()) {
            throw new WorkflowValidationException("Workflow definition must not be null or empty");
        }

        JsonNode nodesArray = workflowDefinition.get("nodes");
        if (nodesArray == null || !nodesArray.isArray() || nodesArray.isEmpty()) {
            throw new WorkflowValidationException("Workflow definition must contain a non-empty 'nodes' array");
        }

        List<WorkflowNode> nodes = new ArrayList<>();
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        boolean hasTrigger = false;
        boolean hasOutput = false;

        for (int i = 0; i < nodesArray.size(); i++) {
            JsonNode nodeJson = nodesArray.get(i);
            String id = extractText(nodeJson, "id");
            if (id == null || id.trim().isEmpty()) {
                throw new WorkflowValidationException("Node at index " + i + " is missing a valid 'id'");
            }
            id = id.trim();

            if (nodeMap.containsKey(id)) {
                throw new WorkflowValidationException("Duplicate node ID detected: '" + id + "'");
            }

            String typeStr = extractText(nodeJson, "type");
            if (typeStr == null || typeStr.trim().isEmpty()) {
                throw new WorkflowValidationException("Node '" + id + "' is missing 'type'");
            }

            NodeType type = NodeType.fromString(typeStr);
            if (type == null) {
                throw new WorkflowValidationException("Unsupported node type '" + typeStr + "' in node '" + id + "'. Allowed types: " + Arrays.toString(NodeType.values()));
            }

            if (type == NodeType.MANUAL_TRIGGER) {
                hasTrigger = true;
            }
            if (type == NodeType.OUTPUT) {
                hasOutput = true;
            }

            String name = extractText(nodeJson, "name");
            if (name == null) {
                name = extractText(nodeJson, "label");
            }
            if (name == null) {
                name = id;
            }

            JsonNode config = nodeJson.has("config") ? nodeJson.get("config") : nodeJson.get("data");

            WorkflowNode node = new WorkflowNode(id, type, name, config);
            nodes.add(node);
            nodeMap.put(id, node);
        }

        if (!hasTrigger) {
            throw new WorkflowValidationException("Workflow must contain at least one trigger node (e.g., MANUAL_TRIGGER)");
        }
        if (!hasOutput) {
            throw new WorkflowValidationException("Workflow must contain at least one OUTPUT node");
        }

        // Parse edges
        List<WorkflowEdge> edges = new ArrayList<>();
        Map<String, List<String>> incomingEdges = new HashMap<>();
        Map<String, List<String>> outgoingEdges = new HashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            incomingEdges.put(nodeId, new ArrayList<>());
            outgoingEdges.put(nodeId, new ArrayList<>());
        }

        JsonNode edgesArray = workflowDefinition.has("edges") ? workflowDefinition.get("edges") : workflowDefinition.get("connections");
        if (edgesArray != null && edgesArray.isArray()) {
            for (int i = 0; i < edgesArray.size(); i++) {
                JsonNode edgeJson = edgesArray.get(i);
                String source = extractText(edgeJson, "source");
                String target = extractText(edgeJson, "target");

                if (source == null || source.trim().isEmpty() || target == null || target.trim().isEmpty()) {
                    throw new WorkflowValidationException("Edge at index " + i + " must specify both 'source' and 'target'");
                }
                source = source.trim();
                target = target.trim();

                if (source.equals(target)) {
                    throw new WorkflowValidationException("Self-referencing edge is not allowed on node: '" + source + "'");
                }

                if (!nodeMap.containsKey(source)) {
                    throw new WorkflowValidationException("Edge refers to non-existent source node: '" + source + "'");
                }
                if (!nodeMap.containsKey(target)) {
                    throw new WorkflowValidationException("Edge refers to non-existent target node: '" + target + "'");
                }

                String edgeId = extractText(edgeJson, "id");
                if (edgeId == null) {
                    edgeId = "edge-" + source + "->" + target;
                }

                edges.add(new WorkflowEdge(edgeId, source, target));
                outgoingEdges.get(source).add(target);
                incomingEdges.get(target).add(source);
            }
        }

        // Cycle Detection & Topological Sort
        detectCycles(nodes, outgoingEdges);
        List<WorkflowNode> executionOrder = computeTopologicalOrder(nodes, nodeMap, incomingEdges, outgoingEdges);

        return new WorkflowGraph(nodes, edges, nodeMap, incomingEdges, outgoingEdges, executionOrder);
    }

    private void detectCycles(List<WorkflowNode> nodes, Map<String, List<String>> outgoingEdges) {
        // 0 = unvisited, 1 = visiting (in current DFS stack), 2 = visited
        Map<String, Integer> state = new HashMap<>();
        for (WorkflowNode node : nodes) {
            state.put(node.id(), 0);
        }

        List<String> currentPath = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            if (state.get(node.id()) == 0) {
                dfsCycleCheck(node.id(), outgoingEdges, state, currentPath);
            }
        }
    }

    private void dfsCycleCheck(String nodeId, Map<String, List<String>> outgoingEdges, Map<String, Integer> state, List<String> currentPath) {
        state.put(nodeId, 1);
        currentPath.add(nodeId);

        List<String> neighbors = outgoingEdges.getOrDefault(nodeId, Collections.emptyList());
        for (String neighbor : neighbors) {
            int neighborState = state.getOrDefault(neighbor, 0);
            if (neighborState == 1) {
                // Cycle found
                int cycleStartIndex = currentPath.indexOf(neighbor);
                List<String> cyclePath = new ArrayList<>(currentPath.subList(cycleStartIndex, currentPath.size()));
                cyclePath.add(neighbor);
                throw new WorkflowValidationException("Workflow contains a cycle: " + String.join(" -> ", cyclePath));
            } else if (neighborState == 0) {
                dfsCycleCheck(neighbor, outgoingEdges, state, currentPath);
            }
        }

        state.put(nodeId, 2);
        currentPath.remove(currentPath.size() - 1);
    }

    private List<WorkflowNode> computeTopologicalOrder(
            List<WorkflowNode> nodes,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> incomingEdges,
            Map<String, List<String>> outgoingEdges) {

        Map<String, Integer> inDegree = new HashMap<>();
        for (WorkflowNode node : nodes) {
            inDegree.put(node.id(), incomingEdges.get(node.id()).size());
        }

        Queue<String> queue = new LinkedList<>();
        // Enqueue all nodes with in-degree 0 (triggers or start nodes)
        for (WorkflowNode node : nodes) {
            if (inDegree.get(node.id()) == 0) {
                queue.add(node.id());
            }
        }

        List<WorkflowNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            order.add(nodeMap.get(u));

            for (String v : outgoingEdges.get(u)) {
                int newDegree = inDegree.get(v) - 1;
                inDegree.put(v, newDegree);
                if (newDegree == 0) {
                    queue.add(v);
                }
            }
        }

        if (order.size() != nodes.size()) {
            throw new WorkflowValidationException("Workflow graph contains unreachable or cyclic dependencies");
        }

        return order;
    }

    private String extractText(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }
}
