package com.agentflow.workflow.engine;

import com.agentflow.entity.NodeExecutionLog;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.workflow.engine.executor.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionEngine.class);

    private final ObjectMapper objectMapper;
    private final NodeExecutionLogRepository nodeLogRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    public WorkflowExecutionEngine(
            ObjectMapper objectMapper,
            NodeExecutionLogRepository nodeLogRepository,
            WorkflowExecutionRepository executionRepository,
            ManualTriggerExecutor manualTriggerExecutor,
            TextInputExecutor textInputExecutor,
            TextTransformExecutor textTransformExecutor,
            AiTextProcessorExecutor aiTextProcessorExecutor,
            OutputExecutor outputExecutor) {
        this.objectMapper = objectMapper;
        this.nodeLogRepository = nodeLogRepository;
        this.executionRepository = executionRepository;

        executors.put(NodeType.MANUAL_TRIGGER, manualTriggerExecutor);
        executors.put(NodeType.TEXT_INPUT, textInputExecutor);
        executors.put(NodeType.TEXT_TRANSFORM, textTransformExecutor);
        executors.put(NodeType.AI_TEXT_PROCESSOR, aiTextProcessorExecutor);
        executors.put(NodeType.OUTPUT, outputExecutor);
    }

    public WorkflowExecution run(WorkflowGraph graph, WorkflowExecution execution) {
        ExecutionContext context = new ExecutionContext(
            execution.getWorkflow().getId(),
            execution.getId(),
            execution.getInputData(),
            objectMapper
        );

        log.info("Starting execution [{}] for workflow [{}] with {} nodes in topological order",
            execution.getId(), execution.getWorkflow().getId(), graph.getExecutionOrder().size());

        List<WorkflowNode> executionOrder = graph.getExecutionOrder();

        for (WorkflowNode node : executionOrder) {
            List<String> incomingSourceIds = graph.getIncomingSourceIds(node.id());
            JsonNode incomingData = context.getIncomingData(node.id(), incomingSourceIds);

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

            try {
                JsonNode output = executor.execute(node, incomingSourceIds, context);
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
        }

        // All nodes succeeded
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
