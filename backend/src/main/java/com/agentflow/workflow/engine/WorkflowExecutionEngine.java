package com.agentflow.workflow.engine;

import com.agentflow.entity.NodeExecutionLog;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.workflow.engine.executor.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class WorkflowExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionEngine.class);

    private final ObjectMapper objectMapper;
    private final NodeExecutionLogRepository nodeLogRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final VariableResolver variableResolver;
    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    @Autowired
    public WorkflowExecutionEngine(
            ObjectMapper objectMapper,
            NodeExecutionLogRepository nodeLogRepository,
            WorkflowExecutionRepository executionRepository,
            VariableResolver variableResolver,
            ManualTriggerExecutor manualTriggerExecutor,
            TextInputExecutor textInputExecutor,
            TextTransformExecutor textTransformExecutor,
            AiTextProcessorExecutor aiTextProcessorExecutor,
            OutputExecutor outputExecutor,
            ConditionExecutor conditionExecutor) {
        this.objectMapper = objectMapper;
        this.nodeLogRepository = nodeLogRepository;
        this.executionRepository = executionRepository;
        this.variableResolver = variableResolver != null ? variableResolver : new VariableResolver(objectMapper);

        executors.put(NodeType.MANUAL_TRIGGER, manualTriggerExecutor);
        executors.put(NodeType.TEXT_INPUT, textInputExecutor);
        executors.put(NodeType.TEXT_TRANSFORM, textTransformExecutor);
        executors.put(NodeType.AI_TEXT_PROCESSOR, aiTextProcessorExecutor);
        executors.put(NodeType.OUTPUT, outputExecutor);
        executors.put(NodeType.CONDITION, conditionExecutor != null ? conditionExecutor : new ConditionExecutor(objectMapper));
    }

    public WorkflowExecutionEngine(
            ObjectMapper objectMapper,
            NodeExecutionLogRepository nodeLogRepository,
            WorkflowExecutionRepository executionRepository,
            VariableResolver variableResolver,
            ManualTriggerExecutor manualTriggerExecutor,
            TextInputExecutor textInputExecutor,
            TextTransformExecutor textTransformExecutor,
            AiTextProcessorExecutor aiTextProcessorExecutor,
            OutputExecutor outputExecutor) {
        this(objectMapper, nodeLogRepository, executionRepository, variableResolver,
             manualTriggerExecutor, textInputExecutor, textTransformExecutor, aiTextProcessorExecutor, outputExecutor,
             new ConditionExecutor(objectMapper));
    }

    public WorkflowExecutionEngine(
            ObjectMapper objectMapper,
            NodeExecutionLogRepository nodeLogRepository,
            WorkflowExecutionRepository executionRepository,
            ManualTriggerExecutor manualTriggerExecutor,
            TextInputExecutor textInputExecutor,
            TextTransformExecutor textTransformExecutor,
            AiTextProcessorExecutor aiTextProcessorExecutor,
            OutputExecutor outputExecutor) {
        this(objectMapper, nodeLogRepository, executionRepository, new VariableResolver(objectMapper),
             manualTriggerExecutor, textInputExecutor, textTransformExecutor, aiTextProcessorExecutor, outputExecutor,
             new ConditionExecutor(objectMapper));
    }

    public WorkflowExecution run(WorkflowGraph graph, WorkflowExecution execution) {
        JsonNode workflowVariables = null;
        if (execution.getWorkflow() != null && execution.getWorkflow().getWorkflowDefinition() != null) {
            JsonNode def = execution.getWorkflow().getWorkflowDefinition();
            if (def.hasNonNull("variables")) {
                workflowVariables = def.get("variables");
            }
        }

        ExecutionContext context = new ExecutionContext(
            execution.getWorkflow() != null ? execution.getWorkflow().getId() : null,
            execution.getId(),
            execution.getInputData(),
            objectMapper,
            variableResolver,
            workflowVariables
        );

        log.info("Starting execution [{}] for workflow [{}] with {} nodes in topological order",
            execution.getId(), execution.getWorkflow().getId(), graph.getExecutionOrder().size());

        List<WorkflowNode> executionOrder = graph.getExecutionOrder();
        Set<String> activeEdgeIds = new HashSet<>();

        for (WorkflowNode node : executionOrder) {
            List<WorkflowEdge> incomingEdges = graph.getIncomingEdges(node.id());

            // 1. Determine if this node belongs to an inactive branch and should be skipped
            boolean shouldSkip = false;
            if (!incomingEdges.isEmpty()) {
                boolean hasActiveIncoming = incomingEdges.stream()
                        .anyMatch(e -> activeEdgeIds.contains(e.id()));
                if (!hasActiveIncoming) {
                    shouldSkip = true;
                }
            }

            if (shouldSkip) {
                Instant now = Instant.now();
                NodeExecutionLog nodeLog = new NodeExecutionLog(
                        execution,
                        node.id(),
                        node.type().name(),
                        "SKIPPED"
                );
                nodeLog.setStartedAt(now);
                nodeLog.setCompletedAt(now);
                ObjectNode skippedPayload = objectMapper.createObjectNode();
                skippedPayload.put("skipped", true);
                skippedPayload.put("reason", "Branch not taken");
                nodeLog.setOutputData(skippedPayload);
                nodeLogRepository.save(nodeLog);

                context.markNodeSkipped(node.id());
                log.info("Node [{}] ({}) was SKIPPED (branch not taken)", node.id(), node.type());
                continue;
            }

            // 2. Active node execution: filter incoming sources to only active predecessors
            List<String> activeIncomingSourceIds = incomingEdges.isEmpty()
                    ? Collections.emptyList()
                    : incomingEdges.stream()
                            .filter(e -> activeEdgeIds.contains(e.id()))
                            .map(WorkflowEdge::source)
                            .distinct()
                            .toList();

            JsonNode incomingData = context.getIncomingData(node.id(), activeIncomingSourceIds);

            NodeExecutionLog nodeLog = new NodeExecutionLog(
                execution,
                node.id(),
                node.type().name(),
                "RUNNING"
            );
            nodeLog.setStartedAt(Instant.now());
            nodeLog.setInputData(incomingData);
            nodeLog = nodeLogRepository.save(nodeLog);

            NodeExecutor executor = executors.get(node.type());
            if (executor == null) {
                String error = "No executor found for node type: " + node.type();
                failNodeAndExecution(nodeLog, execution, error);
                return execution;
            }

            JsonNode output;
            try {
                output = executor.execute(node, activeIncomingSourceIds, context);
                nodeLog.setStatus("COMPLETED");
                nodeLog.setOutputData(output);
                nodeLog.setCompletedAt(Instant.now());
                nodeLogRepository.save(nodeLog);

                context.setNodeOutput(node.id(), output);
                log.debug("Node [{}] ({}) completed successfully", node.id(), node.type());

            } catch (Exception ex) {
                log.warn("Execution failed at node [{}] ({}): {}", node.id(), node.type(), ex.getMessage());
                failNodeAndExecution(nodeLog, execution, ex.getMessage());
                return execution;
            }

            // 3. Activate outgoing edges based on node type and evaluation result
            List<WorkflowEdge> outgoingEdges = graph.getOutgoingEdges(node.id());
            if (node.type() == NodeType.CONDITION) {
                boolean conditionMet = output != null && output.hasNonNull("result") && output.get("result").asBoolean();
                String activeHandle = conditionMet ? "true" : "false";
                int activatedCount = 0;
                for (WorkflowEdge edge : outgoingEdges) {
                    if (edge.sourceHandle() != null && edge.sourceHandle().equalsIgnoreCase(activeHandle)) {
                        activeEdgeIds.add(edge.id());
                        activatedCount++;
                    }
                }
                log.debug("CONDITION node [{}] evaluated to branch '{}'. Activated {} outgoing edge(s).",
                        node.id(), activeHandle.toUpperCase(), activatedCount);
            } else {
                for (WorkflowEdge edge : outgoingEdges) {
                    activeEdgeIds.add(edge.id());
                }
            }
        }

        // All active nodes succeeded
        execution.setStatus("COMPLETED");
        execution.setOutputData(context.getFinalOutput());
        execution.setCompletedAt(Instant.now());
        execution = executionRepository.save(execution);

        log.info("Workflow execution [{}] completed successfully", execution.getId());
        return execution;
    }

    private void failNodeAndExecution(NodeExecutionLog nodeLog, WorkflowExecution execution, String errorMessage) {
        Instant now = Instant.now();

        nodeLog.setStatus("FAILED");
        nodeLog.setErrorMessage(errorMessage);
        nodeLog.setCompletedAt(now);
        nodeLogRepository.save(nodeLog);

        execution.setStatus("FAILED");
        execution.setErrorMessage(errorMessage);
        execution.setCompletedAt(now);
        executionRepository.save(execution);
    }
}
