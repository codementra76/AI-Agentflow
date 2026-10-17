package com.agentflow.repository;

import com.agentflow.entity.NodeExecutionLog;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowExecutionRepositoryTest {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Autowired
    private NodeExecutionLogRepository nodeExecutionLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should persist and retrieve workflow execution and node execution logs")
    void shouldPersistAndRetrieveExecutionAndLogs() throws Exception {
        Workflow workflow = new Workflow("Test Workflow", "Desc", objectMapper.readTree("{\"nodes\":[]}"), true);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        WorkflowExecution execution = new WorkflowExecution(savedWorkflow, "RUNNING", objectMapper.readTree("{\"input\":\"data\"}"));
        execution.setStartedAt(Instant.now());
        WorkflowExecution savedExecution = workflowExecutionRepository.save(execution);

        NodeExecutionLog log = new NodeExecutionLog(savedExecution, "node-1", "AI_PROMPT", "COMPLETED");
        log.setOutputData(objectMapper.readTree("{\"result\":\"ok\"}"));
        nodeExecutionLogRepository.save(log);

        List<WorkflowExecution> executions = workflowExecutionRepository.findByWorkflowId(savedWorkflow.getId());
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getStatus()).isEqualTo("RUNNING");

        List<NodeExecutionLog> logs = nodeExecutionLogRepository.findByWorkflowExecutionId(savedExecution.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNodeId()).isEqualTo("node-1");
        assertThat(logs.get(0).getNodeType()).isEqualTo("AI_PROMPT");
    }
}
